package com.wmba.actiondispatcher.component;

/**
 * Class to avoid passing an actual runnable. We don't want to encourage user thread management.
 */
public interface ActionRunnable {
  /**
   * Execute the actions.
   */
  void execute();
}
