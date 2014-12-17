package com.wmba.actiondispatcher;

/* package */ interface ObjectPool<T> {
  T borrow();
  boolean release(T obj);
}
