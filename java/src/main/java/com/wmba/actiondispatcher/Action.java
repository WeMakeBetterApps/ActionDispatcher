package com.wmba.actiondispatcher;

import rx.Scheduler;

public abstract class Action<T> {
  private transient SubscriptionContext mSubscriptionContext = null;
  private int mRetryCount = -1;

  /**
   * Called once before the action is run, allowing some setup / preparation to be done on the
   * Action, such as injecting it.
   */
  public void prepare() {}

  /**
   * Called before the Action is retried. Only called after the Action fails at least once.
   */
  public void preRetry() {}

  public abstract T execute() throws Throwable;

  /**
   * Called after a Throwable is thrown inside of execute(). By returning false onError() will be
   * called on this Actions corresponding Observable with the Throwable provided. If the Observable
   * should receive a different Throwable to onError, simply throw that exception inside this method.
   *
   * @param t the Throwable that caused the Action to fail.
   * @return true if the Action should retry, false otherwise.
   */
  public boolean shouldRetryForThrowable(Throwable t) {
    return ++mRetryCount < getRetryLimit();
  }

  public int getRetryLimit() {
    return 0;
  }

  /**
   * @return true if this Action should continue to be run / retried if it's Observable has been
   * unsubscribed from.
   */
  public boolean runIfUnsubscribed() {
    return true;
  }

  /**
   * @return The key for the Thread that this action should run on when a key isn't provided
   * directly to the ActionDispatcher.
   */
  public String getKey() {
    return KeySelector.DEFAULT_KEY;
  }

  /**
   * @return the default Scheduler the result of this Action should return on.
   */
  public Scheduler observeOn() {
    return null;
  }

  public int getRetryCount() {
    return (mRetryCount >= 0) ? mRetryCount : 0;
  }

  /**
   * @return true if the corresponding Observable for this action has been unsubscribed to,
   * otherwise false.
   */
  protected final boolean isUnsubscribed() {
    return mSubscriptionContext != null && mSubscriptionContext.isUnsubscribed();
  }

  /**
   * Runs an action inside of the lifecycle of another Action synchronously. Whether or not an
   * action is persistent is ignored for this call.
   *
   * @param action the Action to run.
   * @return the response of the Action.
   */
  protected <R> R subscribeBlocking(Action<R> action) throws Throwable {
    if (mSubscriptionContext == null) throw new IllegalStateException("SubscriptionContext is null. " +
        "subscribeBlocking() can only be called from within the Action lifecycle.");
    return mSubscriptionContext.getDispatcher().subscribeBlocking(mSubscriptionContext, action);
  }

  /**
   * @return true if this action should be persisted before running. If for some reason the program
   * running this action exits before completing the Action, it can be restored and run at a later
   * time.
   */
  public boolean isPersistent() {
    return false;
  }

  /* package */ final SubscriptionContext getSubscriptionContext() {
    return mSubscriptionContext;
  }

  /* package */ final void setSubscriptionContext(SubscriptionContext subscriptionContext) {
    mSubscriptionContext = subscriptionContext;
  }
}
