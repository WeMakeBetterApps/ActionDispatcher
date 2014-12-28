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

  protected <V> V subscribeBlocking(Action<V> action) {
    ActionDispatcher ad = mContext.getActionDispatcher();
    if (ad instanceof JavaActionDispatcher) {
      return ((JavaActionDispatcher) ad).subscribeBlocking(mContext, action);
    } else {
      throw new RuntimeException(String.format("The implementation of ActionDispatcher must be a" +
          "child of %s to use #subscribeBlocking()", JavaActionDispatcher.class.getSimpleName()));
    }
  }

}