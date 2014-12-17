package com.wmba.actiondispatcher.android;

import android.content.Context;

import com.wmba.actiondispatcher.ActionDispatcher;
import com.wmba.actiondispatcher.ActionPersister;

public class AndroidActionDispatcher extends ActionDispatcher {

  public static class Builder {

    private final Context mContext;

    private ActionRunner mActionRunner;
    private ObserveOnProvider mObserveOnProvider;
    private ActionKeySelector mKeySelector;
    private ActionPersister mPersister;
    private ActionPauser mPauser;
    private ActionInjector mInjector;

    public Builder(Context context) {
      if (context == null)
        throw new NullPointerException("Context can not be null when creating "
            + Builder.class.getName());

      this.mContext = context;
    }

    public Builder actionRunner(ActionRunner actionRunner) {
      mActionRunner = actionRunner;
      return this;
    }

    public Builder injector(ActionInjector injector) {
      mInjector = injector;
      return this;
    }

    public Builder overrideMainThreadObserveOnProvider(ObserveOnProvider observeOnProvider) {
      mObserveOnProvider = observeOnProvider;
      return this;
    }

    public Builder overrideKeySelector(ActionKeySelector keySelector) {
      mKeySelector = keySelector;
      return this;
    }

    public Builder overrideSQLPersister(ActionPersister persister) {
      mPersister = persister;
      return this;
    }

    public Builder overrideNetworkPauser(ActionPauser pauser) {
      mPauser = pauser;
      return this;
    }

    public AndroidActionDispatcher build() {
      if (mPauser == null)
        mPauser = new AndroidNetworkActionPauser(mContext);

      if (mPersister == null)
        mPersister = new AndroidActionPersister(mContext);

      if (mKeySelector == null)
        mKeySelector = new AndroidActionKeySelector();

      if (mObserveOnProvider == null)
        mObserveOnProvider = new AndroidMainThreadObserveOnProvider();

      return new AndroidActionDispatcher(mActionRunner, mObserveOnProvider, mKeySelector, mPersister,
          mPauser, mInjector);
    }

  }

  protected AndroidActionDispatcher(ActionRunner actionRunner, ObserveOnProvider observeOnProvider,
                                    ActionKeySelector keySelector, ActionPersister persister,
                                    ActionPauser pauser, ActionInjector injector) {
    super(actionRunner, observeOnProvider, keySelector, persister, pauser, injector);
  }

}
