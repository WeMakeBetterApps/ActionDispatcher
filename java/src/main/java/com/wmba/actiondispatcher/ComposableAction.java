package com.wmba.actiondispatcher;

import rx.Subscriber;

public abstract class ComposableAction<T> extends Action<T> {

  private int mRetryCount = 0;

  @Override public boolean shouldRetryForThrowable(Throwable throwable, Subscriber<T> subscriber) {
    return false;
  }

  @Override public int getRetryLimit() {
    return 0;
  }

  @Override public int getRetryCount() {
    return mRetryCount;
  }

  @Override public void incrementRetryCount() {
    mRetryCount++;
  }

  protected <V> V subscribeBlocking(ComposableAction<V> action) {
    ActionDispatcher ad = getSubscriptionContext().getActionDispatcher();
    if (ad instanceof JavaActionDispatcher) {
      return ((JavaActionDispatcher) ad).subscribeBlocking(getSubscriptionContext(), action);
    } else {
      throw new RuntimeException(String.format("The implementation of ActionDispatcher must be a" +
          "child of %s to use #subscribeBlocking()", JavaActionDispatcher.class.getSimpleName()));
    }
  }
  
}
