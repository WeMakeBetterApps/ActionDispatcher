package com.wmba.actiondispatcher.component;

import java.util.concurrent.CountDownLatch;

public class SimplePersistentAction extends SimpleAction {
  private final CountDownLatch mCountDownLatch;

  public SimplePersistentAction() {
    this(null);
  }

  public SimplePersistentAction(CountDownLatch countDownLatch) {
    mCountDownLatch = countDownLatch;
  }

  @Override public Boolean execute() throws Throwable {
    if (mCountDownLatch != null) mCountDownLatch.countDown();
    return super.execute();
  }

  @Override public boolean isPersistent() {
    return true;
  }
}
