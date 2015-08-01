package com.wmba.actiondispatcher

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class BasicTest {
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

    val blockingResult = dispatcher.toSingle(SimpleAction())
        .toObservable()
        .toBlocking()
        .first()

    assertTrue(blockingResult);
    assertEquals(1, count)
  }

  private class SimpleAction: Action<Boolean>() {
    override fun execute(): Boolean {
      return true
    }
  }
}

