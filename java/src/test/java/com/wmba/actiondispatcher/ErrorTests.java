package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.ErrorAction;
import com.wmba.actiondispatcher.component.TestException;

import org.junit.Test;

import rx.observers.TestSubscriber;

import static org.junit.Assert.*;

public class ErrorTests {
  private ActionDispatcher createDispatcher() {
    return new ActionDispatcher.Builder().build();
  }

  @Test public void simpleErrorTest() {
    ActionDispatcher dispatcher = createDispatcher();

    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle(new ErrorAction()).subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertError(TestException.class);
  }

  @Test public void subscribeBlockingErrorTest() {
    ActionDispatcher dispatcher = createDispatcher();

    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle(new SubscribeBlockingErrorActionLevel1())
        .subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertNoValues();
    ts.assertError(TestException.class);
  }

  @Test public void nestedSubscribeBlockingErrorTest() {
    ActionDispatcher dispatcher = createDispatcher();

    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle(new SubscribeBlockingErrorActionLevel3())
        .subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertNoValues();
    ts.assertError(TestException.class);
  }

  @Test public void nestedSubscribeBlockingErrorHandlingTest() {
    final Action<Boolean> errorAction1 = new Action<Boolean>() {
      @Override public Boolean execute() throws Throwable {
        return subscribeBlocking(new ErrorAction(300));
      }

      @Override public boolean shouldRetryForThrowable(Throwable t) {
        assertTrue(t instanceof TestException);
        assertEquals(300, ((TestException) t).getValue());
        throw new TestException(1);
      }
    };

    final Action<Boolean> errorAction2 = new Action<Boolean>() {
      @Override public Boolean execute() throws Throwable {
        return subscribeBlocking(errorAction1);
      }

      @Override public boolean shouldRetryForThrowable(Throwable t) {
        assertTrue(t instanceof TestException);
        assertEquals(1, ((TestException) t).getValue());
        throw new TestException(2);
      }
    };

    Action<Boolean> errorAction3 = new Action<Boolean>() {
      @Override public Boolean execute() throws Throwable {
        return subscribeBlocking(errorAction2);
      }

      @Override public boolean shouldRetryForThrowable(Throwable t) {
        assertTrue(t instanceof TestException);
        assertEquals(2, ((TestException) t).getValue());
        throw new TestException(3);
      }
    };

    ActionDispatcher dispatcher = createDispatcher();
    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle(errorAction3)
        .subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertNoValues();
    ts.assertError(TestException.class);

    TestException te = (TestException) ts.getOnErrorEvents().get(0);
    assertEquals(3, te.getValue());
  }

  private static class SubscribeBlockingErrorActionLevel1 extends Action<Boolean> {
    @Override public Boolean execute() throws Throwable {
      return subscribeBlocking(new ErrorAction());
    }
  }

  private static class SubscribeBlockingErrorActionLevel2 extends Action<Boolean> {
    @Override public Boolean execute() throws Throwable {
      return subscribeBlocking(new SubscribeBlockingErrorActionLevel1());
    }
  }

  private static class SubscribeBlockingErrorActionLevel3 extends Action<Boolean> {
    @Override public Boolean execute() throws Throwable {
      return subscribeBlocking(new SubscribeBlockingErrorActionLevel2());
    }
  }

  private static class SubscribeBlockingThrowsErrorActionLevel1 extends Action<Boolean> {
    @Override public Boolean execute() throws Throwable {
      return subscribeBlocking(new SubscribeBlockingThrowsErrorActionLevel2());
    }

    @Override public boolean shouldRetryForThrowable(Throwable t) {
      throw new TestException(1);
    }
  }

  private static class SubscribeBlockingThrowsErrorActionLevel2 extends Action<Boolean> {
    @Override public Boolean execute() throws Throwable {
      return subscribeBlocking(new SubscribeBlockingThrowsErrorActionLevel1());
    }

    @Override public boolean shouldRetryForThrowable(Throwable t) {
      throw new TestException(2);
    }
  }

  private static class SubscribeBlockingThrowsErrorActionLevel3 extends Action<Boolean> {
    @Override public Boolean execute() throws Throwable {
      return subscribeBlocking(new SubscribeBlockingThrowsErrorActionLevel2());
    }

    @Override public boolean shouldRetryForThrowable(Throwable t) {
      throw new TestException(3);
    }
  }
}
