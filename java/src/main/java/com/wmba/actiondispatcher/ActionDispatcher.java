package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.SingleSubscriber;

public class ActionDispatcher {
  private final Object mPersistentLock = new Object();

  private final ExecutorCache mExecutorCache = new ExecutorCache();

  private final KeySelector mKeySelector;
  private final ActionPreparer mActionPreparer;
  private final ActionLogger mActionLogger;
  private final ActionPersister mActionPersister;

  /**
   * Is null until the persisted Actions have been loaded. If no Actions are loaded, it is replaced
   * with an empty list.
   */
  private List<PersistedActionHolder> mPersistedActions = null;
  private boolean mPendingRunPersistentActions = false;

  /**
   * Used for queueing actions before the persisted Actions have loaded when the dispatcher is
   * first starting. Both of these lists are kept in sync, and always modified under the
   * mPersistentLock.
   */
  private List<ExecutorService> mQueuedActionExecutors = null;
  private List<Runnable> mQueuedActionRunnables = null;

  public ActionDispatcher(KeySelector keySelector, ActionPreparer actionPreparer,
                          ActionLogger actionLogger, ActionPersister actionPersister) {
    mKeySelector = keySelector;
    mActionPreparer = actionPreparer;
    mActionLogger = actionLogger;
    mActionPersister = actionPersister;

    if (mActionPersister != null) {
      ExecutorService executor = mExecutorCache.getExecutorForKey(KeySelector.ASYNC_KEY);
      executor.execute(new Runnable() {
        @Override public void run() {
          try {
            List<PersistedActionHolder> persistedActions = mActionPersister.getPersistedActions();
            if (persistedActions == null) {
              persistedActions = new ArrayList<PersistedActionHolder>(0);
            }

            if (mActionLogger != null)
              mActionLogger.logDebug("Loaded " + persistedActions.size() + " persistent Actions");

            synchronized (mPersistentLock) {
              mPersistedActions = persistedActions;
              if (mPendingRunPersistentActions) {
                startPersistentActions();
              }

              dispatchQueuedActions();
            }
          } catch (Throwable t) {
            logOrPrintError(t, "Error running persisted Actions");

            try {
              mActionPersister.deleteAll();
            } catch (Throwable t2) {
              logOrPrintError(t2, "Error deleting all persisted actions.");
            }

            synchronized (mPersistentLock) {
              if (mPersistedActions == null) {
                dispatchQueuedActions();
                mPersistedActions = new ArrayList<PersistedActionHolder>(0);
              }
            }
          }
        }
      });
    } else {
      synchronized (mPersistentLock) {
        mPersistedActions = new ArrayList<PersistedActionHolder>(0);
      }
    }
  }

  private void dispatchQueuedActions() {
    if (mQueuedActionExecutors != null) {
      for (int i = 0, size = mQueuedActionExecutors.size(); i < size; i++) {
        ExecutorService executor = mQueuedActionExecutors.get(i);
        Runnable runnable = mQueuedActionRunnables.get(i);
        executor.execute(runnable);
      }
      mQueuedActionExecutors = null;
      mQueuedActionRunnables = null;
    }
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

  public <T> Observable<T> toObservable(Action<T> action) {
    return toObservable(mKeySelector.getKey(action), action);
  }

  public <T> Observable<T> toObservableAsync(Action<T> action) {
    return toObservable(KeySelector.ASYNC_KEY, action);
  }

  public <T> Observable<T> toObservable(String key, Action<T> action) {
    //noinspection unchecked
    return toSingle(key, action).toObservable();
  }

  public void startPersistentActions() {
    synchronized (mPersistentLock) {

      if (arePersistentActionsLoaded()) {

        for (PersistedActionHolder holder : mPersistedActions) {

          long persistedId = holder.getActionId();
          Action<?> action = holder.getAction();
          String key = mKeySelector.getKey(action);
          //noinspection unchecked
          Single.create(new ExecutionContext(key, action, persistedId))
              .subscribe(new SingleSubscriber() {
                @Override public void onSuccess(Object value) {}
                @Override public void onError(Throwable error) {}
              });

        }

        if (mActionLogger != null) mActionLogger.logDebug("Persistent Actions started");

        mPersistedActions = new ArrayList<PersistedActionHolder>(0);
        mPendingRunPersistentActions = false;
      } else {
        mPendingRunPersistentActions = true;
      }

    }
  }

  public boolean arePersistentActionsLoaded() {
    return mPersistedActions != null;
  }

  public Set<String> getActiveKeys() {
    return mExecutorCache.getActiveKeys();
  }

  /* package */ <T> T subscribeBlocking(SubscriptionContext subscriptionContext, Action<T> action) throws Throwable {
    ExecutionContext<T> executionContext = new ExecutionContext<T>(null, action, false);
    return executionContext.runAction(subscriptionContext);
  }

  private void logOrPrintError(Throwable t, String message) {
    if (mActionLogger == null) {
      System.out.println("Action Dispatcher Error: " + message);
      t.printStackTrace();
    } else {
      mActionLogger.logError(t, message);
    }
  }

  private class ExecutionContext<T> implements Single.OnSubscribe<T> {
    private final String mKey;
    private final Action<T> mAction;
    private final boolean mShouldPersist;

    // Action options that should be used instead of using the action getters so the getters are
    // only called once.
    private final boolean mRunIfUnsubscribed;

    // Optional member variables that are only used in certain circumstances.
    private Long mPersistedId = null;

    ExecutionContext(String key, Action<T> action, boolean shouldPersist) {
      mKey = key;
      mAction = action;
      mShouldPersist = shouldPersist;
      mRunIfUnsubscribed = action.runIfUnsubscribed();
    }

    ExecutionContext(String key, Action<T> action, long persistedId) {
      this(key, action, false);
      mPersistedId = persistedId;
    }

    @Override public void call(final SingleSubscriber<? super T> subscriber) {
      ExecutorService executor = mExecutorCache.getExecutorForKey(mKey);
      Runnable runnable = new Runnable() {
        @Override public void run() {
          try {
            T response = runAction(new SubscriptionContext(ActionDispatcher.this, subscriber));
            subscriber.onSuccess(response);
          } catch (Throwable t) {
            subscriber.onError(t);
          }
        }
      };

      if (!arePersistentActionsLoaded()) {
        // Persistent Actions haven't loaded
        synchronized (mPersistentLock) {
          if (!arePersistentActionsLoaded()) {
            if (mQueuedActionExecutors == null) {
              mQueuedActionExecutors = new ArrayList<ExecutorService>();
              mQueuedActionRunnables = new ArrayList<Runnable>();
            }

            mQueuedActionExecutors.add(executor);
            mQueuedActionRunnables.add(runnable);
          } else {
            executor.execute(runnable);
          }
        }
      } else {
        executor.execute(runnable);
      }
    }

    public T runAction(SubscriptionContext subscriptionContext) throws Throwable {
      mAction.setSubscriptionContext(subscriptionContext);

      if (mShouldPersist) {
        if (mActionPersister == null) {
          throw new IllegalStateException("Running Persistent Action " + mAction.getClass().getName()
              + ", but no ActionPersister is set");
        }

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
        logOrPrintError(t, "Error while preparing Action " + mAction.getClass().getName());
      }
      mAction.prepare();
    }

    private void persistAction() {
      try {
        mPersistedId = mActionPersister.persist(mAction);
      } catch (Throwable t) {
        logOrPrintError(t, "Error while persisting Action " + mAction.getClass().getName());
      }
    }

    private void persistActionUpdate() {
      try {
        mActionPersister.update(mPersistedId, mAction);
      } catch (Throwable t) {
        logOrPrintError(t, "Error while persisting update for Action " + mAction.getClass().getName());
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
        if (mRunIfUnsubscribed || mAction.isUnsubscribed()) {

          if (count > 0) mAction.preRetry();

          try {
            response = mAction.execute();
            if (mActionLogger != null) mActionLogger.logDebug("Action finished running " + mAction.getClass().getName() + ".");
            completed = true;
          } catch (Throwable t) {
            boolean shouldRetry = mAction.shouldRetryForThrowable(t);
            count++;
            if (mActionLogger != null) mActionLogger.logDebug("Error running Action " + mAction.getClass().getName() + ". "
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

    public ActionDispatcher.Builder withKeySelector(KeySelector keySelector) {
      mKeySelector = keySelector;
      return this;
    }

    public ActionDispatcher.Builder withActionPreparer(ActionPreparer actionPreparer) {
      mActionPreparer = actionPreparer;
      return this;
    }

    public ActionDispatcher.Builder withActionLogger(ActionLogger actionLogger) {
      mActionLogger = actionLogger;
      return this;
    }

    public ActionDispatcher.Builder withActionPersister(ActionPersister actionPersister) {
      mActionPersister = actionPersister;
      return this;
    }
  }
}
