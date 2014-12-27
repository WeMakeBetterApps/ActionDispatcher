package com.wmba.actiondispatcher;

import rx.Subscriber;

public interface SubscriptionContext {

  Action[] getActions();

  Subscriber getSubscriber();

  ActionDispatcher getActionDispatcher();

  int getCurrentActionIndex();

  void setCurrentActionIndex(int currentActionIndex);

  boolean isUnsubscribed();

}