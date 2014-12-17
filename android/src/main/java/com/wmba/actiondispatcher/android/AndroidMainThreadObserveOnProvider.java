package com.wmba.actiondispatcher.android;

import com.wmba.actiondispatcher.ActionDispatcher;
import com.wmba.actiondispatcher.Action;

import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;

public class AndroidMainThreadObserveOnProvider implements ActionDispatcher.ObserveOnProvider {

  @Override public Scheduler provideScheduler(Action[] actions) {
    return AndroidSchedulers.mainThread();
  }

}
