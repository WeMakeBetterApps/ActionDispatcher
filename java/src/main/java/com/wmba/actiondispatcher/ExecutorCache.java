package com.wmba.actiondispatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/* package */ class ExecutorCache {
  private final Map<String, Executor> mCache = new HashMap<String, Executor>();

  Executor getExecutorForKey(final String key) {
    synchronized (mCache) {
      Executor executor = mCache.get(key);

      if (executor == null) {
        if (KeySelector.ASYNC_KEY.equals(key)) {
          final AtomicLong threadCount = new AtomicLong(1);
          executor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "ActionDispatcherThread-" + key + "-" + threadCount.getAndIncrement());
              t.setPriority(Thread.MIN_PRIORITY);
              t.setDaemon(true);
              return t;
            }
          });
        } else {
          executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "ActionDispatcherThread-" + key);
              t.setPriority(Thread.MIN_PRIORITY);
              t.setDaemon(true);
              return t;
            }
          });
        }

        mCache.put(key, executor);
      }

      return executor;
    }
  }

  void setExecutor(Executor executor, String key) {
    synchronized (mCache) {
      mCache.put(key, executor);
    }
  }

  Set<String> getActiveKeys() {
    synchronized (mCache) {
      return mCache.keySet();
    }
  }
}
