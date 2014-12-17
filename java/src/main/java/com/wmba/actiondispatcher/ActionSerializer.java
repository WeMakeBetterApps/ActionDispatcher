package com.wmba.actiondispatcher;

public interface ActionSerializer {

  byte[] serialize(SingularAction action);
  <T extends SingularAction> T deserialize(byte[] bytes);

}
