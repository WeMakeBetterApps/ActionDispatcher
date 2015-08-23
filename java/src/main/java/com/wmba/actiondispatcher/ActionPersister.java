package com.wmba.actiondispatcher;

public interface ActionPersister {
  long persist(Action<?> action);
  void update(long id, Action<?> action);
  void delete(long id);
}
