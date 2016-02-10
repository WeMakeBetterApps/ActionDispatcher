package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.component.SimpleAction;

import rx.observers.TestSubscriber;

public class DispatcherUtil {
  private DispatcherUtil() {}

  public static void waitForPersistentActionsToLoad(ActionDispatcher dispatcher) {
    subscribeActionBlocking(dispatcher);
  }

  public static void subscribeActionBlocking(ActionDispatcher dispatcher) {
    TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
    dispatcher.toSingle(new SimpleAction())
        .subscribe(ts);
    ts.awaitTerminalEvent(); // Wait for Actions to load.
  }
}
