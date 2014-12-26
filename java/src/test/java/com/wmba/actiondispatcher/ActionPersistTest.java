package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rx.functions.Action1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionPersistTest {

  @Test
  public void nonCompletedPersistRunTest() {
    final CountDownLatch latch = new CountDownLatch(1);
    TestPersister persister = new TestPersister() {
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

    String sharedKey = "test";

    // Persist the action before building the dispatcher
    PersistentTestAction action = new PersistentTestAction(latch);
    action.setKey(sharedKey);
    persister.persist(action);

    assertTrue(persister.isPersisted(action));

    JavaActionDispatcher actionDispatcher = buildDispatcher(persister);
    actionDispatcher.toObservable(sharedKey, new TestAction())
        .subscribe(new Action1<TestResponse>() {
          @Override public void call(TestResponse testResponse) {
            // Check that the already Persisted action runs before you.
            assertEquals(latch.getCount(), 0);
          }
        });

    try {
      latch.await(100L, TimeUnit.MILLISECONDS);
      Thread.sleep(5L);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    assertFalse(persister.isPersisted(action));
  }

  private JavaActionDispatcher buildDispatcher(TestPersister persister) {
    return new JavaActionDispatcher.Builder()
        .persister(persister)
        .build();
  }

  private static class PersistentTestAction extends SingularAction {

    private static CountDownLatch mLatch;

    public PersistentTestAction(CountDownLatch latch) {
      super(true);
      PersistentTestAction.mLatch = latch;
    }

    @Override public Object execute() throws Throwable {
      PersistentTestAction.mLatch.countDown();
      return new TestResponse();
    }
  }

  private static class TestAction extends ComposableAction<TestResponse> {

    @Override public TestResponse execute() throws Throwable {
      return new TestResponse();
    }
  }

  private static class TestResponse {}


}
