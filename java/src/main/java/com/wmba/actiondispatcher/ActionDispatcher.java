package com.wmba.actiondispatcher;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import rx.Scheduler;
import rx.Single;
import rx.SingleSubscriber;

public class ActionDispatcher {
  private final ExecutorService mPersistentExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorCache mExecutorCache = new ExecutorCache();

  private final KeySelector mKeySelector;
  private final ActionPreparer mActionPreparer;
  private final ActionLogger mActionLogger;
  private final ActionPersister mActionPersister;

  public ActionDispatcher(KeySelector keySelector, ActionPreparer actionPreparer,
                          ActionLogger actionLogger, ActionPersister actionPersister) {
    mKeySelector = keySelector;
    mActionPreparer = actionPreparer;
    mActionLogger = actionLogger;
    mActionPersister = actionPersister;
  }

  public <T> Single<T> toSingle(Action<T> action) {
    return toSingle(mKeySelector.getKey(action), action);
  }

  public <T> Single<T> toSingleAsync(Action<T> action) {
    return toSingle(KeySelector.ASYNC_KEY, action);
  }

  public <T> Single<T> toSingle(String key, Action<T> action) {
    //noinspection unchecked
    Single<T> single = Single.create(new ExecutionContext(key, action, action.isPersistent()));
    Scheduler scheduler = action.observeOn();
    return (scheduler == null) ? single : single.observeOn(scheduler);
  }

  public Set<String> getActiveKeys() {
    return mExecutorCache.getActiveKeys();
  }

  /* package */ <T> T subscribeBlocking(SubscriptionContext subscriptionContext, Action<T> action) throws Throwable {
    ExecutionContext<T> executionContext = new ExecutionContext<T>(null, action, false);
    return executionContext.runAction(subscriptionContext);
  }

  private class ExecutionContext<T> implements Single.OnSubscribe<T> {
    private final String mKey;
    private final Action<T> mAction;

    /* Action Options, should be used instead of calling getters from the action so that the getters
       are only called once. */
    private final boolean mIsPersistent;

    // Optional member variables that are only used in certain circumstances.
    private Semaphore mPersistSemaphore = null;
    private Long mPersistedId = null;

    ExecutionContext(String key, Action<T> action, boolean isPersistent) {
      mKey = key;
      mAction = action;
      mIsPersistent = isPersistent;
    }

    @Override public void call(final SingleSubscriber<? super T> subscriber) {
      ExecutorService executor = mExecutorCache.getExecutorForKey(mKey);
      executor.execute(new Runnable() {
        @Override public void run() {
          try {
            T response = runAction(new SubscriptionContext(ActionDispatcher.this, subscriber));
            subscriber.onSuccess(response);
          } catch (Throwable t) {
            subscriber.onError(t);
          }
        }
      });
    }

    public T runAction(SubscriptionContext subscriptionContext) throws Throwable {
      mAction.setSubscriptionContext(subscriptionContext);

      if (mIsPersistent) {
        if (mActionPersister == null) {
          throw new IllegalStateException("Running Persistent Action " + mAction.getClass().getName()
              + ", but no ActionPersister is set");
        }

        mPersistSemaphore = new Semaphore(1);
        persistAction();
      }

      try {
        prepareAction();
        return runActionBody();
      } finally {
        if (mPersistedId != null) {
          persistActionDelete();
        }
      }
    }

    private void prepareAction() {
      try {
        if (mActionPreparer != null) mActionPreparer.prepare(mAction);
      } catch (Throwable t) {
        if (mActionLogger != null)
          mActionLogger.logError(t, "Error while preparing Action " + mAction.getClass().getName());
      }
      mAction.prepare();
    }

    private void persistAction() {
      try {
        mPersistSemaphore.acquireUninterruptibly();
        mPersistentExecutor.execute(new Runnable() {
          @Override public void run() {
            try {
              mPersistedId = mActionPersister.persist(mAction);
              mPersistSemaphore.release();
            } catch (Throwable t) {
              if (mActionLogger != null)
                mActionLogger.logError(t, "Error while persisting Action " + mAction.getClass().getName());
            }
          }
        });

        mPersistSemaphore.acquireUninterruptibly();
      } catch (Throwable t) {
        if (mActionLogger != null)
          mActionLogger.logError(t, "Error while persisting Action " + mAction.getClass().getName());
      }
    }

    private void persistActionUpdate() {
      try {
        mPersistSemaphore.acquireUninterruptibly();
        mPersistentExecutor.execute(new Runnable() {
          @Override public void run() {
            try {
              mActionPersister.update(mPersistedId, mAction);
              mPersistSemaphore.release();
            } catch (Throwable t) {
              if (mActionLogger != null)
                mActionLogger.logError(t, "Error while persisting update for Action " + mAction.getClass().getName());
            }
          }
        });

        mPersistSemaphore.acquireUninterruptibly();
      } catch (Throwable t) {
        if (mActionLogger != null)
          mActionLogger.logError(t, "Error while persisting update for Action " + mAction.getClass().getName());
      }
    }

    private void persistActionDelete() {
      mActionPersister.delete(mPersistedId);
    }

    private T runActionBody() throws Throwable {
      T response = null;
      boolean completed = false;
      int count = 0;
      if (mActionLogger != null) mActionLogger.logDebug("Running Action " + mAction.getClass().getName() + ".");

      do {
        if (mAction.runIfUnsubscribed() || mAction.isUnsubscribed()) {

          if (count > 0) mAction.preRetry();

          try {
            response = mAction.execute();
            if (mActionLogger != null) mActionLogger.logDebug("Action finished running " + mAction.getClass().getName() + ".");
            completed = true;
          } catch (Throwable t) {
            boolean shouldRetry = mAction.shouldRetryForThrowable(t);
            count ++;
            if (mActionLogger != null) mActionLogger.logDebug("Error running Action " + mAction.getClass().getName() + "."
                + (shouldRetry ? ("Retrying. #" + count) : "Not Retrying") + ".");

            if (shouldRetry) {
              if (mPersistedId != null) {
                persistActionUpdate();
              }
            } else {
              throw t;
            }
          }
        }
      } while (!completed);

      return response;
    }
  }

  public static class Builder {
    private KeySelector mKeySelector = null;
    private ActionPreparer mActionPreparer = null;
    private ActionLogger mActionLogger = null;
    private ActionPersister mActionPersister = null;

    public ActionDispatcher build() {
      return new ActionDispatcher(
          mKeySelector != null ? mKeySelector : new KeySelector(),
          mActionPreparer,
          mActionLogger,
          mActionPersister
      );
    }

    public KeySelector getKeySelector() {
      return mKeySelector;
    }

    public void setKeySelector(KeySelector keySelector) {
      mKeySelector = keySelector;
    }

    public ActionPreparer getActionPreparer() {
      return mActionPreparer;
    }

    public void setActionPreparer(ActionPreparer actionPreparer) {
      mActionPreparer = actionPreparer;
    }

    public ActionLogger getActionLogger() {
      return mActionLogger;
    }

    public void setActionLogger(ActionLogger actionLogger) {
      mActionLogger = actionLogger;
    }

    public ActionPersister getActionPersister() {
      return mActionPersister;
    }

    public void setActionPersister(ActionPersister actionPersister) {
      mActionPersister = actionPersister;
    }
  }
}
