package com.wmba.actiondispatcher.android;

import com.wmba.actiondispatcher.Action;
import com.wmba.actiondispatcher.component.ActionKeySelector;

public class AndroidActionKeySelector implements ActionKeySelector {

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
