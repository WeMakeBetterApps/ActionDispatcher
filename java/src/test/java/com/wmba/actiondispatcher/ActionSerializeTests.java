package com.wmba.actiondispatcher;

import com.wmba.actiondispatcher.persist.JavaActionSerializer;

import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

import static org.junit.Assert.*;

public class ActionSerializeTests {
  private JavaActionSerializer mSerializer;

  @Before public void beforeTest() {
    mSerializer = new JavaActionSerializer();
  }

  @Test public void serializeDeserializeTest() {
    TestAction action1 = new TestAction();
    action1.booleanTest = true;
    action1.intTest = 30;
    action1.longTest = 500000000000L;
    action1.floatTest = 50000f;
    action1.doubleTest = -5000d;
    action1.stringTest = "test1";
    TestAction serializedAction1 = mSerializer.deserialize(mSerializer.serialize(action1));
    testActionEquality(action1, serializedAction1);

    // Child class
    TestActionChild action2 = new TestActionChild();
    action1.booleanTest = true;
    action1.intTest = 30;
    action1.longTest = 500000000000L;
    action1.floatTest = 50000f;
    action1.doubleTest = -5000d;
    action1.stringTest = "test1";
    TestActionChild serializedAction2 = mSerializer.deserialize(mSerializer.serialize(action2));
    testChildActionEquality(action2, serializedAction2);

    action2.transientBooleanTest = true;
    TestActionChild serializedAction3 = mSerializer.deserialize(mSerializer.serialize(action2));
    assertFalse(serializedAction3.transientBooleanTest);
    assertFalse(serializedAction3.unsetBooleanTest);
    testChildActionEquality(action2, serializedAction3);

    action2.transientBooleanTest = false;
    TestActionChild serializedAction4 = mSerializer.deserialize(mSerializer.serialize(action2));
    assertFalse(serializedAction4.transientBooleanTest);
    assertFalse(serializedAction4.unsetBooleanTest);
    testChildActionEquality(action2, serializedAction4);
  }

  private void testChildActionEquality(TestActionChild action1, TestActionChild action2) {
    testActionEquality(action1, action2);

    assertEquals(action1.unsetBooleanTest, action2.unsetBooleanTest);
  }

  private void testActionEquality(TestAction action1, TestAction action2) {
    assertEquals(action1.booleanTest, action2.booleanTest);
    assertEquals(action1.intTest, action2.intTest);
    assertEquals(action1.longTest, action2.longTest);
    assertEquals(action1.floatTest, action2.floatTest, 0.001d);
    assertEquals(action1.doubleTest, action2.doubleTest, 0.001f);
    assertEquals(action1.stringTest, action2.stringTest);
  }

  @Test public void serializeSubObjectTest() {
    TestAction action1 = new TestAction();
    action1.booleanTest = true;
    action1.intTest = 30;
    action1.longTest = 500000000000L;
    action1.floatTest = 50000f;
    action1.doubleTest = -5000d;
    action1.stringTest = "test1";
    TestAction serializedAction1 = mSerializer.deserialize(mSerializer.serialize(action1));
    testActionEquality(action1, serializedAction1);

    // Can serialize internal objects
    TestActionChildWithSubObject subObjectAction = new TestActionChildWithSubObject();
    subObjectAction.subobjectTest = action1;
    TestActionChildWithSubObject serializedSubObjectAction = mSerializer.deserialize(mSerializer.serialize(subObjectAction));
    testActionEquality(action1, serializedSubObjectAction.subobjectTest);

    // Can't serialize internal objects that don't implement Serializable
    TestActionChildWithSubObject2 subObjectAction2 = new TestActionChildWithSubObject2();
    subObjectAction2.subobjectTest = action1;
    subObjectAction2.nonSerializableObjectTest = new Ball();
    boolean errorCaught = false;
    try {
      TestActionChildWithSubObject serializedSubObjectAction2 = mSerializer.deserialize(mSerializer.serialize(subObjectAction2));
    } catch (Throwable t) {
      errorCaught = true;
    }
    assertTrue(errorCaught);
  }

  private static class TestAction extends Action<TestResponse> implements Serializable {
    private boolean booleanTest;
    private int intTest;
    private long longTest;
    private float floatTest;
    private double doubleTest;
    private String stringTest;

    @Override public TestResponse execute() throws Throwable {
      return new TestResponse();
    }

    @Override public boolean isPersistent() {
      return true;
    }
  }

  private static class TestActionChild extends TestAction {
    private boolean unsetBooleanTest;
    private transient boolean transientBooleanTest;
  }

  private static class TestActionChildWithSubObject extends TestAction {
    public TestAction subobjectTest;
  }

  private static class TestActionChildWithSubObject2 extends TestActionChildWithSubObject {
    private Ball nonSerializableObjectTest;
  }

  private static class Ball {
    private boolean nonSerializableBooleanTest;
  }

  private static class TestResponse {}

}
