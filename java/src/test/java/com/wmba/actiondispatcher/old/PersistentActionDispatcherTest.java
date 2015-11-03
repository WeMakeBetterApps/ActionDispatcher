package com.wmba.actiondispatcher.old;

import com.wmba.actiondispatcher.Action;
import com.wmba.actiondispatcher.ActionDispatcher;
import com.wmba.actiondispatcher.ActionPreparer;
import com.wmba.actiondispatcher.KeySelector;
import com.wmba.actiondispatcher.component.DelayedActionPersister;
import com.wmba.actiondispatcher.component.InstantActionPersister;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersistentActionDispatcherTest {

  private ActionDispatcher buildDispatcher() {
    return buildDispatcher(null);
  }

  private ActionDispatcher buildDispatcher(final InstantActionPersister persister) {
    return new ActionDispatcher.Builder()
        .withActionPreparer(new ActionPreparer() {
          @Override public void prepare(Action<?> action) {
            assertNotNull(action);
            if (persister != null) {
              assertTrue(persister.isPersisted(action));
            }
          }
        })
        .withKeySelector(new KeySelector() {
          @Override public String getKey(Action<?> action) {
            assertNotNull(action);
            if (persister != null) {
              assertFalse(persister.isPersisted(action));
            }
            return super.getKey(action);
          }
        })
        .withActionPersister(persister)
        .build();
  }

  @Test public void persistentFailTest() {
    Action bogusPersistentAction = new Action<TestResponse>() {
      @Override public TestResponse execute() throws Throwable {
        return new TestResponse();
      }

      @Override public boolean isPersistent() {
        return true;
      }
    };
    Action bogusSingularAction = new Action<TestResponse>() {
      @Override public TestResponse execute() throws Throwable {
        return new TestResponse();
      }

      @Override public boolean isPersistent() {
        return true;
      }
    };

    ActionDispatcher dispatcher = buildDispatcher();

    // Persistent
    boolean isFailed = false;
    try {
      dispatcher.toSingle(bogusPersistentAction).subscribe();
    } catch (Throwable t) {
      isFailed = true;
    } finally {
      assertTrue(isFailed);
    }

    isFailed = false;
    try {
      dispatcher.toSingle(bogusPersistentAction);
    } catch (Throwable t) {
      isFailed = true;
    } finally {
      assertTrue(isFailed);
    }

    isFailed = false;
    try {
      dispatcher.toSingle("test1", bogusPersistentAction);
    } catch (Throwable t) {
      isFailed = true;
    } finally {
      assertTrue(isFailed);
    }

    isFailed = false;
    try {
      dispatcher.toSingle("test2", bogusPersistentAction);
    } catch (Throwable t) {
      isFailed = true;
    } finally {
      assertTrue(isFailed);
    }

    // Singular
    isFailed = false;
    try {
      dispatcher.toSingle(bogusSingularAction);
    } catch (Throwable t) {
      isFailed = true;
    } finally {
      assertFalse(isFailed);
    }

    isFailed = false;
    try {
      dispatcher.toSingle(bogusSingularAction);
    } catch (Throwable t) {
      isFailed = true;
    } finally {
      assertFalse(isFailed);
    }

    isFailed = false;
    try {
      dispatcher.toSingle("test1", (Action) bogusSingularAction);
    } catch (Throwable t) {
      isFailed = true;
    } finally {
      assertFalse(isFailed);
    }

    isFailed = false;
    try {
      dispatcher.toSingle("test2", bogusSingularAction);
    } catch (Throwable t) {
      isFailed = true;
    } finally {
      assertFalse(isFailed);
    }
  }

  @Test public void persistentInstantTest() {
    persistentTest(new InstantActionPersister());
  }

  @Test public void persistentDelayedTest() {
    persistentTest(new DelayedActionPersister(10L));
  }

  private void persistentTest(InstantActionPersister persister) {
    ActionDispatcher dispatcher = buildDispatcher(persister);

    LongPersistentAction action = new LongPersistentAction();

    LongResponse response = dispatcher.toSingle(action)
        .toObservable()
        .toBlocking()
        .first();

    assertNotNull(response);

    try {
      Thread.sleep(200L);
    } catch (InterruptedException ignored) {
    }

    assertFalse(persister.isPersisted(action));
  }

  private class LongPersistentAction extends Action<LongResponse> {
    @Override public LongResponse execute() throws Throwable {
      Thread.sleep(1);
      return new LongResponse();
    }

    @Override public boolean isPersistent() {
      return false;
    }
  }

  private class ShortPersistentAction extends Action<ShortResponse> {
    @Override public ShortResponse execute() throws Throwable {
      return new ShortResponse();
    }

    @Override public boolean isPersistent() {
      return true;
    }
  }

  private class LongAction extends Action<LongResponse> {
    @Override public LongResponse execute() throws Throwable {
      Thread.sleep(1);
      return new LongResponse();
    }

    @Override public boolean isPersistent() {
      return true;
    }
  }

  private class LongResponse {
  }

  private class ShortAction extends Action<ShortResponse> {
    @Override public ShortResponse execute() throws Throwable {
      return new ShortResponse();
    }

    @Override public boolean isPersistent() {
      return true;
    }
  }

  private class ShortResponse {
  }

  private class TestAction extends Action<TestResponse> {
    @Override public TestResponse execute() throws Throwable {
      return new TestResponse();
    }

    @Override public boolean isPersistent() {
      return true;
    }
  }

  private class TestResponse {
  }

  private class TestAction2 extends Action<TestResponse2> {
    @Override public TestResponse2 execute() throws Throwable {
      return new TestResponse2();
    }

    @Override public boolean isPersistent() {
      return true;
    }
  }

  private class TestResponse2 {
  }
}
