package com.wmba.actiondispatcher;

import rx.Scheduler;

public abstract class Action<T> {
  private SubscriptionContext mSubscriptionContext = null;
  private int mRetryCount = -1;

  public void prepare() {}

  public void preRetry() {}

  public abstract T execute() throws Throwable;

  public boolean shouldRetryForThrowable(Throwable t) {
    mRetryCount++;
    return mRetryCount < getRetryLimit();
  }

  public int getRetryLimit() {
    return 0;
  }

  public boolean runIfUnsubscribed() {
    return true;
  }

  public String getKey() {
    return KeySelector.DEFAULT_KEY;
  }

  public Scheduler observeOn() {
    return null;
  }

  public int getRetryCount() {
    return (mRetryCount >= 0) ? mRetryCount : 0;
  }

  protected boolean isUnsubscribed() {
    return mSubscriptionContext != null && mSubscriptionContext.isUnsubscribed();
  }

  /**
   * Runs an action inside of the lifecycle of another Action synchronously. Whether or not an
   * action is persistent is ignored for this call.
   *
   * @param action the Action to run.
   * @return the response of the Action.
   * @throws Throwable
   */
  protected <R> R subscribeBlocking(Action<R> action) throws Throwable {
    if (mSubscriptionContext == null) throw new IllegalStateException("SubscriptionContext is null. " +
        "subscribeBlocking() can only be called from within the Action lifecycle.");
    return mSubscriptionContext.getDispatcher().subscribeBlocking(mSubscriptionContext, action);
  }

  public boolean isPersistent() {
    return false;
  }

  /* package */ SubscriptionContext getSubscriptionContext() {
    return mSubscriptionContext;
  }

  /* package */ void setSubscriptionContext(SubscriptionContext subscriptionContext) {
    mSubscriptionContext = subscriptionContext;
  }
}
