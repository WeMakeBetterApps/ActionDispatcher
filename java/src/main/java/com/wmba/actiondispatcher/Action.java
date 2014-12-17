package com.wmba.actiondispatcher;

import rx.Subscriber;

public interface Action<T> {

  T execute() throws Throwable;
  boolean shouldRetryForThrowable(Throwable throwable, Subscriber<T> subscriber);
  int getRetryLimit();
  int getRetryCount();
  void incrementRetryCount();

}