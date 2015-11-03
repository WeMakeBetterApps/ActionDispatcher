package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.InstantActionPersister;
import com.wmba.actiondispatcher.component.SimpleAction;
import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ResilienceTests {
  @Test public void handleExceptionRetrievingActions() {
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

  @Test public void handleExceptionExecutingPersistentActions() {
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
}
