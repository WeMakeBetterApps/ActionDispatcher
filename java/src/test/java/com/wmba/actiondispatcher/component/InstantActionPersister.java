package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;
import com.wmba.actiondispatcher.ActionPersister;
import com.wmba.actiondispatcher.PersistedActionHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class InstantActionPersister implements ActionPersister {
  private AtomicLong mIdGenerator = new AtomicLong(0);
  private Map<Action<?>, Long> mSavedActions = new HashMap<Action<?>, Long>();

  @Override public synchronized long persist(Action<?> action) {
    assertFalse(isPersisted(action));
    long id = mIdGenerator.getAndIncrement();
    mSavedActions.put(action, id);
    return id;
  }

  @Override public synchronized void update(long id, Action<?> action) {
    assertTrue(mSavedActions.containsKey(action));
    mSavedActions.put(action, id);
  }

  @Override public synchronized void delete(long id) {
    Action action = null;
    for (Map.Entry<Action<?>, Long> entry: mSavedActions.entrySet()) {
      if (entry.getValue().equals(id)) {
        action = entry.getKey();
        break;
      }
    }

    assertNotNull(action);
    mSavedActions.remove(action);
  }

  public synchronized boolean isPersisted(Action<?> action) {
    return mSavedActions.containsKey(action);
  }

  @Override public synchronized List<PersistedActionHolder> getPersistedActions() {
    Set<Map.Entry<Action<?>, Long>> entries = mSavedActions.entrySet();
    List<PersistedActionHolder> persistedActions = new ArrayList<PersistedActionHolder>(entries.size());
    for (Map.Entry<Action<?>, Long> entry : entries) {
      persistedActions.add(new PersistedActionHolder(entry.getValue(), entry.getKey()));
    }
    return persistedActions;
  }

  @Override public synchronized void deleteAll() {
    mSavedActions.clear();
    mIdGenerator.set(0);
  }
}
