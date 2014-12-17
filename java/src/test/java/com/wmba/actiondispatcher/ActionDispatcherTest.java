package com.wmba.actiondispatcher;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import rx.functions.Action1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class ActionDispatcherTest {

  private ActionDispatcher mNormalActionDispatcher;
  private ActionDispatcher mRunnerActionDispatcher;

  @Before
  public void beforeTest() {
    mNormalActionDispatcher = new ActionDispatcher.Builder().build();
    mRunnerActionDispatcher = new ActionDispatcher.Builder()
        .actionRunner(new ActionDispatcher.ActionRunner() {
          @Override
          public void execute(ActionDispatcher.ActionRunnable actionRunnable, Action[] actions) {
            assertNotNull(actions);

            for (Action action : actions) {
              assertNotNull(action);
            }

            try {
              actionRunnable.execute();
            } catch (Throwable throwable) {
              throw new RuntimeException(throwable);
            }
          }
        })
        .keySelector(new ActionDispatcher.ActionKeySelector() {
          @Override public String getKey(Action... actions) {
            assertNotNull(actions);

            for (Action action : actions) {
              assertNotNull(action);
            }

            return ActionDispatcher.ActionKeySelector.DEFAULT_KEY;
          }
        })
        .pauser(new ActionDispatcher.ActionPauser() {
          @Override public boolean shouldPauseForAction(Action action) {
            assertNotNull(action);
            return false;
          }
        })
        .build();
  }

  @Test
  public void asyncSingleTest() {
    asyncSingleTest(mNormalActionDispatcher);
    asyncSingleTest(mRunnerActionDispatcher);
  }

  private void asyncSingleTest(ActionDispatcher actionDispatcher) {
    Object response = actionDispatcher.toObservable(new TestAction())
        .toBlocking()
        .first();
    assertNotNull(response);
  }

  @Test
  public void asyncMultiActionTest() {
    asyncMultiTest(mNormalActionDispatcher);
    asyncMultiTest(mRunnerActionDispatcher);
  }

  private void asyncMultiTest(ActionDispatcher actionDispatcher) {
    final ComposableAction[] actions = new ComposableAction[] {
        new TestAction(),
        new TestAction2(),
        new TestAction(),
        new TestAction2(),
        new TestAction(),
        new TestAction2()
    };

    List<Object> responses = actionDispatcher.toObservable(actions)
        .toList()
        .toBlocking()
        .first();

    assertEquals(actions.length, responses.size());

    for (int i = 0; i < actions.length; i++) {
      Object response = (Object) responses.get(i);
      assertNotNull(response);

      if (actions[i] instanceof TestAction) {
        assertEquals(response.getClass(), TestResponse.class);
      } else if (actions[i] instanceof TestAction2) {
        assertEquals(response.getClass(), TestResponse2.class);
      }
    }
  }

  @Test
  public void asyncMultiThreadTest() {
    asyncMultiThreadTest(mNormalActionDispatcher);
    asyncMultiThreadTest(mRunnerActionDispatcher);
  }

  private void asyncMultiThreadTest(ActionDispatcher actionDispatcher) {
    final long startThreadId = Thread.currentThread().getId();

    final ComposableAction[] actions1 = new ComposableAction[] {
        new TestAction(),
        new TestAction2(),
        new TestAction(),
        new TestAction2(),
        new TestAction(),
        new TestAction2()
    };
    final ComposableAction[] actions2 = new ComposableAction[] {
        new TestAction(),
        new TestAction2(),
        new TestAction(),
        new TestAction2(),
        new TestAction(),
        new TestAction2()
    };

    final long[] observeThreadId1 = new long[1];
    final long[] observeThreadId2 = new long[1];

    final Object[] responses1Holder = new Object[1];
    final Object[] responses2Holder = new Object[1];

    final CountDownLatch latch = new CountDownLatch(2);

    actionDispatcher.toObservable("test1", actions1)
        .toList()
        .subscribe(new Action1<List<Object>>() {
          @Override public void call(List<Object> responses) {
            //Returns on different thread
            observeThreadId1[0] = Thread.currentThread().getId();
            assertNotEquals(startThreadId, observeThreadId1[0]);
            responses1Holder[0] = responses;
            latch.countDown();
          }
        });

    actionDispatcher.toObservable("test2", actions1)
        .toList()
        .subscribe(new Action1<List<Object>>() {
          @Override public void call(List<Object> responses) {
            //Returns on different thread
            observeThreadId2[0] = Thread.currentThread().getId();
            assertNotEquals(startThreadId, observeThreadId2[0]);
            responses2Holder[0] = responses;
            latch.countDown();
          }
        });

    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    Set<String> keys = actionDispatcher.getActiveKeys();
    assertTrue(keys.contains("test1"));
    assertTrue(keys.contains("test2"));

    // Tasks have completed
    assertNotEquals(observeThreadId1[0], observeThreadId2[0]);

    //noinspection unchecked
    List<Object> responses1 = (List<Object>) responses1Holder[0];
    //noinspection unchecked
    List<Object> responses2 = (List<Object>) responses2Holder[0];

    assertNotNull(responses1);
    assertNotNull(responses2);

    assertEquals(actions1.length, responses1.size());
    for (int i = 0; i < actions1.length; i++) {
      Object response = responses1.get(i);
      assertNotNull(response);

      if (actions1[i] instanceof TestAction) {
        assertEquals(response.getClass(), TestResponse.class);
      } else if (actions1[i] instanceof TestAction2) {
        assertEquals(response.getClass(), TestResponse2.class);
      }
    }

    assertEquals(actions2.length, responses2.size());
    for (int i = 0; i < actions2.length; i++) {
      Object response = responses2.get(i);
      assertNotNull(response);

      if (actions2[i] instanceof TestAction) {
        assertEquals(response.getClass(), TestResponse.class);
      } else if (actions2[i] instanceof TestAction2) {
        assertEquals(response.getClass(), TestResponse2.class);
      }
    }
  }

  @Test
  public void asyncOrderTest() {
    asyncOrderTest(mNormalActionDispatcher);
    asyncOrderTest(mRunnerActionDispatcher);
  }

  private void asyncOrderTest(ActionDispatcher dispatcher) {
    final String threadKey = "asyncMultiOrderTest";

    final AtomicInteger completedCount = new AtomicInteger(0);

    int numActions = 30;
    for (int i = 0; i < numActions; i++) {
      final int expectedIndex = i;

      final Action action;
      if (expectedIndex % 2 == 0)
        action = new LongAction();
      else
        action = new ShortAction();

      //noinspection unchecked
      dispatcher.toObservable(threadKey, action)
          .subscribe(new Action1<Object>() {
            @Override public void call(Object response) {
              int index = completedCount.getAndIncrement();
              assertEquals(index, expectedIndex);
              assertNotNull(response);
            }
          });
    }

    // Wehn this completes all actions should be finished
    dispatcher.toObservable(threadKey, new ShortAction())
        .toBlocking()
        .first();

    assertEquals(completedCount.get(), numActions);
  }

  @Test
  public void asyncMultiKeyOrderTest() {
    asyncMultiKeyOrderTest(mNormalActionDispatcher);
    asyncMultiKeyOrderTest(mRunnerActionDispatcher);
  }

  private void asyncMultiKeyOrderTest(ActionDispatcher dispatcher) {
    final String longThreadKey = "asyncMultiKeyOrderTest1";
    final String shortThreadKey = "asyncMultiKeyOrderTest2";

    final long startThreadId = Thread.currentThread().getId();

    final ComposableAction[] longActions = new ComposableAction[] {
        new LongAction(),
        new LongAction(),
        new LongAction(),
        new LongAction(),
        new LongAction(),
        new LongAction(),
        new LongAction(),
        new LongAction(),
        new LongAction()
    };
    final ComposableAction[] shortActions = new ComposableAction[] {
        new ShortAction(),
        new ShortAction(),
        new ShortAction(),
        new ShortAction(),
        new ShortAction(),
        new ShortAction(),
        new ShortAction(),
        new ShortAction(),
        new ShortAction()
    };

    final long[] longEndTime = new long[1];
    final long[] shortEndTime = new long[1];
    final long[] longThreadId = new long[1];
    final long[] shortThreadId = new long[1];
    final List<Object> longResponses = new ArrayList<Object>();
    final List<Object> shortResponses = new ArrayList<Object>();

    final CountDownLatch latch = new CountDownLatch(2);

    dispatcher.toObservable(longThreadKey, longActions)
        .toList()
        .subscribe(new Action1<List<Object>>() {
          @Override public void call(List<Object> responses) {
            longEndTime[0] = System.currentTimeMillis();
            longThreadId[0] = Thread.currentThread().getId();
            longResponses.addAll(responses);
            latch.countDown();
          }
        });

    dispatcher.toObservable(shortThreadKey, shortActions)
        .toList()
        .subscribe(new Action1<List<Object>>() {
          @Override public void call(List<Object> responses) {
            shortEndTime[0] = System.currentTimeMillis();
            shortThreadId[0] = Thread.currentThread().getId();
            shortResponses.addAll(responses);
            latch.countDown();
          }
        });

    try {
      latch.await();
    } catch (InterruptedException ignored) {
    }

    assertFalse(longResponses.isEmpty());
    assertFalse(shortResponses.isEmpty());

    assertNotEquals(longThreadId[0], shortThreadId[0]);
    assertNotEquals(longThreadId[0], startThreadId);
    assertNotEquals(shortThreadId[0], startThreadId);

    assertTrue(shortEndTime[0] < longEndTime[0]);
  }

  /*
  Inner Classes
   */
  private class LongAction extends ComposableAction<LongResponse> {
    @Override public LongResponse execute() throws Throwable {
      Thread.sleep(1);
      return new LongResponse();
    }
  }

  private class LongResponse {
  }

  private class ShortAction extends ComposableAction<ShortResponse> {
    @Override public ShortResponse execute() throws Throwable {
      return new ShortResponse();
    }
  }

  private class ShortResponse {
  }

  private class TestAction extends ComposableAction<TestResponse> {
    @Override public TestResponse execute() throws Throwable {
      return new TestResponse();
    }
  }

  private class TestResponse {
  }

  private class TestAction2 extends ComposableAction<TestResponse2> {
    @Override public TestResponse2 execute() throws Throwable {
      return new TestResponse2();
    }
  }

  private class TestResponse2 {
  }
}
