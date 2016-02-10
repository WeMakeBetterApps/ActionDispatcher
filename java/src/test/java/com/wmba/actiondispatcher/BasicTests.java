package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.SimpleAction;

import org.junit.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import rx.observers.TestSubscriber;

import static org.junit.Assert.*;

public class BasicTests {
  private ActionDispatcher createDispatcher() {
    return new ActionDispatcher.Builder().build();
  }

  @Test public void simpleTest() {
    ActionDispatcher dispatcher = createDispatcher();

    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle(new SimpleAction())
        .subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertValue(true);

    Set<String> activeKeys = dispatcher.getActiveKeys();
    assertEquals(1, activeKeys.size());
    assertTrue(activeKeys.contains(KeySelector.DEFAULT_KEY));
  }

  @Test public void runAsyncTest() {
    ActionDispatcher dispatcher = createDispatcher();

    final AtomicInteger count = new AtomicInteger();

    final Long[] action1ThreadId = new Long[1];
    final Long[] action2ThreadId = new Long[1];
    final String[] action1ThreadName = new String[1];
    final String[] action2ThreadName = new String[1];

    TestSubscriber<Boolean> ts1 = new TestSubscriber<Boolean>();
    TestSubscriber<Boolean> ts2 = new TestSubscriber<Boolean>();

    dispatcher.toSingle(new Action<Boolean>() {
      @Override public Boolean execute() throws Throwable {
        Thread.sleep(10);

        assertEquals(2, count.incrementAndGet());

        action1ThreadId[0] = Thread.currentThread().getId();
        action1ThreadName[0] = Thread.currentThread().getName();

        return true;
      }
    }).subscribe(ts1);

    dispatcher.toSingle(new Action<Boolean>() {
      @Override public Boolean execute() throws Throwable {
        assertEquals(1, count.incrementAndGet());

        action2ThreadId[0] = Thread.currentThread().getId();
        action2ThreadName[0] = Thread.currentThread().getName();

        return false;
      }

      @Override public String getKey() {
        return "key2";
      }
    }).subscribe(ts2);

    ts2.awaitTerminalEvent();
    ts1.assertNotCompleted();

    ts2.assertValue(false);

    ts1.awaitTerminalEvent();
    ts1.assertValue(true);

    assertNotNull(action1ThreadId[0]);
    assertNotNull(action2ThreadId[0]);
    assertNotNull(action1ThreadName[0]);
    assertNotNull(action2ThreadName[0]);
    assertNotEquals(action1ThreadId[0], action2ThreadId[0]);
    assertNotEquals(action1ThreadName[0], action2ThreadName[0]);

    Set<String> activeKeys = dispatcher.getActiveKeys();
    assertEquals(2, activeKeys.size());
    assertTrue(activeKeys.contains("key2"));
    assertTrue(activeKeys.contains(KeySelector.DEFAULT_KEY));
  }

  @Test public void returnValueTest() {
    ActionDispatcher dispatcher = createDispatcher();

    TestSubscriber<String> ts = new TestSubscriber<String>();
    dispatcher.toSingle(new Action<String>() {
      @Override public String execute() throws Throwable {
        return "value";
      }
    }).subscribe(ts);

    ts.awaitTerminalEvent();
    ts.assertValue("value");
  }
}
