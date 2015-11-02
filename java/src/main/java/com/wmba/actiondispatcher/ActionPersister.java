package com.wmba.actiondispatcher;

import java.util.List;

/**
 * Persists actions before they are run to ensure that they eventually run. Implementations must be
 * thread safe.
 */
public interface ActionPersister {
  long persist(Action<?> action);
  void update(long id, Action<?> action);
  void delete(long id);
  List<PersistedActionHolder> getPersistedActions();

  /**
   * Delete all persisted actions.
   */
  void deleteAll();
}
