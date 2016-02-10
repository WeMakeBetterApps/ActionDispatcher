package com.wmba.actiondispatcher;

import rx.SingleSubscriber;

/* package */ class SubscriptionContext {
  private final ActionDispatcher mDispatcher;
  private final SingleSubscriber<?> mSubscriber;

  public SubscriptionContext(ActionDispatcher dispatcher, SingleSubscriber<?> subscriber) {
    mDispatcher = dispatcher;
    mSubscriber = subscriber;
  }

  public ActionDispatcher getDispatcher() {
    return mDispatcher;
  }

  public SingleSubscriber<?> getSubscriber() {
    return mSubscriber;
  }

  public boolean isUnsubscribed() {
    return mSubscriber.isUnsubscribed();
  }
}
