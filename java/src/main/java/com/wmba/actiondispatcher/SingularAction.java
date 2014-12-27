package com.wmba.actiondispatcher;

import java.io.Serializable;

import rx.Subscriber;

public abstract class SingularAction<T> extends Action<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final int DEFAULT_RETRY_LIMIT = 20;

  private int mRetryCount = 0;
  private final boolean mIsPersistent;
  private String mKey;

  public SingularAction(boolean isPersistent) {
    this.mIsPersistent = isPersistent;
  }

  @Override public boolean shouldRetryForThrowable(Throwable throwable, Subscriber<T> subscriber) {
    return (mRetryCount < getRetryLimit());
  }

  @Override public int getRetryLimit() {
    return DEFAULT_RETRY_LIMIT;
  }

  @Override public int getRetryCount() {
    return mRetryCount;
  }

  @Override public void incrementRetryCount() {
    mRetryCount++;
  }

  /* package */ void setKey(String key) {
    this.mKey = key;
  }

  public String getKey() {
    return mKey;
  }

  public boolean isPersistent() {
    return mIsPersistent;
  }
}
