package com.wmba.actiondispatcher;

import java.util.Set;

import rx.Observable;

public interface ActionDispatcher {
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
}
