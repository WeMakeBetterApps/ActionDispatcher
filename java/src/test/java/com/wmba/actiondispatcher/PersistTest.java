package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.SimpleAction;
import com.wmba.actiondispatcher.component.SimplePersistentAction;
import com.wmba.actiondispatcher.component.TestActionPersister;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import rx.observers.TestSubscriber;

import static org.junit.Assert.*;

public class PersistTest {
  @Test public void persistedActionsCanBeGuaranteedFirst() {
    TestActionPersister persister = new TestActionPersister() {
      @Override public List<PersistedActionHolder> getPersistedActions() {
        // Add some time to getting the actions.
        try {
          Thread.sleep(5L);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        return super.getPersistedActions();
      }
    };

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

  private ActionDispatcher buildDispatcher(ActionPersister persister) {
    return new ActionDispatcher.Builder()
        .withActionPersister(persister)
        .build();
  }
}
