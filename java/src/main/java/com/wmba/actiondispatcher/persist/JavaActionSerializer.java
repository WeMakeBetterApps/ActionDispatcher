package com.wmba.actiondispatcher.persist;

import com.wmba.actiondispatcher.Action;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class JavaActionSerializer {
  public byte[] serialize(Action<?> action) {
    try {
      return unsafeSerialize(action);
    } catch (Throwable t) {
      throw new RuntimeException("Error while serializing action " + action.getClass().getName()
          + ". Make sure the class being serialized implements " + Serializable.class.getSimpleName()
          + ", and it has no internal objects that do not implement "
          + Serializable.class.getSimpleName() + ".", t);
    }
  }

  public <T extends Action<?>> T deserialize(byte[] bytes) {
    try {
      return unsafeDeserialize(bytes);
    } catch (Throwable t) {
      t.printStackTrace();
    }
    return null;
  }

  private byte[] unsafeSerialize(Action<?> action) throws IOException {
    if (action == null) {
      return null;
    }
    ByteArrayOutputStream bos = null;
    try {
      ObjectOutput out = null;
      bos = new ByteArrayOutputStream();
      out = new ObjectOutputStream(bos);
      out.writeObject(action);
      // Get the bytes of the serialized action
      return bos.toByteArray();
    } finally {
      if (bos != null) {
        bos.close();
      }
    }
  }

  private <T extends Action<?>> T unsafeDeserialize(byte[] bytes) throws IOException, ClassNotFoundException {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    ObjectInputStream in = null;
    try {
      in = new ObjectInputStream(new ByteArrayInputStream(bytes));
      //noinspection unchecked
      return (T) in.readObject();
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }
}

