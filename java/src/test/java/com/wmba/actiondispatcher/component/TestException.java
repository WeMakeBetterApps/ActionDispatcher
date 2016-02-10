package com.wmba.actiondispatcher.component;

public class TestException extends RuntimeException {
  private final int mValue;

  public TestException() {
    this(-1);
  }

  public TestException(int value) {
    mValue = value;
  }

  public int getValue() {
    return mValue;
  }
}
