package com.wmba.actiondispatcher;

import java.util.Set;

import rx.Observable;

public interface ActionDispatcher {

  public static final long PAUSE_EXPONENTIAL_BACKOFF_MIN_TIME = 100;
  public static final long PAUSE_EXPONENTIAL_BACKOFF_MAX_TIME = 3000;

  Set<String> getActiveKeys();

  <T> Observable<T> toObservable(Action<T> action);

  <T> Observable<T> toObservable(String key, Action<T> action);

  <T> Observable<T> toObservable(ComposableAction<T> action);

  <T> Observable<T> toObservable(String key, ComposableAction<T> action);

  Observable<Object> toObservable(ComposableAction... actions);

  Observable<Object> toObservable(String key, ComposableAction... actions);

  <T> Observable<T> toObservableAsync(ComposableAction<T> action);

  Observable<Object> toObservableAsync(ComposableAction... actions);

  <T> Observable<T> toObservableAsync(SingularAction<T> action);

  <T> Observable<T> toObservable(SingularAction<T> action);

  <T> Observable<T> toObservable(String key, SingularAction<T> action);

  <T> T runBlocking(Action<T> action);

  Object[] runBlocking(Action... actions);
}
