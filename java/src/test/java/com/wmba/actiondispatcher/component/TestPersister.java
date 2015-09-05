package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;
import com.wmba.actiondispatcher.ActionPersister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestPersister implements ActionPersister {
  private AtomicLong mIdGenerator = new AtomicLong(0);
  private Map<Action, Long> mSavedActions = new HashMap<Action, Long>();

  @Override public long persist(Action<?> action) {
    assertFalse(isPersisted(action));
    long id = mIdGenerator.getAndIncrement();
    mSavedActions.put(action, id);
    return id;
  }

  @Override public void update(long id, Action<?> action) {
    assertTrue(mSavedActions.containsKey(action));
    mSavedActions.put(action, id);
  }

  @Override public void delete(long id) {
    Action action = null;
    for (Map.Entry<Action, Long> entry: mSavedActions.entrySet()) {
      if (entry.getValue().equals(id)) {
        action = entry.getKey();
        break;
      }
    }

    assertNotNull(action);
    mSavedActions.remove(action);
  }

  public boolean isPersisted(Action<?> action) {
    return mSavedActions.containsKey(action);
  }

  @Override public List<PersistedActionHolder> getPersistedActions() {
    Set<Map.Entry<Action, Long>> entries = mSavedActions.entrySet();
    List<PersistedActionHolder> persistedActions = new ArrayList<Action>(entries.size());
    for (Map.Entry<Action<?>, Long> entry : entries) {
      PersistedActionHolder persistedActionHolder = new PersistedActionHolder(entry.getValue(), null);
      persistedActionHolder.setPersistedAction(entry.getKey());
      persistedActions.add(persistedActionHolder);
    }
    return persistedActions;
  }

}
