package com.wmba.actiondispatcher;

public interface ActionPreparer {
  /**
   * Prepare the action to be run. This would be a great place to inject dependencies into the action.
   * @param action The action to prepare.
   */
  public void prepare(Action<?> action);
}
