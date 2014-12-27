package com.wmba.actiondispatcher;

import java.util.concurrent.Semaphore;

public class PersistentSubscriptionContext extends SubscriptionContext {

  private final Semaphore mPersistSemaphore = new Semaphore(1);
  private Long mPersistedId;
  private boolean mIsActionAlreadyPersisted = false;

}
