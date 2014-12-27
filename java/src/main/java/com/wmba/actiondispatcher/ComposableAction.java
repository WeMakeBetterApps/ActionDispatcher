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

}
