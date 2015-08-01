package com.wmba.actiondispatcher

public class ErrorAction(val value: Int = 1): Action<Boolean>() {
  override fun execute(): Boolean {
    throw TestException(value)
  }
}

public class TestException(val value: Int): RuntimeException()

public class SimpleAction: Action<Boolean>() {
  override fun execute(): Boolean {
    return true
  }
}

