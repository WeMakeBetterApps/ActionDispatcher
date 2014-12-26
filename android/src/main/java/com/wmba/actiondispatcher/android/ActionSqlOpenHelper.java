package com.wmba.actiondispatcher.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.wmba.actiondispatcher.persist.PersistedActionHolder;

import java.util.ArrayList;
import java.util.List;

public class ActionSqlOpenHelper extends SQLiteOpenHelper {

  /*package*/ static final String TABLE_ACTIONS = "actions";

  /*package*/ static final String COLUMN_ID = "_id";
  /*package*/ static final String COLUMN_SERIALIZED_ACTION = "serialized_action";

  private static final String DB_NAME = "ActionDispatcherPersistentQueue.sqlite";
  private static final int DB_VERSION = 1;

  private final SQLiteDatabase mDB;

  public ActionSqlOpenHelper(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
    mDB = getWritableDatabase();
  }

  @Override public void onCreate(SQLiteDatabase db) {
    String sql = "CREATE TABLE " + TABLE_ACTIONS + " "
        + "("
        + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "`" + COLUMN_SERIALIZED_ACTION + "` BYTE NOT NULL" +
        ");";
    db.execSQL(sql);
  }

  @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACTIONS);
    onCreate(db);
  }

  public synchronized long insert(byte[] serializedAction) {
    ContentValues values = new ContentValues();
    values.put(COLUMN_SERIALIZED_ACTION, serializedAction);

    return mDB.insert(TABLE_ACTIONS, null, values);
  }

  public synchronized void update(long id, byte[] serializedAction) {
    ContentValues values = new ContentValues();
    values.put(COLUMN_SERIALIZED_ACTION, serializedAction);

    String where = COLUMN_ID + " = " + id;
    mDB.update(TABLE_ACTIONS, values, where, null);
  }

  public synchronized void delete(long id) {
    String where = COLUMN_ID + " = " + id;
    mDB.delete(TABLE_ACTIONS, where, null);
  }

  public synchronized List<PersistedActionHolder> getAllActions() {
    String sql = "SELECT " + COLUMN_ID + ", " + COLUMN_SERIALIZED_ACTION + " "
        + "FROM " + TABLE_ACTIONS + " ORDER BY " + COLUMN_ID + " ASC;";

    List<PersistedActionHolder> serializedActions = new ArrayList<PersistedActionHolder>();

    Cursor c = null;
    try {
      c = mDB.rawQuery(sql, null);

      int idIndex = c.getColumnIndex(COLUMN_ID);
      int actionIndex = c.getColumnIndex(COLUMN_SERIALIZED_ACTION);

      if (c.moveToFirst()) {
        do {
          long id = c.getLong(idIndex);
          byte[] serializedAction = c.getBlob(actionIndex);

          serializedActions.add(new PersistedActionHolder(id, serializedAction));
        } while (c.moveToNext());
      }

    } catch (Throwable t) {
      deleteAllActions();
    } finally {
      if (c != null)
        c.close();
    }

    return serializedActions;
  }

  public void deleteAllActions() {
    mDB.delete(TABLE_ACTIONS, null, null);
    mDB.execSQL("VACUUM");
  }

}