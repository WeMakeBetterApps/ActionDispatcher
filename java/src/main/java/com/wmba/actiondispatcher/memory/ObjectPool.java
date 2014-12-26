package com.wmba.actiondispatcher.memory;

/* package */ interface ObjectPool<T> {
  T borrow();
  boolean release(T obj);
}
