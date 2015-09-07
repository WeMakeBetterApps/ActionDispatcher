package com.wmba.actiondispatcher;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import rx.observers.TestSubscriber;

import static org.junit.Assert.assertEquals;

public class RetryTests {
  @Test public void retryTest() {
    ActionDispatcher dispatcher = new ActionDispatcher.Builder().build();

    final AtomicInteger prepareCount = new AtomicInteger();
    final AtomicInteger preRetryCount = new AtomicInteger();
    final AtomicInteger executeCount = new AtomicInteger();
    final AtomicInteger shouldRetryForThrowableCount = new AtomicInteger();

    final int totalRunCount = 20;
    final AtomicInteger count = new AtomicInteger(0);

    Action<Boolean> action = new Action<Boolean>() {
      private Thread mThread = null;

      @Override public void prepare() {
        super.prepare();
        checkThread();
        assertEquals(1, prepareCount.incrementAndGet());
      }

      @Override public void preRetry() {
        super.preRetry();
        checkThread();
        assertEquals(getRetryCount(), preRetryCount.incrementAndGet());
      }

      @Override public Boolean execute() throws Throwable {
        checkThread();
        assertEquals(executeCount.getAndIncrement(), getRetryCount());

        if (count.incrementAndGet() <= totalRunCount) {
          throw new RuntimeException();
        }

        return true;
      }

      @Override public boolean shouldRetryForThrowable(Throwable t) {
        checkThread();
        assertEquals(executeCount.get(), shouldRetryForThrowableCount.incrementAndGet());
        return super.shouldRetryForThrowable(t);
      }

      @Override public int getRetryLimit() {
        return totalRunCount;
      }

      private void checkThread() {
        if (mThread == null) {
          mThread = Thread.currentThread();
        } else {
          assertEquals(mThread, Thread.currentThread());
        }
      }
    };

    assertEquals(0, action.getRetryCount());

    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle(action).subscribe(ts);

    ts.awaitTerminalEvent();

    assertEquals(1, prepareCount.get());
    assertEquals(totalRunCount - 1, preRetryCount.get());
    assertEquals(totalRunCount, executeCount.get());
    assertEquals(totalRunCount, shouldRetryForThrowableCount.get());
  }
}
