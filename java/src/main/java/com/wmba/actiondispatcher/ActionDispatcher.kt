package com.wmba.actiondispatcher

import rx.Scheduler
import rx.Single
import rx.SingleSubscriber
import java.lang
import java.util.HashMap
import java.util.concurrent.*

public open class ActionDispatcher private constructor(
    private val keySelector: KeySelector,
    private val actionPreparer: ActionPreparer?,
    private val actionLogger: ActionLogger?,
    private val actionPersister: ActionPersister?) {

  public class Builder {
    var keySelector: KeySelector? = null
    var actionPreparer: ActionPreparer? = null
    var actionLogger: ActionLogger? = null
    var actionPersister: ActionPersister? = null

    public fun build(): ActionDispatcher {
      return ActionDispatcher(
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

  fun <T> subscribeBlocking(subscriptionContext: SubscriptionContext, action: Action<T>): T {
    val executionContext = ExecutionContext("", action);
    val result = executionContext.runAction(subscriptionContext)
    return result
  }

  private inner class ExecutionContext<T>(
      private val key: String,
      private val action: Action<T>) : Single.OnSubscribe<T> {

    private var persistSemaphore: Semaphore? = null
    private var persistedId: Long? = null

    override fun call(subscriber: SingleSubscriber<in T>) {
      val executor = executorCache.getExecutorForKey(key)
      executor.execute {
        try {
          val response = runAction(SubscriptionContext(this@ActionDispatcher, subscriber))
          subscriber.onSuccess(response)
        } catch (t: Throwable) {
          subscriber.onError(t)
        }
      }
    }

    fun runAction(subscriptionContext: SubscriptionContext): T {
      action.subscriptionContext = subscriptionContext

      if (action.isPersistent) {
        if (actionPersister == null) {
          throw IllegalStateException("Running Persistent Action ${action.javaClass.getName()}, " +
              "but no ActionPersister is set.")
        }
        persistSemaphore = Semaphore(1)
        persistAction()
      }

      try {
        prepareAction()
        return runActionBody()
      } finally {
        if (persistedId != null) {
          persistActionDelete()
        }
      }
    }

    private fun prepareAction() {
      try {
        actionPreparer?.prepare(action);
      } catch (t: Throwable) {
        actionLogger?.logError(t, "Error while preparing Action ${action.javaClass.getName()}.")
      }
      action.prepare()
    }

    private fun persistAction() {
      try {
        persistSemaphore!!.acquireUninterruptibly()
        persistentExecutor.execute({
          try {
            persistedId = actionPersister!!.persist(action)
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
            actionPersister!!.update(persistedId!!, action)
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

    private fun persistActionDelete() {
      actionPersister!!.delete(persistedId!!)
    }

    private fun runActionBody(): T {
      var response: T = null
      var completed = false;
      var count = 0;
      actionLogger?.logDebug("Running Action ${action.javaClass.getName()}.")
      do {
        if (action.runIfUnsubscribed || action.isUnsubscribedInternal()) {

          if (count > 0) action.preRetry()

          try {
            response = action.execute()
            actionLogger?.logDebug("Action finished running ${action.javaClass.getName()}.")
            completed = true;
          } catch (t: Throwable) {
            val shouldRetry = action.shouldRetryForThrowable(t)
            count++
            actionLogger?.logError(t, "Error running Action ${action.javaClass.getName()}. " +
                "${if (shouldRetry) "Retrying. #${count}" else "Not Retrying"}.")

            if (shouldRetry) {
              if (persistedId != null) {
                persistActionUpdate()
              }
            } else {
              throw t
            }
          }
        }
      } while (!completed)

      return response
    }
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
    public val DEFAULT_KEY: String = "default";
    public val ASYNC_KEY: String = "async";
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
  fun update(id: Long, action: Action<*>)
  fun delete(id: Long)
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

        cache.put(key, executor)
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

private class SubscriptionContext(
    val dispatcher: ActionDispatcher,
    val subscriber: SingleSubscriber<*>) {
  fun isUnsubscribed(): Boolean {
    return subscriber.isUnsubscribed()
  }
}

public abstract class Action<T>(
    public val key: String = KeySelector.DEFAULT_KEY,
    public val runIfUnsubscribed: Boolean = true,
    public val retryLimit: Int = 0,
    public val isPersistent: Boolean = false) {

  var subscriptionContext: SubscriptionContext? = null
  var retryCount = -1;
    private set
    get() = if ($retryCount >= 0) $retryCount else 0

  public open fun prepare() {
  }

  public open fun preRetry() {
  }

  @throws(Throwable::class) public abstract fun execute(): T

  public open fun shouldRetryForThrowable(t: Throwable): Boolean {
    retryCount++;
    return retryCount < retryLimit;
  }

  public open fun observeOn(): Scheduler? {
    return null;
  }

  protected fun isUnsubscribed(): Boolean {
    return subscriptionContext?.isUnsubscribed() ?: false
  }

  internal fun isUnsubscribedInternal(): Boolean {
    return isUnsubscribed()
  }

  protected fun <T> subscribeBlocking(action: Action<T>): T {
    if (subscriptionContext == null) {
      throw IllegalStateException("SubscriptionContext is null. subscribeBlocking() can only be " +
          "called from within the Action lifecycle.")
    }
    return subscriptionContext!!.dispatcher.subscribeBlocking(subscriptionContext!!, action)
  }
}