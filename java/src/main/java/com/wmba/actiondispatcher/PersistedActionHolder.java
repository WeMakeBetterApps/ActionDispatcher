package com.wmba.actiondispatcher;

public class PersistedActionHolder {
  private final long mActionId;
  private final Action<?> mAction;

  public PersistedActionHolder(long actionId, Action<?> action) {
    mActionId = actionId;
    mAction = action;
  }

  public long getActionId() {
    return mActionId;
  }

  public Action<?> getAction() {
    return mAction;
  }
}

