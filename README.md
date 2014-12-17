ActionDispatcher
================

### Creation

In most cases for Android, the default AndroidActionDispatcher with a few custom components should be enough:

```java
ActionDispatcher dispatcher = new AndroidActionDispatcher.Builder(context)
        .injector(new ActionDispatcher.ActionInjector() {
          @Override public void inject(Action action) {
            // TODO: Your method of injection here.
            Injector.inject(action);
          }
        })
        .actionRunner(new ActionDispatcher.ActionRunner() {
          @Override
          public void execute(final ActionDispatcher.ActionRunnable actionRunnable, Action[] actions) {
            // TODO: This would be the place to run the action in a Database Session should you have one.
            daoSession.runInTx(new Runnable() {
              @Override public void run() {
                actionRunnable.execute();
              }
            });
          }
        })
        .build();
```

### Actions

`Action` are how we divide up our asyncronous work. There are 2 main type of `Action`: `ComposableAction` and `NetworkAction`.

##### ComposableAction
An action that can be grouped together with other `ComposableAction` in the same database transaction. These are well suited to do database work, and should never be used to run network operations. These can also not be persisted.

##### SingularAction
An Action that is run by itself unrelated to other Actions. These can be persisted in a queue to ensure eventual running.

##### NetworkAction
A useful Android implementation of SingularAction is the `NetworkAction`. `NetworkAction`s with the default `KeySelector` run in a separate queue from other Actions. This is because `NetworkAction`s will pause running if the network is not available.

A good practice is to create your own overrided BaseNetworkAction that handles server errors, or other errors by default, so you only have to worry about handling the errors in one place.

```java
public abstract class BaseNetworkAction<T> extends NetworkAction<T> {

  public BaseNetworkAction() {
    super(false);
  }

  public BaseNetworkAction(boolean isPersistent) {
    super(isPersistent);
  }

  @Override public boolean shouldRetryForThrowable(Throwable throwable, Subscriber<T> subscriber) {
    Throwable cause = throwable;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }

    if (cause instanceof RetrofitError) {
      RetrofitError retrofitError = (RetrofitError) cause;
      if ( retrofitError.isNetworkError() ) {
        if (retrofitError.getCause() instanceof SocketTimeoutException) {
          subscriber.onError( new ConnectionTimeoutException() );
        } else {
          subscriber.onError( new NoInternetException() );
        }
      } else {
        subscriber.onError( new ServerException( retrofitError.getResponse().getStatus() ) );
      }
    }

    return false;
  }
}
```
