package com.wmba.actiondispatcher;

import rx.Scheduler;

public abstract class Action<T> {
  private final boolean mIsPersistent;

  private SubscriptionContext mSubscriptionContext = null;
  private int mRetryCount = -1;

  public Action() {
    this(false);
  }

  public Action(boolean isPersistent) {
    mIsPersistent = isPersistent;
  }

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

  protected <T> T subscribeBlocking(Action<T> action) throws Throwable {
    if (mSubscriptionContext == null) throw new IllegalStateException("SubscriptionContext is null. " +
        "subscribeBlocking() can only be called from within the Action lifecycle.");
    return mSubscriptionContext.getDispatcher().subscribeBlocking(mSubscriptionContext, action);
  }

  public boolean isPersistent() {
    return mIsPersistent;
  }

  /* package */ SubscriptionContext getSubscriptionContext() {
    return mSubscriptionContext;
  }

  /* package */ void setSubscriptionContext(SubscriptionContext subscriptionContext) {
    mSubscriptionContext = subscriptionContext;
  }
}
