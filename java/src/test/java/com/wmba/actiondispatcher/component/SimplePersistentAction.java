package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;

public class SimplePersistentAction extends Action<Boolean> {
  @Override public Boolean execute() throws Throwable {
    return true;
  }

  @Override public boolean isPersistent() {
    return true;
  }
}
