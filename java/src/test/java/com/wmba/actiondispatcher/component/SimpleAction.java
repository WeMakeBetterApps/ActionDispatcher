package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;

public class SimpleAction extends Action<Boolean> {
  @Override public Boolean execute() throws Throwable {
    return true;
  }
}
