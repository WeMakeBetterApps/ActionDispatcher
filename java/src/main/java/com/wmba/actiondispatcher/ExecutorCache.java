package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.ActionKeySelector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/*package*/ class ExecutorCache {

  private final Object CACHE_LOCK = new Object();
  private final Map<String, ExecutorService> mExecutorCache = new HashMap<String, ExecutorService>();

  /*package*/ ExecutorService getExecutorForKey(final String key) {
    ExecutorService executor = mExecutorCache.get(key);

    if (executor == null) {
      synchronized (CACHE_LOCK) {

        executor = mExecutorCache.get(key);
        if (executor == null) {
          //noinspection NullableProblems
          ThreadFactory tf = new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
              Thread t = new Thread(runnable, "ActionDispatcherThread-" + key);
              t.setPriority(Thread.MIN_PRIORITY);
              t.setDaemon(true);
              return t;
            }
          };

          if (key.equals(ActionKeySelector.ASYNC_KEY)) {
            // Custom for async
            executor = Executors.newCachedThreadPool(tf);
          } else {
            executor = Executors.newSingleThreadScheduledExecutor(tf);
          }

          mExecutorCache.put(key, executor);
        }

      }
    }

    return executor;
  }

  /*package*/ Set<String> getActiveKeys() {
    synchronized (CACHE_LOCK) {
      return mExecutorCache.keySet();
    }
  }

}
