package com.wmba.actiondispatcher;

import java.util.List;

public interface ActionPersister {

  long persist(SingularAction action);
  void update(long id, SingularAction action);
  void delete(long id);
  List<PersistedActionHolder> getPersistedActions();

}
