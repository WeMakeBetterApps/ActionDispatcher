package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;

import rx.Scheduler;

/**
 * Can be supplied to ActionDispatcher at creation to add a default Scheduler to always observe on.
 * Useful if you want to observe all your observables on a particular thread.
 */
public interface ObserveOnProvider {
  Scheduler provideScheduler(Action[] actions);
}
