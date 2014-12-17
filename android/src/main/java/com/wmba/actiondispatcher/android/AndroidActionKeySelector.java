package com.wmba.actiondispatcher.android;

import com.wmba.actiondispatcher.ActionDispatcher;
import com.wmba.actiondispatcher.Action;

public class AndroidActionKeySelector implements ActionDispatcher.ActionKeySelector {

  public static final String DEFAULT_NETWORK_KEY = "networkDefault";

  @Override public String getKey(Action... actions) {
    String key;

    if (actions[0] instanceof NetworkAction)
      key = DEFAULT_NETWORK_KEY;
    else
      key = DEFAULT_KEY;

    return key;
  }

}
