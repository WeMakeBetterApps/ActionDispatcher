package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;

/**
 * Can be supplied to ActionDispatcher at creation to add custom behavior when running actions.
 * Useful for running the actions in a transaction.
 */
public interface ActionRunner {

  /**
   * If running the actionRunnable in a transaction, if an exception happens roll the transaction
   * back, but any caught errors need to be re-thrown.
   *
   * @param actionRunnable Call execute() on the calling thread.
   * @param actions The actions that will be ran.
   */
  void execute(ActionRunnable actionRunnable, Action[] actions);
}
