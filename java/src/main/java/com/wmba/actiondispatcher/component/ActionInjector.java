package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;

/**
 * Can be supplied to ActionDispatcher at creation. Allows for action injection.
 */
public interface ActionInjector {
  void inject(Action action);
}
