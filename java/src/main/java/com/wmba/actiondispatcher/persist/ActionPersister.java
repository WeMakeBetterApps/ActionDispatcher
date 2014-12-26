package com.wmba.actiondispatcher.persist;

import com.wmba.actiondispatcher.SingularAction;

import java.util.List;

public interface ActionPersister {

  long persist(SingularAction action);
  void update(long id, SingularAction action);
  void delete(long id);
  List<PersistedActionHolder> getPersistedActions();

}
