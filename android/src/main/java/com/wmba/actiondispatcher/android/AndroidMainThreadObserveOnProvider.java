package com.wmba.actiondispatcher.android;

import com.wmba.actiondispatcher.Action;
import com.wmba.actiondispatcher.component.ObserveOnProvider;

import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;

public class AndroidMainThreadObserveOnProvider implements ObserveOnProvider {

  @Override public Scheduler provideScheduler(Action[] actions) {
    return AndroidSchedulers.mainThread();
  }

}
