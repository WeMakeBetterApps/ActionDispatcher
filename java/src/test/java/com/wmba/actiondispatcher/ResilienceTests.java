package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.InstantActionPersister;
import com.wmba.actiondispatcher.component.SimpleAction;
import com.wmba.actiondispatcher.component.SimplePersistentAction;
import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import rx.observers.TestSubscriber;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ResilienceTests {
  @Test public void handleExceptionRetrievingActionsTest() {
    InstantActionPersister persister = mock(InstantActionPersister.class, CALLS_REAL_METHODS);
    doThrow(new RuntimeException("getPersistedActions() Error")).when(persister).getPersistedActions();
    doThrow(new RuntimeException("deleteAll() Error")).when(persister).deleteAll();

    ActionDispatcher dispatcher = new ActionDispatcher.Builder()
        .withActionPersister(persister)
        .build();

    assertFalse(dispatcher.arePersistentActionsLoaded());
    dispatcher.startPersistentActions();

    DispatcherUtil.waitForPersistentActionsToLoad(dispatcher);
    DispatcherUtil.subscribeActionBlocking(dispatcher);
  }

  @Test public void handleExceptionExecutingPersistentActionsTest() {
    InstantActionPersister persister = mock(InstantActionPersister.class, CALLS_REAL_METHODS);
    List<PersistedActionHolder> persistedActionHolders = new ArrayList<PersistedActionHolder>();
    persistedActionHolders.add(new PersistedActionHolder(0, new SimpleAction()));
    doReturn(persistedActionHolders).when(persister).getPersistedActions();

    assertEquals(persistedActionHolders, persister.getPersistedActions());

    ActionDispatcher dispatcher = new ActionDispatcher.Builder()
        .withActionPersister(persister)
        .build();

    assertFalse(dispatcher.arePersistentActionsLoaded());
    dispatcher.startPersistentActions();

    DispatcherUtil.waitForPersistentActionsToLoad(dispatcher);
    DispatcherUtil.subscribeActionBlocking(dispatcher);
  }

  @Test public void actionRunsWithPersistExceptionTest() {
    InstantActionPersister persister = new InstantActionPersister() {
      @Override public synchronized long persist(Action<?> action) {
        throw new RuntimeException();
      }
    };

    ActionDispatcher dispatcher = new ActionDispatcher.Builder()
        .withActionPersister(persister)
        .build();

    assertFalse(dispatcher.arePersistentActionsLoaded());
    dispatcher.startPersistentActions();
    DispatcherUtil.waitForPersistentActionsToLoad(dispatcher);

    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    SimplePersistentAction action = new SimplePersistentAction();
    dispatcher.toSingle(action).subscribe(ts);
    ts.awaitTerminalEvent();
    assertFalse(persister.isPersisted(action));
    ts.assertNoErrors();
    ts.assertValue(true);
  }
}
