package com.wmba.actiondispatcher.android;

import com.wmba.actiondispatcher.SingularAction;

public abstract class NetworkAction<T> extends SingularAction<T> {

  public NetworkAction(boolean isPersistent) {
    super(isPersistent);
  }

}
