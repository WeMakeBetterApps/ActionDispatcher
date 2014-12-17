package com.wmba.actiondispatcher;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import rx.Scheduler;
import rx.Subscriber;
import rx.schedulers.Schedulers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RetryActionDispatcherTest {

  private ActionDispatcher buildDispatcher(final TestPersister persister) {
    return new ActionDispatcher.Builder()
        .actionRunner(new ActionDispatcher.ActionRunner() {
          @Override
          public void execute(ActionDispatcher.ActionRunnable actionRunnable, Action[] actions) {
            assertNotNull(actions);

            for (Action action : actions) {
              assertNotNull(action);
              if (persister != null
                  && action instanceof SingularAction
                  && ((SingularAction) action).isPersistent())
                assertTrue(persister.isPersisted(action));
            }

            actionRunnable.execute();
          }
        })
        .observeOnProvider(new ActionDispatcher.ObserveOnProvider() {
          @Override public Scheduler provideScheduler(Action[] actions) {
            assertNotNull(actions);
            if (persister != null) {
              for (Action action : actions) {
                if (action instanceof SingularAction
                    && ((SingularAction) action).isPersistent()) {
                  assertFalse(persister.isPersisted(action));
                }
              }
            }
            return Schedulers.immediate();
          }
        })
        .keySelector(new ActionDispatcher.ActionKeySelector() {
          @Override public String getKey(Action... actions) {
            assertNotNull(actions);
            if (persister != null) {
              for (Action action : actions) {
                if (action instanceof SingularAction
                    && ((SingularAction) action).isPersistent()) {
                  assertFalse(persister.isPersisted(action));
                }
              }
            }

            return ActionDispatcher.ActionKeySelector.DEFAULT_KEY;
          }
        })
        .pauser(new ActionDispatcher.ActionPauser() {
          @Override public boolean shouldPauseForAction(Action action) {
            if (persister != null
                && action instanceof SingularAction
                && ((SingularAction) action).isPersistent())
              assertTrue(persister.isPersisted(action));
            return false;
          }
        })
        .persister(persister)
        .build();
  }

  @Test
  public void retryTest() {
    final TestPersister persister = new TestPersister();
    ActionDispatcher dispatcher = buildDispatcher(persister);

    final CountDownLatch latch = new CountDownLatch(1);

    final SingularRetryAction singularAction = new SingularRetryAction();
    dispatcher.toObservable(singularAction)
        .subscribe(new Subscriber<RetryResponse>() {
          @Override public void onCompleted() {
            assertTrue(false);
          }

          @Override public void onError(Throwable e) {
            int runCount = singularAction.mRunCount.get();
            int retryCount = singularAction.getRetryCount();
            assertEquals(runCount, retryCount + 1);
            latch.countDown();
          }

          @Override public void onNext(RetryResponse retryResponse) {
            assertTrue(false);
          }
        });

    try {
      latch.await();
    } catch (InterruptedException ignored) {
    }

    final CountDownLatch latch2 = new CountDownLatch(1);

    final PersistentRetryAction persistentAction = new PersistentRetryAction();

    assertFalse(persister.isPersisted(persistentAction));

    dispatcher.toObservable(persistentAction)
        .subscribe(new Subscriber<RetryResponse>() {
          @Override public void onCompleted() {
            assertTrue(false);
          }

          @Override public void onError(Throwable e) {
            assertTrue(persister.isPersisted(persistentAction));

            int runCount = persistentAction.mRunCount.get();
            int retryCount = persistentAction.getRetryCount();
            assertEquals(runCount, retryCount + 1);
            latch2.countDown();
          }

          @Override public void onNext(RetryResponse retryResponse) {
            assertTrue(false);
          }
        });

    try {
      latch2.await();
    } catch (InterruptedException ignored) {
    }

    try {
      Thread.sleep(5L);
    } catch (InterruptedException ignored) {
    }

    assertFalse(persister.isPersisted(persistentAction));
  }

  /*
  Inner Classes
   */
  private abstract class RetryAction extends SingularAction<RetryResponse> {

    public AtomicInteger mRunCount = new AtomicInteger(0);

    public RetryAction(boolean isPersistent) {
      super(isPersistent);
    }

    @Override public RetryResponse execute() throws Throwable {
      int runCount = mRunCount.incrementAndGet();
      int retryCount = getRetryCount();
      assertEquals(runCount, retryCount + 1);

      throw new RuntimeException("test exception");
    }

    @Override public int getRetryLimit() {
      return super.getRetryLimit();
    }
  }

  private class PersistentRetryAction extends RetryAction {
    public PersistentRetryAction() {
      super(true);
    }
  }

  private class SingularRetryAction extends RetryAction {
    public SingularRetryAction() {
      super(false);
    }
  }

  private class RetryResponse {
  }

}
