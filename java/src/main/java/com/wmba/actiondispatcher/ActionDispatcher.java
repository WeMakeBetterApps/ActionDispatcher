package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

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
  private List<Executor> mQueuedActionExecutors = null;
  private List<Runnable> mQueuedActionRunnables = null;

  public ActionDispatcher(KeySelector keySelector, ActionPreparer actionPreparer,
      ActionLogger actionLogger, ActionPersister actionPersister,
      Map<String, Executor> executorMap, final boolean delayPersistentActionLoading) {
    mKeySelector = keySelector;
    mActionPreparer = actionPreparer;
    mActionLogger = actionLogger;
    mActionPersister = actionPersister;

    for (Map.Entry<String, Executor> entry : executorMap.entrySet()) {
      mExecutorCache.setExecutor(entry.getValue(), entry.getKey());
    }

    if (mActionPersister != null) {
      Executor executor = mExecutorCache.getExecutorForKey(KeySelector.ASYNC_KEY);
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
              if (mPendingRunPersistentActions || !delayPersistentActionLoading) {
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
        Executor executor = mQueuedActionExecutors.get(i);
        Runnable runnable = mQueuedActionRunnables.get(i);
        executor.execute(runnable);
      }
      mQueuedActionExecutors = null;
      mQueuedActionRunnables = null;
    }
  }

  public <T> Single<T> toSingle(Action<T> action) {
    return toSingle(action, mKeySelector.getKey(action), null);
  }

  public <T> Single<T> toSingleAsync(Action<T> action) {
    return toSingle(action, KeySelector.ASYNC_KEY, null);
  }

  public <T> Single<T> toSingleAsync(Action<T> action, Scheduler observeOn) {
    return toSingle(action, KeySelector.ASYNC_KEY, observeOn);
  }

  public <T> Single<T> toSingle(Action<T> action, String key) {
    return toSingle(action, key, null);
  }

  public <T> Single<T> toSingle(Action<T> action, Scheduler observeOn) {
    return toSingle(action, null, observeOn);
  }

  public <T> Single<T> toSingle(Action<T> action, String key, Scheduler observeOn) {
    String checkedKey = (key == null) ? KeySelector.DEFAULT_KEY : key;
    //noinspection unchecked
    Single<T> single = Single.create(new ExecutionContext(checkedKey, action, action.isPersistent()));
    if (observeOn == null) {
      Scheduler actionObserveOn = action.observeOn();
      return (actionObserveOn == null) ? single : single.observeOn(actionObserveOn);
    } else {
      return single.observeOn(observeOn);
    }
  }

  public <T> Observable<T> toObservable(Action<T> action) {
    return toObservable(action, mKeySelector.getKey(action), null);
  }

  public <T> Observable<T> toObservableAsync(Action<T> action) {
    return toObservable(action, KeySelector.ASYNC_KEY, null);
  }

  public <T> Observable<T> toObservableAsync(Action<T> action, Scheduler observeOn) {
    return toObservable(action, KeySelector.ASYNC_KEY, observeOn);
  }

  public <T> Observable<T> toObservable(Action<T> action, String key) {
    return toObservable(action, key, null);
  }

  public <T> Observable<T> toObservable(Action<T> action, Scheduler observeOn) {
    return toObservable(action, null, observeOn);
  }

  public <T> Observable<T> toObservable(Action<T> action, String key, Scheduler observeOn) {
    //noinspection unchecked
    return toSingle(action, key, observeOn).toObservable();
  }

  /**
   * This does nothing unless {@link Builder#delayPersistentActionLoading()} is called at
   * {@link ActionDispatcher} creation.
   *
   * Loads the persistent Actions and queues them
   */
  public void startPersistentActions() {
    synchronized (mPersistentLock) {

      if (arePersistentActionsLoaded()) {

        for (PersistedActionHolder holder : mPersistedActions) {

          try {
            long persistedId = holder.getActionId();
            Action<?> action = holder.getAction();
            String key = mKeySelector.getKey(action);
            if (action != null) {
              //noinspection unchecked
              Single.create(new ExecutionContext(key, action, persistedId))
                  .subscribe(new SingleSubscriber() {
                    @Override public void onSuccess(Object value) {}
                    @Override public void onError(Throwable error) {}
                  });
            }
          } catch (Throwable t) {
            logOrPrintError(t, "Error running persisted Actions");

            try {
              mActionPersister.deleteAll();
            } catch (Throwable t2) {
              logOrPrintError(t2, "Error deleting all persisted actions.");
            }
          }

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

  public Executor getExecutor(String key) {
    return mExecutorCache.getExecutorForKey(key);
  }

  public Executor getAsyncExecutor() {
    return mExecutorCache.getExecutorForKey(KeySelector.ASYNC_KEY);
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
      Executor executor = mExecutorCache.getExecutorForKey(mKey);
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
              mQueuedActionExecutors = new ArrayList<Executor>();
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
    private Map<String, Executor> mExecutorMap = null;
    private boolean mDelayPersistentActionLoading = false;

    public ActionDispatcher build() {
      return new ActionDispatcher(
          mKeySelector != null ? mKeySelector : new KeySelector(),
          mActionPreparer,
          mActionLogger,
          mActionPersister,
          mExecutorMap,
          mDelayPersistentActionLoading
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

    public ActionDispatcher.Builder withExecutor(String key, Executor executor) {
      if (mExecutorMap == null) {
        mExecutorMap = new HashMap<String, Executor>(5);
      }

      mExecutorMap.put(key, executor);

      return this;
    }

    /**
     * Call to delay the running of persisted Actions, until
     * {@link ActionDispatcher#startPersistentActions()} is called.
     */
    public ActionDispatcher.Builder delayPersistentActionLoading() {
      mDelayPersistentActionLoading = true;
      return this;
    }
  }
}
