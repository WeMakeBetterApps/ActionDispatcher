package com.wmba.actiondispatcher.kotlin

import rx.Scheduler
import rx.Single
import rx.SingleSubscriber
import java.util.HashMap
import java.util.concurrent.*
import kotlin.properties.Delegates

public open class ActionDispatcher private constructor(
    private val actionRunner: ActionRunner,
    private val keySelector: KeySelector,
    private val actionPreparer: ActionPreparer?,
    private val actionLogger: ActionLogger?,
    private val actionPersister: ActionPersister?) {

  public class Builder {
    var actionRunner: ActionRunner? = null
    var keySelector: KeySelector? = null
    var actionPreparer: ActionPreparer? = null
    var actionLogger: ActionLogger? = null
    var actionPersister: ActionPersister? = null

    public fun build(): ActionDispatcher {
      return ActionDispatcher(
          actionRunner ?: ActionRunner(),
          keySelector ?: KeySelector(),
          actionPreparer,
          actionLogger,
          actionPersister
      )
    }
  }

  private val persistentExecutor: Executor = Executors.newSingleThreadExecutor();
  private val executorCache = ExecutorCache()

  public fun <T> toSingle(action: Action<T>): Single<T> {
    return toSingle(keySelector.getKey(action), action)
  }

  public fun <T> toSingleAsync(action: Action<T>): Single<T> {
    return toSingle(KeySelector.ASYNC_KEY, action)
  }

  public fun <T> toSingle(key: String, action: Action<T>): Single<T> {
    val single = Single.create(ExecutionContext(key, action))
    val scheduler = action.observeOn()
    return if (scheduler == null) single else single.observeOn(scheduler)
  }

  private inner class ExecutionContext<T>(
      private val key: String,
      private val action: Action<T>) : Single.OnSubscribe<T> {

    private var persistSemaphore: Semaphore? = null

    override fun call(subscriber: SingleSubscriber<in T>) {
      val executor = executorCache.getExecutorForKey(key)
      executor.execute {
        prepareAction()
        if (action.isPersistent) {
          if (actionPersister == null) {
            throw IllegalStateException("Running Persistent Action ${action.javaClass.getName()}, " +
                "but no ActionPersister is set.")
          }
          persistSemaphore = Semaphore(1)
          persistAction()
        }
        runAction(subscriber)
      }
    }

    private fun prepareAction() {
      try {
        actionPreparer?.prepare(action);
      } catch (t: Throwable) {
        actionLogger?.logError(t, "Error while preparing Action ${action.javaClass.getName()}.")
      }
    }

    private fun persistAction() {
      try {
        persistSemaphore!!.acquireUninterruptibly()
        persistentExecutor.execute({
          try {
            actionPersister!!.persist(action)
            persistSemaphore!!.release()
          } catch (t: Throwable) {
            actionLogger?.logError(t, "Error while persisting Action ${action.javaClass.getName()}.")
          }
        })

        persistSemaphore!!.acquireUninterruptibly()
      } catch (t: Throwable) {
        actionLogger?.logError(t, "Error while persisting Action ${action.javaClass.getName()}.")
      }
    }

    private fun persistActionUpdate() {
      try {
        persistSemaphore!!.acquireUninterruptibly()
        persistentExecutor.execute({
          try {
            actionPersister!!.update(action)
            persistSemaphore!!.release()
          } catch (t: Throwable) {
            actionLogger?.logError(t, "Error while persisting update for Action ${action.javaClass.getName()}.")
          }
        })

        persistSemaphore!!.acquireUninterruptibly()
      } catch (t: Throwable) {
        actionLogger?.logError(t, "Error while persisting update for Action ${action.javaClass.getName()}.")
      }
    }

    private fun runAction(subscriber: SingleSubscriber<in T>) {
      var completed = false;
      var count = 0;
      actionLogger?.logDebug("Running Action ${action.javaClass.getName()}.")
      do {
        if (action.runIfUnsubscribed || !subscriber.isUnsubscribed()) {
          try {
            actionRunner.execute(action)
            actionLogger?.logDebug("Action finished running ${action.javaClass.getName()}.")
            completed = true;
          } catch (t: Throwable) {
            val shouldRetry = action.shouldRetryForThrowable(t)
            actionLogger?.logError(t, "Error running Action ${action.javaClass.getName()}. " +
                "${if (shouldRetry) "Retrying. #${++count}" else "Not Retrying"}.")
            if (shouldRetry && action.isPersistent) persistActionUpdate()
          }
        }
      } while (!completed)
    }
  }
}

public open class ActionRunner {
  /**
   * @param action The action to be run.
   */
  public open fun execute(action: Action<*>) {
    action.execute()
  }
}

public interface ActionPreparer {
  /**
   * Prepare the action to be run. This would be a great place to inject dependencies into the action.
   * @param action The action to prepare.
   */
  fun prepare(action: Action<*>)
}

public interface ActionLogger {
  fun logDebug(message: String);
  fun logError(t: Throwable, message: String);
}

public open class KeySelector {
  companion object {
    val DEFAULT_KEY = "default_UnlikelyConflict";
    val ASYNC_KEY = "async_UnlikelyConflict";
  }

  /**
   * @param action the action that is being run
   * @return the key that the action should be run on
   */
  public open fun getKey(action: Action<*>): String {
    return action.key;
  }
}

public interface ActionPersister {
  fun persist(action: Action<*>): Long
  fun update(action: Action<*>)
  fun delete(action: Action<*>)
}

private class ExecutorCache {
  private val cache = HashMap<String, ExecutorService>()

  fun getExecutorForKey(key: String): ExecutorService {
    synchronized(cache) {
      var executor = cache.get(key);

      if (executor == null) {
        val tf = ThreadFactory { r ->
          val t = Thread(r, "ActionDispatcherThread-" + key)
          t.setPriority(Thread.MIN_PRIORITY)
          t.setDaemon(true)
          t
        }

        if (key == KeySelector.ASYNC_KEY) {
          executor = Executors.newCachedThreadPool(tf)
        } else {
          executor = Executors.newSingleThreadExecutor(tf)
        }
      }

      return executor;
    }
  }

  fun getActiveKeys(): Set<String> {
    synchronized(cache) {
      return cache.keySet()
    }
  }
}

public abstract class Action<T>(
    public val key: String = KeySelector.DEFAULT_KEY,
    public val runIfUnsubscribed: Boolean = true,
    public val retryLimit: Int = 0,
    public val isPersistent: Boolean = false) {

  var retryCount = -1;
    private set
    get() = if (retryCount >= 0) retryCount else 0

  public abstract fun execute(): T

  public open fun shouldRetryForThrowable(t: Throwable): Boolean {
    retryCount++;
    return retryCount < retryLimit;
  }

  public open fun observeOn(): Scheduler? {
    return null;
  }
}