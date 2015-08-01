package com.wmba.actiondispatcher

import org.junit.Before
import org.junit.Test
import rx.Subscriber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class ErrorTests {
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

  @Test fun nestedSubscribeBlockingErrorHandlingTest() {
    val errorAction1: Action<Boolean> = object : Action<Boolean>() {
      override fun execute(): Boolean {
        return subscribeBlocking(ErrorAction(300))
      }

      override fun shouldRetryForThrowable(t: Throwable): Boolean {
        assertTrue(t is TestException)
        assertEquals(300, (t as TestException).value)
        throw TestException(1)
      }
    }

    val errorAction2: Action<Boolean> = object : Action<Boolean>() {
      override fun execute(): Boolean {
        return subscribeBlocking(errorAction1)
      }

      override fun shouldRetryForThrowable(t: Throwable): Boolean {
        assertTrue(t is TestException)
        assertEquals(1, (t as TestException).value)
        throw TestException(2)
      }
    }

    val errorAction3: Action<Boolean> = object : Action<Boolean>() {
      override fun execute(): Boolean {
        return subscribeBlocking(errorAction2)
      }

      override fun shouldRetryForThrowable(t: Throwable): Boolean {
        assertTrue(t is TestException)
        assertEquals(2, (t as TestException).value)
        throw TestException(3)
      }
    }

    var hasRun = false;
    dispatcher.toSingle(errorAction3).subscribe({
      // Should not run
      assertTrue(false)
    }, {
      hasRun = true
      assertTrue(it is TestException)
      assertEquals(3, (it as TestException).value)
    })

    block()
    assertTrue(hasRun)
  }

  fun block() {
    val value = dispatcher.toSingle(SimpleAction())
        .toObservable()
        .toBlocking()
        .first()
    assertTrue(value)
  }

  private class SubscribeBlockingErrorActionLevel1(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(ErrorAction())
    }
  }

  private class SubscribeBlockingErrorActionLevel2(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(SubscribeBlockingErrorActionLevel1())
    }
  }

  private class SubscribeBlockingErrorActionLevel3(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(SubscribeBlockingErrorActionLevel2())
    }
  }

  private class SubscribeBlockingThrowsErrorActionLevel1(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(SubscribeBlockingErrorActionLevel2())
    }

    override fun shouldRetryForThrowable(t: Throwable): Boolean {
      throw TestException(1)
    }
  }

  private class SubscribeBlockingThrowsErrorActionLevel2(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(SubscribeBlockingThrowsErrorActionLevel1())
    }

    override fun shouldRetryForThrowable(t: Throwable): Boolean {
      throw TestException(2)
    }
  }

  private class SubscribeBlockingThrowsErrorActionLevel3(): Action<Boolean>() {
    override fun execute(): Boolean {
      return subscribeBlocking(SubscribeBlockingThrowsErrorActionLevel2())
    }

    override fun shouldRetryForThrowable(t: Throwable): Boolean {
      throw TestException(3)
    }
  }
}

