package com.wmba.actiondispatcher;

/**
 * A thread-safe implementation of an object pool.
 *
 * @param <T> The type of the pooled object.
 */
/* package */ abstract class AbstractSynchronizedObjectPool<T> implements ObjectPool<T> {

  public static final int DEFAULT_CAPACITY = 10;

  private int mIndex = -1;
  private final Object mLock = new Object();
  private final Object[] mObjectPool;
  private final int mMaxIndex;

  public AbstractSynchronizedObjectPool() {
    this(DEFAULT_CAPACITY);
  }

  public AbstractSynchronizedObjectPool(int capacity) {
    if (capacity <= 0)
      throw new IllegalArgumentException("Capacity must be greater than 0.");
    this.mObjectPool = new Object[capacity];
    this.mMaxIndex = capacity - 1;
  }

  public int size() {
    return mIndex + 1;
  }

  public T borrow() {
    T object;

    synchronized (mLock) {

      if (mIndex >= 0) {
        //noinspection unchecked
        object = (T) mObjectPool[mIndex];
        mObjectPool[mIndex] = null;
        mIndex--;
      } else {
        object = create();
      }

    }

    return object;
  }

  public boolean release(T obj) {
    if (obj == null)
      return false;

    free(obj);

    synchronized (mLock) {

      int newIndex = mIndex + 1;
      if (newIndex <= mMaxIndex) {
        mObjectPool[newIndex] = obj;
        mIndex = newIndex;
        return true;
      }

      return false;
    }
  }

  protected abstract T create();
  protected abstract void free(T obj);

}
