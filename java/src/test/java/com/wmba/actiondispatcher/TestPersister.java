package com.wmba.actiondispatcher;

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
  private Map<SingularAction, Long> mSavedActions = new HashMap<SingularAction, Long>();

  @Override public long persist(SingularAction action) {
    assertFalse(isPersisted(action));
    long id = mIdGenerator.getAndIncrement();
    mSavedActions.put(action, id);
    return id;
  }

  @Override public void update(long id, SingularAction action) {
    assertTrue(mSavedActions.containsKey(action));
    mSavedActions.put(action, id);
  }

  @Override public void delete(long id) {
    SingularAction action = null;
    for (Map.Entry<SingularAction, Long> entry: mSavedActions.entrySet()) {
      if (entry.getValue().equals(id)) {
        action = entry.getKey();
        break;
      }
    }

    assertNotNull(action);
    mSavedActions.remove(action);
  }

  public boolean isPersisted(Action action) {
    return mSavedActions.containsKey(action);
  }

  @Override public List<PersistedActionHolder> getPersistedActions() {
    Set<Map.Entry<SingularAction, Long>> entries = mSavedActions.entrySet();
    List<PersistedActionHolder> persistedActions = new ArrayList<PersistedActionHolder>(entries.size());
    for (Map.Entry<SingularAction, Long> entry : entries) {
      PersistedActionHolder persistedActionHolder = new PersistedActionHolder(entry.getValue(), null);
      persistedActionHolder.setPersistedAction(entry.getKey());
      persistedActions.add(persistedActionHolder);
    }
    return persistedActions;
  }

}