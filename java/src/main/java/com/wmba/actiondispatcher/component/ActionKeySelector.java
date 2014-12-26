package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;

public interface ActionKeySelector {
  public static final String DEFAULT_KEY = "default";
  public static final String ASYNC_KEY = "async";

  /**
   * @param actions the actions that are being run
   * @return the groupId that the actions should be run in.
   */
  String getKey(Action... actions);
}
