package com.wmba.actiondispatcher;

public interface ActionLogger {
  void logDebug(String message);
  void logError(Throwable t, String message);
}
