package com.wmba.actiondispatcher;

import rx.Subscriber;

public class SubscriptionContext {

  private String mKey;
  private Action[] mActions;
  private long mCurrentPauseTime;
  private Subscriber<? super Object> mSubscriber;

  public void set(String key, Action[] actions, Subscriber<? super Object> subscriber) {
    mKey = key;
    mActions = actions;
    mSubscriber = subscriber;
    mCurrentPauseTime = ActionDispatcher.PAUSE_EXPONENTIAL_BACKOFF_MIN_TIME;
  }

  public void clear() {
    mKey = null;
    mActions = null;
    mSubscriber = null;
  }

  public String getKey() {
    return mKey;
  }

  public Action[] getActions() {
    return mActions;
  }

  public long incrementCurrentPauseTime() {
    mCurrentPauseTime *= 2;  // Exponential backoff
    if (mCurrentPauseTime > ActionDispatcher.PAUSE_EXPONENTIAL_BACKOFF_MAX_TIME)
      mCurrentPauseTime = ActionDispatcher.PAUSE_EXPONENTIAL_BACKOFF_MAX_TIME;
    return mCurrentPauseTime;
  }

  public long getCurrentPauseTime() {
    return mCurrentPauseTime;
  }

  public Subscriber<? super Object> getSubscriber() {
    return mSubscriber;
  }

}
