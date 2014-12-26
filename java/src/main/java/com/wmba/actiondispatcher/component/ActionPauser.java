package com.wmba.actiondispatcher.component;

import com.wmba.actiondispatcher.Action;

/**
 * Can be supplied to ActionDispatcher at creation. Will be run before every action.
 */
public interface ActionPauser {
  boolean shouldPauseForAction(Action action);
}
