package com.example.user.myapplication;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class MyApplication extends Application {

    public static final String LOG_TAG = "MyApp";

    public boolean wasInBackground = true;

    //private AppSession appSession;
    private Timer mActivityTransitionTimer;
    private TimerTask mActivityTransitionTimerTask;
    private final long MAX_ACTIVITY_TRANSITION_TIME_MS = 2000;  // Time allowed for transitions

    Application.ActivityLifecycleCallbacks activityCallbacks = new Application.ActivityLifecycleCallbacks() {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {

            if (wasInBackground) {
                //Do app-wide came-here-from-background code
                appEntered();
            }
            stopActivityTransitionTimer();
        }

        @Override
        public void onActivityPaused(Activity activity) {
            startActivityTransitionTimer();
        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {

        }

    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(activityCallbacks);
    }

    public void startActivityTransitionTimer() {
        this.mActivityTransitionTimer = new Timer();
        this.mActivityTransitionTimerTask = new TimerTask() {
            public void run() {
                // Task is run when app is exited
                wasInBackground = true;
                appExited();
            }
        };

        this.mActivityTransitionTimer.schedule(mActivityTransitionTimerTask,
                MAX_ACTIVITY_TRANSITION_TIME_MS);
    }

    public void stopActivityTransitionTimer() {
        if (this.mActivityTransitionTimerTask != null) {
            this.mActivityTransitionTimerTask.cancel();
        }

        if (this.mActivityTransitionTimer != null) {
            this.mActivityTransitionTimer.cancel();
        }

        this.wasInBackground = false;
    }

    private void appEntered() {
        Log.i(LOG_TAG, "APP ENTERED");

       // appSession = new AppSession();
    }

    private void appExited() {
        Log.i(LOG_TAG, "APP EXITED");

        /*appSession.finishAppSession();

        // Submit AppSession to server
        submitAppSession(appSession);
        long sessionLength = (appSession.getT_close() - appSession.getT_open()) / 1000L;
        Log.i(LOG_TAG, "Session Length: " + sessionLength);*/
    }
}