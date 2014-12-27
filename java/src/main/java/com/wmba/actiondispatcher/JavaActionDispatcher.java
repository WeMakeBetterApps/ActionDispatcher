package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.ActionInjector;
import com.wmba.actiondispatcher.component.ActionKeySelector;
import com.wmba.actiondispatcher.component.ActionPauser;
import com.wmba.actiondispatcher.component.ActionRunnable;
import com.wmba.actiondispatcher.component.ActionRunner;
import com.wmba.actiondispatcher.component.ObserveOnProvider;
import com.wmba.actiondispatcher.memory.AbstractSynchronizedObjectPool;
import com.wmba.actiondispatcher.persist.ActionPersister;
import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import rx.Observable;
import rx.Subscriber;

public class JavaActionDispatcher implements ActionDispatcher {

  private final ExecutorCache mExecutorCache = new ExecutorCache();
  private final ActionOnSubscribePool mActionOnSubscribePool = new ActionOnSubscribePool();
  private final InstantSubscriptionContextPool mInstantSubscriptionContextPool = new InstantSubscriptionContextPool();
  private final PersistentActionOnSubscribePool mPersistentActionOnSubscribePool = new PersistentActionOnSubscribePool();
  private final Executor mPersistentExecutor = Executors.newSingleThreadExecutor();

  private final ActionRunner mActionRunner;
  private final ObserveOnProvider mObserveOnProvider;
  private final ActionKeySelector mKeySelector;
  private final ActionPersister mPersister;
  private final ActionPauser mPauser;
  private final ActionInjector mInjector;

  private final Object PERSISTENT_QUEUE_RESORE_LOCK = new Object();
  private boolean mIsPersistentQueueRestored = false;

  // Use 2 lists instead of a map as maps are highly memory intensive.
  /**
   * List of Queued Executors for actions that are run prior to the persistent queue being restored.
   * This will always be used inside of the synchronized context of PERSISTENT_QUEUE_RESORE_LOCK
   * and is paired with mQueuedActionRunnables.
   */
  private List<Executor> mQueuedActionExecutors = null;
  /**
   * List of Queued Runnables for actions that are run prior to the persistent queue being restored.
   * This will always be used inside of the synchronized context of PERSISTENT_QUEUE_RESORE_LOCK
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

    public JavaActionDispatcher build() {
      return new JavaActionDispatcher(mActionRunner, mObserveOnProvider, mKeySelector, mPersister,
          mPauser, mInjector);
    }
  }

  protected JavaActionDispatcher(ActionRunner actionRunner, ObserveOnProvider observeOnProvider,
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
            Observable.OnSubscribe onSubscribe = mPersistentActionOnSubscribePool.get(
                action.getKey(), new SingularAction[]{action}, true, holder.getActionId());
            Observable.create(onSubscribe).subscribe();
          }

          // Now that we've loaded the persistent jobs, lets check if we've queued anything.
          synchronized (PERSISTENT_QUEUE_RESORE_LOCK) {
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
      synchronized (PERSISTENT_QUEUE_RESORE_LOCK) {
        mIsPersistentQueueRestored = true;
      }
    }
  }

  @Override public Set<String> getActiveKeys() {
    return mExecutorCache.getActiveKeys();
  }

  /**
   * Will route the action automatically if its a {@link ComposableAction} or a
   * {@link SingularAction}, automatically selecting the actions key.
   *
   * @param action Takes a {@link ComposableAction} or a {@link SingularAction}. Anything else
   *               will throw an exception.
   */
  @Override public <T> Observable<T> toObservable(Action<T> action) {
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
  @Override public <T> Observable<T> toObservable(String key, Action<T> action) {
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

  @Override public <T> Observable<T> toObservable(ComposableAction<T> action) {
    //noinspection unchecked
    return (Observable<T>) toObservable(new ComposableAction[]{action});
  }

  @Override public <T> Observable<T> toObservable(String key, ComposableAction<T> action) {
    //noinspection unchecked
    return (Observable<T>) toObservable(key, new ComposableAction[]{action});
  }

  @Override public Observable<Object> toObservable(ComposableAction... actions) {
    return toObservable(mKeySelector.getKey(actions), actions);
  }

  @Override public Observable<Object> toObservable(String key, ComposableAction... actions) {
    //noinspection unchecked
    return getActionObservable(key, actions);
  }

  @Override public <T> Observable<T> toObservableAsync(ComposableAction<T> action) {
    //noinspection unchecked
    return (Observable<T>) toObservableAsync(new ComposableAction[]{action});
  }

  @Override public Observable<Object> toObservableAsync(ComposableAction... actions) {
    //noinspection unchecked
    return getActionObservable(ActionKeySelector.ASYNC_KEY, actions);
  }

  @Override public <T> Observable<T> toObservableAsync(SingularAction<T> action) {
    return toObservable(ActionKeySelector.ASYNC_KEY, action);
  }

  @Override public <T> Observable<T> toObservable(SingularAction<T> action) {
    return toObservable(mKeySelector.getKey(action), action);
  }

  @Override public <T> Observable<T> toObservable(String key, SingularAction<T> action) {
    if (action.isPersistent() && mPersister == null)
      throw new RuntimeException("Can't persist action as no " + ActionPersister.class.getName()
          + " was ever provided at " + JavaActionDispatcher.class.getSimpleName() + " creation.");

    action.setKey(key);

    if (action.isPersistent()) {
      //noinspection unchecked
      return (Observable<T>) getActionPersistentObservable(key, action);
    } else {
      //noinspection unchecked
      return (Observable<T>) getActionObservable(key, action);
    }
  }

  @Override public <T> T runBlocking(Action<T> action) {
    //noinspection unchecked
    return (T) runBlocking(new Action[]{action})[0];
  }

  @Override public Object[] runBlocking(Action... actions) {
    InstantSubscriptionContext context = mInstantSubscriptionContextPool.get(actions);
    runActions(context);

    Object[] responses = context.getResponses();
    mInstantSubscriptionContextPool.release(context);
    return responses;
  }

  private Observable getActionObservable(String key, Action... actions) {
    Observable observable = Observable.create(mActionOnSubscribePool.get(key, actions));
    return postCreateObservable(observable, actions);
  }

  private Observable getActionPersistentObservable(String key, Action... actions) {
    Observable observable = Observable.create(mPersistentActionOnSubscribePool.get(key, actions));
    return postCreateObservable(observable, actions);
  }

  private Observable postCreateObservable(Observable observable, Action[] actions) {
    if (mObserveOnProvider != null)
      observable = observable.observeOn(mObserveOnProvider.provideScheduler(actions));

    return observable;
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
      synchronized (PERSISTENT_QUEUE_RESORE_LOCK) {
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

  private void runActions(SubscriptionContext context) {
    for (Action action : context.getActions()) {
      action.set(context);
    }

    long currentPauseTime = PAUSE_EXPONENTIAL_BACKOFF_MIN_TIME;

    boolean runIfUnsubscribed = false;
    {
      for (Action action : context.getActions()) {
        if (action.shouldRunIfUnsubscribed()) {
          runIfUnsubscribed = true;
          break;
        }
      }
    }

    int actionsLen = context.getActions().length;
    Object[] Objects = new Object[actionsLen];

    for (int i = 0; i < actionsLen; i++) {
      context.setCurrentActionIndex(i);
      Action action = context.getActions()[i];
      mInjector.inject(action);

      if (!runIfUnsubscribed && context.getSubscriber().isUnsubscribed())
        return;

      while (mPauser.shouldPauseForAction(action)) {
        try {
          Thread.sleep(currentPauseTime);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }

        currentPauseTime *= 2;  // Exponential backoff
        if (currentPauseTime > PAUSE_EXPONENTIAL_BACKOFF_MAX_TIME)
          currentPauseTime = PAUSE_EXPONENTIAL_BACKOFF_MAX_TIME;
      }
      currentPauseTime = PAUSE_EXPONENTIAL_BACKOFF_MIN_TIME;

      if (!runIfUnsubscribed && context.getSubscriber().isUnsubscribed())
        return;

      try {
        Objects[i] = action.execute();
      } catch (Throwable t) {
        throw new RuntimeException(t);
      }
    }

    for (Object Object : Objects)
      context.getSubscriber().onNext(Object);

    context.getSubscriber().onCompleted();

    for (Action action : context.getActions()) {
      action.clear();
    }
  }

  /*package*/ class ActionOnSubscribe implements Observable.OnSubscribe<Object>, SubscriptionContext {

    protected Subscriber<? super Object> mSubscriber;
    protected String mKey;

    private Action[] mActions;
    private int mCurrentActionIndex;

    protected final ActionRunnable mActionRunnable = new ActionRunnable() {
      @Override public void execute() {
        runActions(ActionOnSubscribe.this);
      }
    };

    @Override public void call(final Subscriber<? super Object> subscriber) {
      this.mSubscriber = subscriber;

      Executor executor = mExecutorCache.getExecutorForKey(mKey);
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
          mActionRunner.execute(mActionRunnable, mActions);
          isCompleted = true;
        } catch (Throwable t) {
          Action errorAction = mActions[mCurrentActionIndex];
          //noinspection unchecked
          boolean shouldRetry = errorAction.shouldRetryForThrowable(t, mSubscriber);

          if (shouldRetry) {
            onRetryAction(errorAction);
          } else {
            isCompleted = true;
            System.out.println("There was an error running action #"
                + (mCurrentActionIndex + 1) + " " + errorAction.getClass().getSimpleName()
                + ". Retried " + errorAction.getRetryCount() + " times.");
            mSubscriber.onError(t);
          }
        }
      } while(!isCompleted);
    }

    protected void onRetryAction(Action action) {
      action.incrementRetryCount();
    }

    public void set(String key, Action[] actions) {
      this.mKey = key;
      this.mActions = actions;
    }

    @Override public Action[] getActions() {
      return mActions;
    }

    @Override public Subscriber<? super Object> getSubscriber() {
      return mSubscriber;
    }

    @Override public ActionDispatcher getActionDispatcher() {
      return JavaActionDispatcher.this;
    }

    @Override public int getCurrentActionIndex() {
      return mCurrentActionIndex;
    }

    @Override public void setCurrentActionIndex(int currentActionIndex) {
      mCurrentActionIndex = currentActionIndex;
    }

    @Override public boolean isUnsubscribed() {
      return mSubscriber == null || mSubscriber.isUnsubscribed();
    }

  }

  /*package*/ class PersistentActionOnSubscribe extends ActionOnSubscribe {

    private final Semaphore mPersistSemaphore = new Semaphore(1);
    private Long mPersistedId = null;
    private boolean mIsActionAlreadyPersisted = false;

    @Override public void call(final Subscriber<? super Object> subscriber) {
      this.mSubscriber = subscriber;

      if (!mIsActionAlreadyPersisted) {
        mPersistSemaphore.acquireUninterruptibly();
        Runnable persistentRunnable = new Runnable() {
          @Override public void run() {
            mPersistedId = mPersister.persist((SingularAction) getActions()[0]);
            mPersistSemaphore.release();
          }
        };
        runOnExecutor(mPersistentExecutor, persistentRunnable);
      }

      Executor executor = mExecutorCache.getExecutorForKey(mKey);
      Runnable runnable = new Runnable() {
        @Override public void run() {
          mPersistSemaphore.acquireUninterruptibly();

          try {
            runActionRunner();

            mPersistentExecutor.execute(new Runnable() {
              @Override public void run() {
                mPersister.delete(mPersistedId);
                mPersistedId = null;
              }
            });
          } catch (Throwable t) {
            throw new RuntimeException(t);
          } finally {
            mPersistSemaphore.release();
          }
        }
      };
      runOnExecutor(executor, runnable);
    }

    @Override protected void onRetryAction(final Action action) {
      super.onRetryAction(action);
      mPersistentExecutor.execute(new Runnable() {
        @Override public void run() {
          mPersister.update(mPersistedId, (SingularAction) action);
        }
      });
    }

    public void set(String key, Action[] actions, boolean isActionAlreadyPersisted, Long persistedId) {
      set(key, actions);
      this.mIsActionAlreadyPersisted = isActionAlreadyPersisted;
      this.mPersistedId = persistedId;
    }
  }

  /*package*/ class InstantSubscriptionContext implements SubscriptionContext {

    private Action[] mActions;
    private Object[] mResponses;
    private int mCurrentActionIndex;

    private final Subscriber<Object> mSubscriber = new Subscriber<Object>() {
      @Override public void onCompleted() {}
      @Override public void onError(Throwable e) {}
      @Override public void onNext(Object o) {
        mResponses[mCurrentActionIndex] = o;
      }
    };

    @Override public Action[] getActions() {
      return mActions;
    }

    @Override public Subscriber<? super Object> getSubscriber() {
      //noinspection unchecked
      return mSubscriber;
    }

    @Override public ActionDispatcher getActionDispatcher() {
      return JavaActionDispatcher.this;
    }

    @Override public int getCurrentActionIndex() {
      return mCurrentActionIndex;
    }

    @Override public void setCurrentActionIndex(int currentActionIndex) {
      mCurrentActionIndex = currentActionIndex;
    }

    @Override public boolean isUnsubscribed() {
      return mSubscriber.isUnsubscribed();
    }

    public Object[] getResponses() {
      return mResponses;
    }

    public void set(Action[] actions) {
      mActions = actions;
      mResponses = new Object[mActions.length];
      mCurrentActionIndex = 0;
    }

    public void free() {
      mActions = null;
      mResponses = null;
    }
  }

  /*
   *
   * OBJECT POOLS
   *
   */

  /*package*/ class ActionOnSubscribePool extends AbstractSynchronizedObjectPool<ActionOnSubscribe> {

    public ActionOnSubscribePool() {
      super(10);
    }

    @Override protected ActionOnSubscribe create() {
      return new ActionOnSubscribe();
    }

    @Override protected void free(ActionOnSubscribe obj) {
      obj.set(null, null);
    }

    public ActionOnSubscribe get(String key, Action[] actions) {
      ActionOnSubscribe obj = borrow();
      obj.set(key, actions);
      return obj;
    }

  }

  /*package*/ class InstantSubscriptionContextPool extends AbstractSynchronizedObjectPool<InstantSubscriptionContext> {

    public InstantSubscriptionContextPool() {
      super(10);
    }

    @Override protected InstantSubscriptionContext create() {
      return new InstantSubscriptionContext();
    }

    @Override protected void free(InstantSubscriptionContext obj) {
      obj.free();
    }

    public InstantSubscriptionContext get(Action[] actions) {
      InstantSubscriptionContext obj = borrow();
      obj.set(actions);
      return obj;
    }

  }

  /* package */ class PersistentActionOnSubscribePool extends AbstractSynchronizedObjectPool<PersistentActionOnSubscribe> {

    public PersistentActionOnSubscribePool() {
      super(5);
    }

    @Override protected PersistentActionOnSubscribe create() {
      return new PersistentActionOnSubscribe();
    }

    @Override protected void free(PersistentActionOnSubscribe obj) {
      obj.set(null, null);
    }

    public PersistentActionOnSubscribe get(String key, Action[] actions) {
      return get(key, actions, false, null);
    }

    public PersistentActionOnSubscribe get(String key, Action[] actions, boolean isActionAlreadyPersisted,
                                     Long persistedId) {
      PersistentActionOnSubscribe obj = borrow();
      obj.set(key, actions, isActionAlreadyPersisted, persistedId);
      return obj;
    }

  }

}