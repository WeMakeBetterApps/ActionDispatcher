package com.wmba.actiondispatcher;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;

public class ActionDispatcher {

  private static final long PAUSE_EXPONENTIAL_BACKOFF_MIN_TIME = 100;
  private static final long PAUSE_EXPONENTIAL_BACKOFF_MAX_TIME = 5000;

  private final Map<String, ExecutorService> mExecutorCache = new HashMap<String, ExecutorService>();

  private final ComposableOnSubscribePool mComposableOnSubscribePool = new ComposableOnSubscribePool();
  private final PersistentOnSubscribePool mPersistentOnSubscribePool = new PersistentOnSubscribePool();
  private final Executor mPersistentExecutor = Executors.newSingleThreadExecutor();

  private final ActionRunner mActionRunner;
  private final ObserveOnProvider mObserveOnProvider;
  private final ActionKeySelector mKeySelector;
  private final ActionPersister mPersister;
  private final ActionPauser mPauser;
  private final ActionInjector mInjector;


  private final Object mPersistentQueueRestoreLock = new Object();
  private boolean mIsPersistentQueueRestored = false;

  // Use 2 lists instead of a map as maps are highly memory intensive.
  /**
   * List of Queued Executors for actions that are run prior to the persistent queue being restored.
   * This will always be used inside of the synchronized context of mPersistentQueueRestoreLock
   * and is paired with mQueuedActionRunnables.
   */
  private List<Executor> mQueuedActionExecutors = null;
  /**
   * List of Queued Runnables for actions that are run prior to the persistent queue being restored.
   * This will always be used inside of the synchronized context of mPersistentQueueRestoreLock
   * and is paired with mQueuedActionExecutors.
   */
  private List<Runnable> mQueuedActionRunnables = null;

  public static class Builder {

    private ActionRunner mActionRunner;
    private ObserveOnProvider mObserveOnProvider;
    private ActionKeySelector mKeySelector;
    private ActionPersister mPersister;
    private ActionPauser mPauser;
    private ActionInjector mInjector;

    public Builder actionRunner(ActionRunner actionRunner) {
      mActionRunner = actionRunner;
      return this;
    }

    public Builder observeOnProvider(ObserveOnProvider observeOnProvider) {
      mObserveOnProvider = observeOnProvider;
      return this;
    }

    public Builder keySelector(ActionKeySelector keySelector) {
      mKeySelector = keySelector;
      return this;
    }

    public Builder persister(ActionPersister persister) {
      mPersister = persister;
      return this;
    }

    public Builder pauser(ActionPauser pauser) {
      mPauser = pauser;
      return this;
    }

    public Builder injector(ActionInjector injector) {
      mInjector = injector;
      return this;
    }

    public ActionDispatcher build() {
      return new ActionDispatcher(mActionRunner, mObserveOnProvider, mKeySelector, mPersister,
          mPauser, mInjector);
    }
  }

  protected ActionDispatcher(ActionRunner actionRunner, ObserveOnProvider observeOnProvider,
                           ActionKeySelector keySelector, ActionPersister persister,
                           ActionPauser pauser, ActionInjector injector) {
    // Action Runner
    if (actionRunner == null) {
      this.mActionRunner = new ActionRunner() {
        @Override public void execute(ActionRunnable actionRunnable, Action[] actions) {
          actionRunnable.execute();
        }
      };
    } else {
      this.mActionRunner = actionRunner;
    }

    // ObserveOn Provider
    this.mObserveOnProvider = observeOnProvider;

    // Key Selector
    if (keySelector == null) {
      this.mKeySelector = new ActionKeySelector() {
        @Override public String getKey(Action... actions) {
          return ActionKeySelector.DEFAULT_KEY;
        }
      };
    } else {
      this.mKeySelector = keySelector;
    }

    // Pauser
    if (pauser == null) {
      this.mPauser = new ActionPauser() {
        @Override public boolean shouldPauseForAction(Action action) {
          return false;
        }
      };
    } else {
      this.mPauser = pauser;
    }

    // Injector
    if (injector == null) {
      this.mInjector = new ActionInjector() {
        @Override public void inject(Action action) {
          // no-op
        }
      };
    } else {
      this.mInjector = injector;
    }

    // Persister
    this.mPersister = persister;

    if (mPersister != null) {
      // Get and run non-completed persistent actions
      mPersistentExecutor.execute(new Runnable() {
        @Override public void run() {
          List<PersistedActionHolder> actionHolders = mPersister.getPersistedActions();
          for (PersistedActionHolder holder : actionHolders) {
            SingularAction action = holder.getPersistedAction();
            Observable.OnSubscribe onSubscribe = mPersistentOnSubscribePool.get(
                action.getKey(), new SingularAction[]{action}, true, holder.getActionId());
            Observable.create(onSubscribe).subscribe();
          }

          // Now that we've loaded the persistent jobs, lets check if we've queued anything.
          synchronized (mPersistentQueueRestoreLock) {
            if (mQueuedActionExecutors != null) {
              for (int i = 0; i < mQueuedActionExecutors.size(); i++) {
                mQueuedActionExecutors.get(i).execute(mQueuedActionRunnables.get(i));
              }
            }
            mIsPersistentQueueRestored = true;

            mQueuedActionExecutors = null;
            mQueuedActionRunnables = null;
          }
        }
      });
    } else {
      synchronized (mPersistentQueueRestoreLock) {
        mIsPersistentQueueRestored = true;
      }
    }
  }

  public Set<String> getActiveKeys() {
    return mExecutorCache.keySet();
  }

  /**
   * Will route the action automatically if its a {@link ComposableAction} or a
   * {@link SingularAction}, automatically selecting the actions key.
   *
   * @param action Takes a {@link ComposableAction} or a {@link SingularAction}. Anything else
   *               will throw an exception.
   */
  public <T> Observable<T> toObservable(Action<T> action) {
    return toObservable(mKeySelector.getKey(action), action);
  }

  /**
   * Will route the action automatically if its a {@link ComposableAction} or a
   * {@link SingularAction}.
   *
   * @param key Key that represents the thread the action should be run in.
   * @param action Takes a {@link ComposableAction} or a {@link SingularAction}. Anything else
   *               will throw an exception.
   */
  public <T> Observable<T> toObservable(String key, Action<T> action) {
    if (action instanceof ComposableAction) {
      //noinspection unchecked
      return (Observable<T>) toObservable(key, new ComposableAction[]{(ComposableAction<T>) action});
    } else if (action instanceof SingularAction) {
      return toObservable(key, (SingularAction<T>) action);
    } else {
      throw new InvalidParameterException("Action must be instance of "
          + ComposableAction.class.getName() + " or " + SingularAction.class.getName()
          + ". You provided " + action.getClass().getName());
    }
  }

  public <T> Observable<T> toObservable(ComposableAction<T> action) {
    //noinspection unchecked
    return (Observable<T>) toObservable(new ComposableAction[]{action});
  }

  public <T> Observable<T> toObservable(String key, ComposableAction<T> action) {
    //noinspection unchecked
    return (Observable<T>) toObservable(key, new ComposableAction[]{action});
  }

  public Observable<Object> toObservable(ComposableAction... actions) {
    return toObservable(mKeySelector.getKey(actions), actions);
  }

  public Observable<Object> toObservable(String key, ComposableAction... actions) {
    //noinspection unchecked
    return getActionComposableObservable(key, actions);
  }

  public <T> Observable<T> toObservableAsync(ComposableAction<T> action) {
    //noinspection unchecked
    return (Observable<T>) toObservableAsync(new ComposableAction[]{action});
  }

  public Observable<Object> toObservableAsync(ComposableAction... actions) {
    //noinspection unchecked
    return getActionComposableObservable(ActionKeySelector.ASYNC_KEY, actions);
  }

  public <T> Observable<T> toObservableAsync(SingularAction<T> action) {
    return toObservable(ActionKeySelector.ASYNC_KEY, action);
  }

  public <T> Observable<T> toObservable(SingularAction<T> action) {
    return toObservable(mKeySelector.getKey(action), action);
  }

  public <T> Observable<T> toObservable(String key, SingularAction<T> action) {
    if (action.isPersistent() && mPersister == null)
      throw new RuntimeException("Can't persist action as no " + ActionPersister.class.getName()
          + " was ever provided at " + ActionDispatcher.class.getSimpleName() + " creation.");

    action.setKey(key);

    if (action.isPersistent()) {
      //noinspection unchecked
      return (Observable<T>) getActionPersistentObservable(key, action);
    } else {
      //noinspection unchecked
      return (Observable<T>) getActionComposableObservable(key, action);
    }
  }

  private Observable getActionComposableObservable(String key, Action... actions) {
    Observable observable = Observable.create(mComposableOnSubscribePool.get(key, actions));
    return postCreateObservable(observable, actions);
  }

  private Observable getActionPersistentObservable(String key, Action... actions) {
    Observable observable = Observable.create(mPersistentOnSubscribePool.get(key, actions));
    return postCreateObservable(observable, actions);
  }

  private Observable postCreateObservable(Observable observable, Action[] actions) {
    if (mObserveOnProvider != null)
      observable = observable.observeOn(mObserveOnProvider.provideScheduler(actions));

    return observable;
  }

  private ExecutorService getExecutorForKey(final String key) {
    ExecutorService executor = mExecutorCache.get(key);

    if (executor == null) {
      synchronized (mExecutorCache) {

        executor = mExecutorCache.get(key);
        if (executor == null) {
          ThreadFactory tf = new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
              Thread t = new Thread(runnable, "ActionDispatcherThread-" + key);
              t.setPriority(Thread.MIN_PRIORITY);
              t.setDaemon(true);
              return t;
            }
          };

          if (key.equals(ActionKeySelector.ASYNC_KEY)) {
            // Custom for async
            executor = Executors.newCachedThreadPool(tf);
          } else {
            executor = Executors.newSingleThreadScheduledExecutor(tf);
          }

          mExecutorCache.put(key, executor);
        }

      }
    }

    return executor;
  }

  /**
   * Will run the Runnable on the Executor if the persistent queue is restored and will queue the
   * runnable if the persistent queue has not been restored yet.
   *
   * @param executor the Executor to run the Runnable on.
   * @param runnable the Runnable to run on the Executor.
   */
  private void runOnExecutor(Executor executor, Runnable runnable) {
    if (mIsPersistentQueueRestored) {
      executor.execute(runnable);
    } else {
      synchronized (mPersistentQueueRestoreLock) {
        if (mIsPersistentQueueRestored) {
          executor.execute(runnable);
        } else {
          if (mQueuedActionExecutors == null) {
            mQueuedActionExecutors = new ArrayList<Executor>();
            mQueuedActionRunnables = new ArrayList<Runnable>();
          }

          mQueuedActionExecutors.add(executor);
          mQueuedActionRunnables.add(runnable);
        }
      }
    }
  }

  /*package*/ class ComposableOnSubscribe implements Observable.OnSubscribe<Object> {

    protected String key;
    protected Action[] actions;

    private long currentPauseTime;
    protected Subscriber<? super Object> subscriber;
    protected int currentActionIndex;

    protected final ActionRunnable actionRunnable = new ActionRunnable() {
      @Override public void execute() {
        runActions();
      }
    };

    @Override public void call(final Subscriber<? super Object> subscriber) {
      this.subscriber = subscriber;

      Executor executor = getExecutorForKey(key);
      Runnable runnable = new Runnable() {
        @Override public void run() {
          runActionRunner();
        }
      };
      runOnExecutor(executor, runnable);
    }

    protected void runActionRunner() {
      boolean isCompleted = false;
      do {
        try {
          mActionRunner.execute(actionRunnable, actions);
          isCompleted = true;
        } catch (Throwable t) {
          Action errorAction = actions[currentActionIndex];
          boolean shouldRetry = errorAction.shouldRetryForThrowable(t, subscriber);

          if (shouldRetry) {
            onRetryAction(errorAction);
          } else {
            isCompleted = true;
            System.out.println("There was an error running action #"
                + (currentActionIndex + 1) + " " + errorAction.getClass().getSimpleName()
                + ". Retried " + errorAction.getRetryCount() + " times.");
            subscriber.onError(t);
          }
        }
      } while(!isCompleted);
    }

    protected void onRetryAction(Action action) {
      action.incrementRetryCount();
    }

    private void runActions() {
      currentPauseTime = PAUSE_EXPONENTIAL_BACKOFF_MIN_TIME;

      int len = actions.length;
      Object[] Objects = new Object[len];

      for (int i = 0; i < len; i++) {
        currentActionIndex = i;
        Action action = actions[i];
        mInjector.inject(action);

        if (subscriber.isUnsubscribed())
          return;

        while (mPauser.shouldPauseForAction(action)) {
          pause();
        }
        currentPauseTime = PAUSE_EXPONENTIAL_BACKOFF_MIN_TIME;

        if (subscriber.isUnsubscribed())
          return;

        try {
          Objects[i] = action.execute();
        } catch (Throwable t) {
          throw new RuntimeException(t);
        }
      }

      for (Object Object : Objects)
        subscriber.onNext(Object);

      subscriber.onCompleted();
    }

    private void pause() {
      try {
        Thread.sleep(currentPauseTime);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      currentPauseTime *= 2;  // Exponential backoff
      if (currentPauseTime > PAUSE_EXPONENTIAL_BACKOFF_MAX_TIME)
        currentPauseTime = PAUSE_EXPONENTIAL_BACKOFF_MAX_TIME;
    }

    public void set(String key, Action[] actions) {
      this.key = key;
      this.actions = actions;
    }
  }

  /*package*/ class ComposableOnSubscribePool extends AbstractSynchronizedObjectPool<ComposableOnSubscribe> {

    public ComposableOnSubscribePool() {
      super(10);
    }

    @Override protected ComposableOnSubscribe create() {
      return new ComposableOnSubscribe();
    }

    @Override protected void free(ComposableOnSubscribe obj) {
      obj.set(null, null);
    }

    public ComposableOnSubscribe get(String key, Action[] actions) {
      ComposableOnSubscribe obj = borrow();
      obj.set(key, actions);
      return obj;
    }

  }

  /*package*/ class PersistentOnSubscribe extends ComposableOnSubscribe {

    private final Semaphore persistSemaphore = new Semaphore(1);
    private Long persistedId = null;
    private boolean isActionAlreadyPersisted = false;

    @Override public void call(final Subscriber<? super Object> subscriber) {
      this.subscriber = subscriber;

      if (!isActionAlreadyPersisted) {
        persistSemaphore.acquireUninterruptibly();
        Runnable persistentRunnable = new Runnable() {
          @Override public void run() {
            persistedId = mPersister.persist((SingularAction) actions[0]);
            persistSemaphore.release();
          }
        };
        runOnExecutor(mPersistentExecutor, persistentRunnable);
      }

      Executor executor = getExecutorForKey(key);
      Runnable runnable = new Runnable() {
        @Override public void run() {
          persistSemaphore.acquireUninterruptibly();

          try {
            runActionRunner();

            mPersistentExecutor.execute(new Runnable() {
              @Override public void run() {
                mPersister.delete(persistedId);
                persistedId = null;
              }
            });
          } catch (Throwable t) {
            throw new RuntimeException(t);
          } finally {
            persistSemaphore.release();
          }
        }
      };
      runOnExecutor(executor, runnable);

    }

    @Override protected void onRetryAction(final Action action) {
      super.onRetryAction(action);
      mPersistentExecutor.execute(new Runnable() {
        @Override public void run() {
          mPersister.update(persistedId, (SingularAction) action);
        }
      });
    }

    public void set(String key, Action[] actions, boolean isActionAlreadyPersisted, Long persistedId) {
      set(key, actions);
      this.isActionAlreadyPersisted = isActionAlreadyPersisted;
      this.persistedId = persistedId;
    }
  }

  /* package */ class PersistentOnSubscribePool extends AbstractSynchronizedObjectPool<PersistentOnSubscribe> {

    public PersistentOnSubscribePool() {
      super(5);
    }

    @Override protected PersistentOnSubscribe create() {
      return new PersistentOnSubscribe();
    }

    @Override protected void free(PersistentOnSubscribe obj) {
      obj.set(null, null);
    }

    public PersistentOnSubscribe get(String key, Action[] actions) {
      return get(key, actions, false, null);
    }

    public PersistentOnSubscribe get(String key, Action[] actions, boolean isActionAlreadyPersisted,
                                     Long persistedId) {
      PersistentOnSubscribe obj = borrow();
      obj.set(key, actions, isActionAlreadyPersisted, persistedId);
      return obj;
    }

  }

  /**
   * Can be supplied to ActionDispatcher at creation to add custom behavior when running actions.
   * Useful for running the actions in a transaction.
   */
  public interface ActionRunner {

    /**
     * If running the actionRunnable in a transaction, if an exception happens roll the transaction
     * back, but any caught errors need to be re-thrown.
     *
     * @param actionRunnable Call execute() on the calling thread.
     * @param actions The actions that will be ran.
     */
    void execute(ActionRunnable actionRunnable, Action[] actions);
  }

  /**
   * Can be supplied to ActionDispatcher at creation to add a default Scheduler to always observe on.
   * Useful if you want to observe all your observables on a particular thread.
   */
  public interface ObserveOnProvider {
    Scheduler provideScheduler(Action[] actions);
  }

  public interface ActionKeySelector {
    public static final String DEFAULT_KEY = "default";
    public static final String ASYNC_KEY = "async";

    /**
     * @param actions the actions that are being run
     * @return the groupId that the actions should be run in.
     */
    String getKey(Action... actions);
  }

  /**
   * Can be supplied to ActionDispatcher at creation. Will be run before every action.
   */
  public interface ActionPauser {
    boolean shouldPauseForAction(Action action);
  }

  /**
   * Can be supplied to ActionDispatcher at creation. Allows for action injection.
   */
  public interface ActionInjector {
    void inject(Action action);
  }

  /**
   * Class to avoid passing an actual runnable. We don't want to encourage user thread management.
   */
  public interface ActionRunnable {
    /**
     * Execute the actions.
     */
    void execute();
  }
}