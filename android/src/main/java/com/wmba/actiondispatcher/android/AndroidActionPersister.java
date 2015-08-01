//package com.wmba.actiondispatcher.android;
//
//import android.content.Context;
//
//import com.wmba.actiondispatcher.persist.ActionPersister;
//import com.wmba.actiondispatcher.persist.ActionSerializer;
//import com.wmba.actiondispatcher.persist.JavaActionSerializer;
//import com.wmba.actiondispatcher.persist.PersistedActionHolder;
//import com.wmba.actiondispatcher.SingularAction;
//
//import java.util.List;
//
//public class AndroidActionPersister implements ActionPersister {
//
//  private final ActionSqlOpenHelper mOpenHelper;
//  private final ActionSerializer mSerializer;
//
//  public AndroidActionPersister(Context context) {
//    this(context, new JavaActionSerializer());
//  }
//
//  public AndroidActionPersister(Context context, ActionSerializer serializer) {
//    this.mOpenHelper = new ActionSqlOpenHelper(context);
//    this.mSerializer = serializer;
//  }
//
//  @Override public long persist(SingularAction action) {
//    byte[] serializedAction = mSerializer.serialize(action);
//    return mOpenHelper.insert(serializedAction);
//  }
//
//  @Override public void update(long id, SingularAction action) {
//    byte[] serializedAction = mSerializer.serialize(action);
//    mOpenHelper.update(id, serializedAction);
//  }
//
//  @Override public void delete(long id) {
//    mOpenHelper.delete(id);
//  }
//
//  @Override public List<PersistedActionHolder> getPersistedActions() {
//    List<PersistedActionHolder> serializedActions = mOpenHelper.getAllActions();
//
//    for (PersistedActionHolder holder : serializedActions) {
//      SingularAction action = mSerializer.deserialize(holder.getSerializedAction());
//      holder.setPersistedAction(action);
//    }
//
//    return serializedActions;
//  }
//
//  public void deleteAllActions() {
//    mOpenHelper.deleteAllActions();
//  }
//
//}
