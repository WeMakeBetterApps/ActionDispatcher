package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;

public class ErrorAction extends Action<Boolean> {
  private final int mValue;

  public ErrorAction() {
    this(0);
  }

  public ErrorAction(int value) {
    mValue = value;
  }

  @Override public Boolean execute() throws Throwable {
    throw new TestException(mValue);
  }
}
