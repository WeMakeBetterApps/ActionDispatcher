//package com.wmba.actiondispatcher.old;
//
//import com.wmba.actiondispatcher.Action;
//import com.wmba.actiondispatcher.component.ActionKeySelector;
//import com.wmba.actiondispatcher.component.ActionPauser;
//import com.wmba.actiondispatcher.component.ActionRunnable;
//import com.wmba.actiondispatcher.component.ActionRunner;
//import com.wmba.actiondispatcher.component.ObserveOnProvider;
//
//import org.junit.Test;
//
//import rx.Scheduler;
//import rx.schedulers.Schedulers;
//
//import static org.junit.Assert.assertFalse;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertTrue;
//
//
//
//public class PersistentActionDispatcherTest {
//
//  private JavaActionDispatcher buildDispatcher() {
//    return buildDispatcher(null);
//  }
//
//  private JavaActionDispatcher buildDispatcher(final TestPersister persister) {
//    return new JavaActionDispatcher.Builder()
//        .actionRunner(new ActionRunner() {
//          @Override
//          public void execute(ActionRunnable actionRunnable, Action[] actions) {
//            assertNotNull(actions);
//
//            for (Action action : actions) {
//              assertNotNull(action);
//              if (persister != null)
//                assertTrue(persister.isPersisted(action));
//            }
//
//            actionRunnable.execute();
//          }
//        })
//        .observeOnProvider(new ObserveOnProvider() {
//          @Override public Scheduler provideScheduler(Action[] actions) {
//            assertNotNull(actions);
//            if (persister != null) {
//              for (Action action : actions) {
//                assertFalse(persister.isPersisted(action));
//              }
//            }
//            return Schedulers.immediate();
//          }
//        })
//        .keySelector(new ActionKeySelector() {
//          @Override public String getKey(Action... actions) {
//            assertNotNull(actions);
//            if (persister != null) {
//              for (Action action : actions) {
//                assertFalse(persister.isPersisted(action));
//              }
//            }
//
//            return ActionKeySelector.DEFAULT_KEY;
//          }
//        })
//        .pauser(new ActionPauser() {
//          @Override public boolean shouldPauseForAction(Action action) {
//            if (persister != null)
//              assertTrue(persister.isPersisted(action));
//            return false;
//          }
//        })
//        .persister(persister)
//        .build();
//  }
//
//  @Test
//  public void persistentFailTest() {
//    SingularAction bogusPersistentAction = new SingularAction<TestResponse>(true) {
//      @Override public TestResponse execute() throws Throwable {
//        return new TestResponse();
//      }
//    };
//    SingularAction bogusSingularAction = new SingularAction<TestResponse>(false) {
//      @Override public TestResponse execute() throws Throwable {
//        return new TestResponse();
//      }
//    };
//
//    JavaActionDispatcher dispatcher = buildDispatcher();
//
//    // Persistent
//    boolean isFailed = false;
//    try {
//      dispatcher.toObservable((Action) bogusPersistentAction);
//    } catch (Throwable t) {
//      isFailed = true;
//    } finally {
//      assertTrue(isFailed);
//    }
//
//    isFailed = false;
//    try {
//      dispatcher.toObservable(bogusPersistentAction);
//    } catch (Throwable t) {
//      isFailed = true;
//    } finally {
//      assertTrue(isFailed);
//    }
//
//    isFailed = false;
//    try {
//      dispatcher.toObservable("test1", (Action) bogusPersistentAction);
//    } catch (Throwable t) {
//      isFailed = true;
//    } finally {
//      assertTrue(isFailed);
//    }
//
//    isFailed = false;
//    try {
//      dispatcher.toObservable("test2", bogusPersistentAction);
//    } catch (Throwable t) {
//      isFailed = true;
//    } finally {
//      assertTrue(isFailed);
//    }
//
//    // Singular
//    isFailed = false;
//    try {
//      dispatcher.toObservable((Action) bogusSingularAction);
//    } catch (Throwable t) {
//      isFailed = true;
//    } finally {
//      assertFalse(isFailed);
//    }
//
//    isFailed = false;
//    try {
//      dispatcher.toObservable(bogusSingularAction);
//    } catch (Throwable t) {
//      isFailed = true;
//    } finally {
//      assertFalse(isFailed);
//    }
//
//    isFailed = false;
//    try {
//      dispatcher.toObservable("test1", (Action) bogusSingularAction);
//    } catch (Throwable t) {
//      isFailed = true;
//    } finally {
//      assertFalse(isFailed);
//    }
//
//    isFailed = false;
//    try {
//      dispatcher.toObservable("test2", bogusSingularAction);
//    } catch (Throwable t) {
//      isFailed = true;
//    } finally {
//      assertFalse(isFailed);
//    }
//  }
//
//  @Test
//  public void persistentInstantTest() {
//    TestPersister persister = new TestPersister();
//    persistentTest(persister);
//  }
//
//  @Test
//  public void persistentDelayedTest() {
//    TestPersister persister = new DelayPersister();
//    persistentTest(persister);
//  }
//
//  private void persistentTest(TestPersister persister) {
//    JavaActionDispatcher dispatcher = buildDispatcher(persister);
//
//    LongPersistentAction action = new LongPersistentAction();
//
//    LongResponse response = dispatcher.toObservable(action)
//        .toBlocking()
//        .first();
//
//    assertNotNull(response);
//
//    try {
//      Thread.sleep(200L);
//    } catch (InterruptedException ignored) {
//    }
//
//    assertFalse(persister.isPersisted(action));
//  }
//
//  /*
//  Inner Classes
//   */
//  private class DelayPersister extends TestPersister {
//
//    @Override public long persist(SingularAction action) {
//      sleep(500);
//      return super.persist(action);
//    }
//
//    @Override public void update(long id, SingularAction action) {
//      sleep(100);
//      super.update(id, action);
//    }
//
//    @Override public void delete(long id) {
//      sleep(100);
//      super.delete(id);
//    }
//
//    private void sleep(long time) {
//      try {
//        Thread.sleep(time);
//      } catch (InterruptedException e) {
//        throw new RuntimeException(e);
//      }
//    }
//  }
//
//  private class LongPersistentAction extends SingularAction<LongResponse> {
//    public LongPersistentAction() {
//      super(true);
//    }
//
//    @Override public LongResponse execute() throws Throwable {
//      Thread.sleep(1);
//      return new LongResponse();
//    }
//  }
//
//  private class ShortPersistentAction extends SingularAction<ShortResponse> {
//    public ShortPersistentAction() {
//      super(true);
//    }
//
//    @Override public ShortResponse execute() throws Throwable {
//      return new ShortResponse();
//    }
//  }
//
//  private class LongAction extends ComposableAction<LongResponse> {
//    @Override public LongResponse execute() throws Throwable {
//      Thread.sleep(1);
//      return new LongResponse();
//    }
//  }
//
//  private class LongResponse {
//  }
//
//  private class ShortAction extends ComposableAction<ShortResponse> {
//    @Override public ShortResponse execute() throws Throwable {
//      return new ShortResponse();
//    }
//  }
//
//  private class ShortResponse {
//  }
//
//  private class TestAction extends ComposableAction<TestResponse> {
//    @Override public TestResponse execute() throws Throwable {
//      return new TestResponse();
//    }
//  }
//
//  private class TestResponse {
//  }
//
//  private class TestAction2 extends ComposableAction<TestResponse2> {
//    @Override public TestResponse2 execute() throws Throwable {
//      return new TestResponse2();
//    }
//  }
//
//  private class TestResponse2 {
//  }
//}
