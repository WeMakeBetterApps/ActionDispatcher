package com.wmba.actiondispatcher.android;

import android.content.Context;

import com.wmba.actiondispatcher.Action;
import com.wmba.actiondispatcher.ActionDispatcher;
import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.Serializable;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AndroidPersistTest {

  private ActionDispatcher buildDispatcher() {
    Context context = Robolectric.getShadowApplication().getApplicationContext();
    return new ActionDispatcher.Builder()
        .withActionPersister(new AndroidActionPersister(context))
        .build();
  }

  @Test public void persistTest() {
    AndroidActionPersister persister = buildPersister();
    resetDatabase(persister);

    TestAction action = new TestAction();
    action.testInt = 15;
    persister.persist(action);

    List<PersistedActionHolder> persistedActions = persister.getPersistedActions();
    assertEquals(persistedActions.size(), 1);
    TestAction persistedAction = (TestAction) persistedActions.get(0).getAction();
    assertEquals(action.testInt, persistedAction.testInt);
  }

  @Test public void updateTest() {
    AndroidActionPersister persister = buildPersister();
    resetDatabase(persister);

    TestAction action = new TestAction();
    action.testInt = 15;
    long id = persister.persist(action);

    List<PersistedActionHolder> persistedActions = persister.getPersistedActions();
    assertEquals(persistedActions.size(), 1);
    TestAction persistedAction = (TestAction) persistedActions.get(0).getAction();
    assertEquals(action.testInt, persistedAction.testInt);
    assertEquals(persistedAction.getRetryCount(), 0);

    persistedAction.testInt = 3;
    persister.update(id, persistedAction);

    List<PersistedActionHolder> persistedActions2 = persister.getPersistedActions();
    assertEquals(persistedActions2.size(), 1);
    TestAction persistedAction2 = (TestAction) persistedActions.get(0).getAction();
    assertEquals(persistedAction.testInt, persistedAction2.testInt);
  }

  @Test public void orderTest() {
    AndroidActionPersister persister = buildPersister();
    resetDatabase(persister);

    TestAction action1 = new TestAction();
    TestAction2 action2 = new TestAction2();

    persister.persist(action1);
    persister.persist(action2);

    List<PersistedActionHolder> persistedActions = persister.getPersistedActions();
    assertEquals(persistedActions.size(), 2);

    assertTrue(persistedActions.get(0).getAction() instanceof TestAction);
    assertTrue(persistedActions.get(1).getAction() instanceof TestAction2);
  }

  private AndroidActionPersister buildPersister() {
    Context context = Robolectric.getShadowApplication().getApplicationContext();
    return new AndroidActionPersister(context);
  }

  private void resetDatabase(AndroidActionPersister persister) {
    persister.deleteAll();
    assertEquals(persister.getPersistedActions().size(), 0);
  }

  public static class TestAction extends Action<TestResponse> implements Serializable {
    private int testInt;

    @Override public TestResponse execute() throws Throwable {
      return new TestResponse();
    }

    @Override public boolean isPersistent() {
      return true;
    }
  }

  public static class TestAction2 extends Action<TestResponse> implements Serializable {
    private int testInt;

    @Override public TestResponse execute() throws Throwable {
      return new TestResponse();
    }

    @Override public boolean isPersistent() {
      return true;
    }
  }

  private static class TestResponse {}

}