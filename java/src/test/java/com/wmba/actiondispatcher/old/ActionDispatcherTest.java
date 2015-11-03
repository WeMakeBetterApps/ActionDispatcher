//package com.wmba.actiondispatcher.old;
//
//import com.wmba.actiondispatcher.Action;
//import com.wmba.actiondispatcher.ActionDispatcher;
//import com.wmba.actiondispatcher.KeySelector;
//
//import org.junit.Before;
//import org.junit.Test;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Set;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import rx.functions.Action1;
//
//import static org.junit.Assert.*;
//
//
//public class ActionDispatcherTest {
//
//  private ActionDispatcher mDispatcher;
//
//  @Before public void beforeTest() {
//    mDispatcher = new ActionDispatcher.Builder().build();
//  }
//
//  @Test
//  public void asyncSingleTest() {
//    Object response = mDispatcher.toSingle(new TestAction())
//        .toBlocking()
//        .first();
//    assertNotNull(response);
//  }
//
//  private void asyncSingleTest(JavaActionDispatcher actionDispatcher) {
//    Object response = actionDispatcher.toObservable(new TestAction())
//        .toBlocking()
//        .first();
//    assertNotNull(response);
//  }
//
//  @Test
//  public void asyncMultiActionTest() {
//    asyncMultiTest(mNormalActionDispatcher);
//    asyncMultiTest(mRunnerActionDispatcher);
//  }
//
//  private void asyncMultiTest(JavaActionDispatcher actionDispatcher) {
//    final ComposableAction[] actions = new ComposableAction[] {
//        new TestAction(),
//        new TestAction2(),
//        new TestAction(),
//        new TestAction2(),
//        new TestAction(),
//        new TestAction2()
//    };
//
//    List<Object> responses = actionDispatcher.toObservable(actions)
//        .toList()
//        .toBlocking()
//        .first();
//
//    assertEquals(actions.length, responses.size());
//
//    for (int i = 0; i < actions.length; i++) {
//      Object response = (Object) responses.get(i);
//      assertNotNull(response);
//
//      if (actions[i] instanceof TestAction) {
//        assertEquals(response.getClass(), TestResponse.class);
//      } else if (actions[i] instanceof TestAction2) {
//        assertEquals(response.getClass(), TestResponse2.class);
//      }
//    }
//  }
//
//  @Test
//  public void asyncMultiThreadTest() {
//    asyncMultiThreadTest(mNormalActionDispatcher);
//    asyncMultiThreadTest(mRunnerActionDispatcher);
//  }
//
//  private void asyncMultiThreadTest(JavaActionDispatcher actionDispatcher) {
//    final long startThreadId = Thread.currentThread().getId();
//
//    final ComposableAction[] actions1 = new ComposableAction[] {
//        new TestAction(),
//        new TestAction2(),
//        new TestAction(),
//        new TestAction2(),
//        new TestAction(),
//        new TestAction2()
//    };
//    final ComposableAction[] actions2 = new ComposableAction[] {
//        new TestAction(),
//        new TestAction2(),
//        new TestAction(),
//        new TestAction2(),
//        new TestAction(),
//        new TestAction2()
//    };
//
//    final long[] observeThreadId1 = new long[1];
//    final long[] observeThreadId2 = new long[1];
//
//    final Object[] responses1Holder = new Object[1];
//    final Object[] responses2Holder = new Object[1];
//
//    final CountDownLatch latch = new CountDownLatch(2);
//
//    actionDispatcher.toObservable("test1", actions1)
//        .toList()
//        .subscribe(new Action1<List<Object>>() {
//          @Override public void call(List<Object> responses) {
//            //Returns on different thread
//            observeThreadId1[0] = Thread.currentThread().getId();
//            assertNotEquals(startThreadId, observeThreadId1[0]);
//            responses1Holder[0] = responses;
//            latch.countDown();
//          }
//        });
//
//    actionDispatcher.toObservable("test2", actions1)
//        .toList()
//        .subscribe(new Action1<List<Object>>() {
//          @Override public void call(List<Object> responses) {
//            //Returns on different thread
//            observeThreadId2[0] = Thread.currentThread().getId();
//            assertNotEquals(startThreadId, observeThreadId2[0]);
//            responses2Holder[0] = responses;
//            latch.countDown();
//          }
//        });
//
//    try {
//      latch.await();
//    } catch (InterruptedException e) {
//      throw new RuntimeException(e);
//    }
//
//    Set<String> keys = actionDispatcher.getActiveKeys();
//    assertTrue(keys.contains("test1"));
//    assertTrue(keys.contains("test2"));
//
//    // Tasks have completed
//    assertNotEquals(observeThreadId1[0], observeThreadId2[0]);
//
//    //noinspection unchecked
//    List<Object> responses1 = (List<Object>) responses1Holder[0];
//    //noinspection unchecked
//    List<Object> responses2 = (List<Object>) responses2Holder[0];
//
//    assertNotNull(responses1);
//    assertNotNull(responses2);
//
//    assertEquals(actions1.length, responses1.size());
//    for (int i = 0; i < actions1.length; i++) {
//      Object response = responses1.get(i);
//      assertNotNull(response);
//
//      if (actions1[i] instanceof TestAction) {
//        assertEquals(response.getClass(), TestResponse.class);
//      } else if (actions1[i] instanceof TestAction2) {
//        assertEquals(response.getClass(), TestResponse2.class);
//      }
//    }
//
//    assertEquals(actions2.length, responses2.size());
//    for (int i = 0; i < actions2.length; i++) {
//      Object response = responses2.get(i);
//      assertNotNull(response);
//
//      if (actions2[i] instanceof TestAction) {
//        assertEquals(response.getClass(), TestResponse.class);
//      } else if (actions2[i] instanceof TestAction2) {
//        assertEquals(response.getClass(), TestResponse2.class);
//      }
//    }
//  }
//
//  @Test
//  public void asyncOrderTest() {
//    asyncOrderTest(mNormalActionDispatcher);
//    asyncOrderTest(mRunnerActionDispatcher);
//  }
//
//  private void asyncOrderTest(JavaActionDispatcher dispatcher) {
//    final String threadKey = "asyncMultiOrderTest";
//
//    final AtomicInteger completedCount = new AtomicInteger(0);
//
//    int numActions = 30;
//    for (int i = 0; i < numActions; i++) {
//      final int expectedIndex = i;
//
//      final Action action;
//      if (expectedIndex % 2 == 0)
//        action = new LongAction();
//      else
//        action = new ShortAction();
//
//      //noinspection unchecked
//      dispatcher.toObservable(threadKey, action)
//          .subscribe(new Action1<Object>() {
//            @Override public void call(Object response) {
//              int index = completedCount.getAndIncrement();
//              assertEquals(index, expectedIndex);
//              assertNotNull(response);
//            }
//          });
//    }
//
//    // Wehn this completes all actions should be finished
//    dispatcher.toObservable(threadKey, new ShortAction())
//        .toBlocking()
//        .first();
//
//    assertEquals(completedCount.get(), numActions);
//  }
//
//  @Test
//  public void asyncMultiKeyOrderTest() {
//    asyncMultiKeyOrderTest(mNormalActionDispatcher);
//    asyncMultiKeyOrderTest(mRunnerActionDispatcher);
//  }
//
//  private void asyncMultiKeyOrderTest(JavaActionDispatcher dispatcher) {
//    final String longThreadKey = "asyncMultiKeyOrderTest1";
//    final String shortThreadKey = "asyncMultiKeyOrderTest2";
//
//    final long startThreadId = Thread.currentThread().getId();
//
//    final ComposableAction[] longActions = new ComposableAction[] {
//        new LongAction(),
//        new LongAction(),
//        new LongAction(),
//        new LongAction(),
//        new LongAction(),
//        new LongAction(),
//        new LongAction(),
//        new LongAction(),
//        new LongAction()
//    };
//    final ComposableAction[] shortActions = new ComposableAction[] {
//        new ShortAction(),
//        new ShortAction(),
//        new ShortAction(),
//        new ShortAction(),
//        new ShortAction(),
//        new ShortAction(),
//        new ShortAction(),
//        new ShortAction(),
//        new ShortAction()
//    };
//
//    final long[] longEndTime = new long[1];
//    final long[] shortEndTime = new long[1];
//    final long[] longThreadId = new long[1];
//    final long[] shortThreadId = new long[1];
//    final List<Object> longResponses = new ArrayList<Object>();
//    final List<Object> shortResponses = new ArrayList<Object>();
//
//    final CountDownLatch latch = new CountDownLatch(2);
//
//    dispatcher.toObservable(longThreadKey, longActions)
//        .toList()
//        .subscribe(new Action1<List<Object>>() {
//          @Override public void call(List<Object> responses) {
//            longEndTime[0] = System.currentTimeMillis();
//            longThreadId[0] = Thread.currentThread().getId();
//            longResponses.addAll(responses);
//            latch.countDown();
//          }
//        });
//
//    dispatcher.toObservable(shortThreadKey, shortActions)
//        .toList()
//        .subscribe(new Action1<List<Object>>() {
//          @Override public void call(List<Object> responses) {
//            shortEndTime[0] = System.currentTimeMillis();
//            shortThreadId[0] = Thread.currentThread().getId();
//            shortResponses.addAll(responses);
//            latch.countDown();
//          }
//        });
//
//    try {
//      latch.await();
//    } catch (InterruptedException ignored) {
//    }
//
//    assertFalse(longResponses.isEmpty());
//    assertFalse(shortResponses.isEmpty());
//
//    assertNotEquals(longThreadId[0], shortThreadId[0]);
//    assertNotEquals(longThreadId[0], startThreadId);
//    assertNotEquals(shortThreadId[0], startThreadId);
//
//    assertTrue(shortEndTime[0] < longEndTime[0]);
//  }
//
//  @Test
//  public void blockingActionTest() {
//    blockingActionTest(mNormalActionDispatcher);
//    blockingActionTest(mRunnerActionDispatcher);
//  }
//
//  private void blockingActionTest(JavaActionDispatcher actionDispatcher) {
//    final long startThreadId = Thread.currentThread().getId();
//
//    final int[] count = {0};
//
//    actionDispatcher.subscribeBlocking(null, new SingularAction(false) {
//      @Override public Object execute() throws Throwable {
//        assertEquals(Thread.currentThread().getId(), startThreadId);
//        count[0]++;
//        return null;
//      }
//    });
//
//    assertEquals(count[0], 1);
//
//    actionDispatcher.subscribeBlocking(null,
//        new ComposableAction() {
//          @Override public Object execute() throws Throwable {
//            assertEquals(Thread.currentThread().getId(), startThreadId);
//            count[0]++;
//            return null;
//          }
//        },
//        new ComposableAction() {
//          @Override public Object execute() throws Throwable {
//            assertEquals(Thread.currentThread().getId(), startThreadId);
//            count[0]++;
//            return null;
//          }
//        });
//
//    assertEquals(count[0], 3);
//  }
//
//  @Test
//  public void actionSubscriptionTest() {
//    actionSubscriptionTest(mNormalActionDispatcher);
//    actionSubscriptionTest(mRunnerActionDispatcher);
//  }
//
//  private void actionSubscriptionTest(JavaActionDispatcher actionDispatcher) {
//    SubscriptionTestAction action = new SubscriptionTestAction();
//
//    assertTrue(action.isUnsubscribed());
//
//    Boolean result = actionDispatcher.toObservable(action)
//        .toBlocking()
//        .last();
//    assertEquals(result, Boolean.TRUE);
//
//    assertTrue(action.isUnsubscribed());
//
//    SubscriptionTestAction action2 = new SubscriptionTestAction();
//    assertTrue(action2.isUnsubscribed());
//    Boolean result2 = actionDispatcher.subscribeBlocking(null, action2);
//    assertEquals(result2, Boolean.TRUE);
//    assertTrue(action2.isUnsubscribed());
//  }
//
//  @Test
//  public void actionInsideActionTest() {
//    actionInsideActionTest(mNormalActionDispatcher);
//    actionInsideActionTest(mRunnerActionDispatcher);
//  }
//
//  private void actionInsideActionTest(JavaActionDispatcher dispatcher) {
//    final int[] count = {0};
//
//    final Action<Integer> innerAction = new ComposableAction<Integer>() {
//      @Override public Integer execute() throws Throwable {
//        assertEquals(isUnsubscribed(), false);
//        count[0]++;
//        return count[0];
//      }
//    };
//
//    Action<Integer> outerAction = new ComposableAction<Integer>() {
//      @Override public Integer execute() throws Throwable {
//        assertEquals(isUnsubscribed(), false);
//        count[0]++;
//        return subscribeBlocking(innerAction);
//      }
//    };
//
//    assertEquals(innerAction.isUnsubscribed(), true);
//    assertEquals(outerAction.isUnsubscribed(), true);
//
//    Integer number = dispatcher.toObservable(outerAction)
//        .toBlocking()
//        .last();
//
//    assertEquals(innerAction.isUnsubscribed(), true);
//    assertEquals(outerAction.isUnsubscribed(), true);
//    assertEquals(number, Integer.valueOf(2));
//  }
//
//  /*
//  Inner Classes
//   */
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
//
//  private class SubscriptionTestAction extends ComposableAction<Boolean> {
//    @Override public Boolean execute() throws Throwable {
//      assertFalse(isUnsubscribed());
//      return Boolean.TRUE;
//    }
//  }
//
//}
