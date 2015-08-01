package com.wmba.actiondispatcher

import org.junit.Before
import org.junit.Test
import rx.Subscriber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class ErrorTest {
  private var dispatcher: ActionDispatcher = ActionDispatcher.Builder().build()

  @Before fun beforeTest() {
    dispatcher: ActionDispatcher = ActionDispatcher.Builder().build()
  }

  @Test fun simpleErrorTest() {
    var count = 0
    dispatcher.toSingle(ErrorAction()).subscribe({}, {
      count++
      assertTrue(it is TestException)
    })

    block()
    assertEquals(1, count)
  }

  @Test fun subscribeBlockingErrorTest() {
    var count = 0
    dispatcher.toSingle(SubscribeBlockingErrorActionLevel1()).subscribe({
      // Should never call this
      assertFalse(true)
    }, {
      count++
      assertTrue(it is TestException)
    })

    block()
    assertEquals(1, count)
  }

  @Test fun nestedSubscribeBlockingErrorTest() {
    var count = 0
    dispatcher.toSingle(SubscribeBlockingErrorActionLevel3()).subscribe({
      // Should never be called
      assertFalse(true)
    }, {
      count++
      assertTrue(it is TestException)
    })

    block()
    assertEquals(1, count)
  }

  fun block() {
    dispatcher.toSingle(SimpleAction())
        .toObservable()
        .toBlocking()
        .first()
  }

  public class ErrorAction(val value: Int = 1): Action<Boolean>() {
    override fun execute(): Boolean {
      throw TestException(value)
    }
  }

  public class SubscribeBlockingErrorActionLevel1(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(ErrorAction())
    }
  }

  public class SubscribeBlockingErrorActionLevel2(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(SubscribeBlockingErrorActionLevel1())
    }
  }

  public class SubscribeBlockingErrorActionLevel3(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(SubscribeBlockingErrorActionLevel2())
    }
  }

  public class TestException(val value: Int): RuntimeException()

  private class SimpleAction: Action<Boolean>() {
    override fun execute(): Boolean {
      return true
    }
  }
}

