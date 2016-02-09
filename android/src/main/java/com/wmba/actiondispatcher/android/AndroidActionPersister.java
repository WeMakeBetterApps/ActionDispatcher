package com.wmba.actiondispatcher.android;

import android.content.Context;

import com.wmba.actiondispatcher.Action;
import com.wmba.actiondispatcher.ActionPersister;
import com.wmba.actiondispatcher.persist.JavaActionSerializer;
import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import java.util.ArrayList;
import java.util.List;

public class AndroidActionPersister implements ActionPersister {
  private final ActionSqlOpenHelper mOpenHelper;
  private final JavaActionSerializer mSerializer = new JavaActionSerializer();

  public AndroidActionPersister(Context context) {
    mOpenHelper = new ActionSqlOpenHelper(context);
  }

  @Override public long persist(Action action) {
    byte[] serializedAction = mSerializer.serialize(action);
    return mOpenHelper.insert(serializedAction);
  }

  @Override public void update(long id, Action action) {
    byte[] serializedAction = mSerializer.serialize(action);
    mOpenHelper.update(id, serializedAction);
  }

  @Override public void delete(long id) {
    mOpenHelper.delete(id);
  }

  @Override public List<PersistedActionHolder> getPersistedActions() {
    List<ActionSqlOpenHelper.SerializedActionHolder> serializedActions = mOpenHelper.getAllActions();

    List<PersistedActionHolder> deserializedActions =
        new ArrayList<PersistedActionHolder>(serializedActions.size());
    for (ActionSqlOpenHelper.SerializedActionHolder holder : serializedActions) {
      Action action = mSerializer.deserialize(holder.getSerializedAction());
      deserializedActions.add(new PersistedActionHolder(holder.getId(), action));
    }

    return deserializedActions;
  }

  @Override public void deleteAll() {
    mOpenHelper.deleteAllActions();
  }
}
