package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.DelayedActionPersister;
import com.wmba.actiondispatcher.component.SimpleAction;
import com.wmba.actiondispatcher.component.SimplePersistentAction;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import rx.observers.TestSubscriber;

import static org.junit.Assert.*;

public class PersistTests {
  @Test public void persistedActionsCanBeGuaranteedFirst() {
    DelayedActionPersister persister = new DelayedActionPersister();

    final AtomicInteger count = new AtomicInteger();

    // Persist the action before building the dispatcher
    SimplePersistentAction action = new SimplePersistentAction() {
      @Override public Boolean execute() throws Throwable {
        assertEquals(1, count.incrementAndGet());
        return super.execute();
      }
    };
    persister.persist(action);

    assertTrue(persister.isPersisted(action));

    ActionDispatcher dispatcher = buildDispatcher(persister);
    dispatcher.startPersistentActions();

    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle("separateThread", new SimpleAction() {
      @Override public Boolean execute() throws Throwable {
        assertEquals(2, count.incrementAndGet());
        return super.execute();
      }
    }).subscribe(ts);
    ts.awaitTerminalEvent();

    assertFalse(persister.isPersisted(action));
  }

  @Test public void persistedActionsCanBeRunSecond() {
    DelayedActionPersister persister = new DelayedActionPersister();

    final AtomicInteger count = new AtomicInteger();
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    // Persist the action before building the dispatcher
    SimplePersistentAction action = new SimplePersistentAction() {
      @Override public Boolean execute() throws Throwable {
        countDownLatch.countDown();
        assertEquals(2, count.incrementAndGet());
        return super.execute();
      }
    };
    persister.persist(action);

    assertTrue(persister.isPersisted(action));

    ActionDispatcher dispatcher = buildDispatcher(persister);

    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle(new SimpleAction())
        .subscribe(ts);
    ts.awaitTerminalEvent(); // Wait for Actions to load.

    dispatcher.toSingle(new SimpleAction() {
      @Override public Boolean execute() throws Throwable {
        assertEquals(1, count.incrementAndGet());
        return super.execute();
      }
    }).subscribe();

    dispatcher.startPersistentActions();

    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private ActionDispatcher buildDispatcher(ActionPersister persister) {
    return new ActionDispatcher.Builder()
        .withActionPersister(persister)
        .build();
  }
}
