package com.wmba.actiondispatcher;

import rx.Subscriber;

public abstract class Action<T> {

  private transient SubscriptionContext mContext;

  public abstract T execute() throws Throwable;
  public abstract boolean shouldRetryForThrowable(Throwable throwable, Subscriber<T> subscriber);
  public abstract int getRetryLimit();
  public abstract int getRetryCount();
  public abstract void incrementRetryCount();

  /**
   * @return true if the {@link Action} should run even if the observable has been unsubscribed.
   */
  public boolean shouldRunIfUnsubscribed() {
    return true;
  }

  /*package*/ void set(SubscriptionContext context) {
    mContext = context;
  }

  /*package*/ void clear() {
    mContext = null;
  }

  public boolean isUnsubscribed() {
    return mContext == null || mContext.isUnsubscribed();
  }

  protected T runAction(Action<T> action) {
    return mContext.getActionDispatcher().runBlocking(action);
  }

}