package com.wmba.actiondispatcher;

public class KeySelector {
  public static final String DEFAULT_KEY = "default";
  public static final String ASYNC_KEY = "async";

  /**
   * @param action the action that is being run
   * @return the key that the action should be run on
   */
  public String getKey(Action<?> action) {
    return action.getKey();
  }
}
