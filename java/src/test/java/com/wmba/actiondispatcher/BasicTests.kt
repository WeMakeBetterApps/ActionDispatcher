package com.wmba.actiondispatcher

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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

  @Test fun runAsyncTest() {
    var count = 0;

    var action1TheadId: Long? = null
    var action2TheadId: Long? = null
    var action1TheadName: String? = null
    var action2TheadName: String? = null

    dispatcher.toSingle(object: Action<Boolean>() {
      override fun execute(): Boolean {
        Thread.sleep(10)
        count++;
        assertEquals(2, count)

        action1TheadId = Thread.currentThread().getId()
        action1TheadName = Thread.currentThread().getName()

        return true
      }
    }).subscribe()

    dispatcher.toSingle(object: Action<Boolean>(key = "key2") {
      override fun execute(): Boolean {
        count++;
        assertEquals(1, count)

        action2TheadId = Thread.currentThread().getId()
        action2TheadName = Thread.currentThread().getName()

        return true
      }
    }).subscribe()

    block()

    assertEquals(2, count)

    assertNotNull(action1TheadId)
    assertNotNull(action2TheadId)
    assertNotNull(action1TheadName)
    assertNotNull(action2TheadName)
    assertNotEquals(action1TheadId, action2TheadId)
    assertNotEquals(action1TheadName, action2TheadName)
  }

  @Test fun returnValueTest() {
    val returnValue = dispatcher.toSingle(object: Action<String>() {
      override fun execute(): String {
        return "value"
      }
    }).toObservable().toBlocking().first()

    assertEquals("value", returnValue)
  }

  fun block() {
    val value = dispatcher.toSingle(SimpleAction())
        .toObservable()
        .toBlocking()
        .first()
    assertTrue(value)
  }
}
