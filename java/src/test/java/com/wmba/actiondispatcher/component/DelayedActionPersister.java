package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;
import com.wmba.actiondispatcher.PersistedActionHolder;

import java.util.List;

public class DelayedActionPersister extends InstantActionPersister {
  private final long mDelay;

  public DelayedActionPersister() {
    this(5L);
  }

  public DelayedActionPersister(long delay) {
    mDelay = delay;
  }

  @Override public synchronized long persist(Action<?> action) {
    sleep();
    return super.persist(action);
  }

  @Override public synchronized void update(long id, Action<?> action) {
    sleep();
    super.update(id, action);
  }

  @Override public synchronized void delete(long id) {
    sleep();
    super.delete(id);
  }

  @Override public synchronized List<PersistedActionHolder> getPersistedActions() {
    sleep();
    return super.getPersistedActions();
  }

  private void sleep() {
    try {
      Thread.sleep(mDelay);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
