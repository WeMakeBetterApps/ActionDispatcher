package com.wmba.actiondispatcher

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class BasicTests {
  private var dispatcher: ActionDispatcher = ActionDispatcher.Builder().build()

  @Before fun beforeTest() {
    dispatcher: ActionDispatcher = ActionDispatcher.Builder().build()
  }

  @Test fun simpleTest() {
    var count = 0;
    dispatcher.toSingle(SimpleAction())
        .subscribe({
          count++
          assertTrue(it)
        })

    block()
    assertEquals(1, count)

    val activeKeys = dispatcher.getActiveKeys();
    assertEquals(1, activeKeys.size())
    assertTrue(activeKeys.contains(KeySelector.DEFAULT_KEY))
  }

  fun block() {
    val value = dispatcher.toSingle(SimpleAction())
        .toObservable()
        .toBlocking()
        .first()
    assertTrue(value)
  }
}
