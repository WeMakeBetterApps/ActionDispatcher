package com.wmba.actiondispatcher.persist;

import com.wmba.actiondispatcher.SingularAction;

public interface ActionSerializer {

  byte[] serialize(SingularAction action);
  <T extends SingularAction> T deserialize(byte[] bytes);

}
