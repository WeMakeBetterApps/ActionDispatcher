package com.wmba.actiondispatcher;

public class PersistedActionHolder {

  private final long mActionId;
  private final byte[] mSerializedAction;
  private SingularAction mPersistedAction;

  public PersistedActionHolder(long actionId, byte[] serializedAction) {
    this.mActionId = actionId;
    this.mSerializedAction = serializedAction;
  }

  public long getActionId() {
    return mActionId;
  }

  public void setPersistedAction(SingularAction persistedAction) {
    this.mPersistedAction = persistedAction;
  }

  public SingularAction getPersistedAction() {
    return mPersistedAction;
  }

  public byte[] getSerializedAction() {
    return mSerializedAction;
  }
}
