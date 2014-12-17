package com.wmba.actiondispatcher.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.wmba.actiondispatcher.ActionDispatcher;
import com.wmba.actiondispatcher.Action;

public class AndroidNetworkActionPauser implements ActionDispatcher.ActionPauser {

  private final Context mContext;
  private ConnectivityManager mConnectivityManager;

  public AndroidNetworkActionPauser(Context context) {
    this.mContext = context;
  }

  @Override public boolean shouldPauseForAction(Action action) {
    return action instanceof NetworkAction
        && !isNetworkConnected();
  }

  private boolean isNetworkConnected() {
    ConnectivityManager cm = getConnectivityManager();
    NetworkInfo netInfo = cm.getActiveNetworkInfo();
    return netInfo != null && netInfo.isConnectedOrConnecting();
  }

  private ConnectivityManager getConnectivityManager() {
    if (mConnectivityManager == null)
      mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    return mConnectivityManager;
  }

}
