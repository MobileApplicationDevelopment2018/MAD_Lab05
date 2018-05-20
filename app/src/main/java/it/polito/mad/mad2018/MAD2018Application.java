package it.polito.mad.mad2018;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;

import it.polito.mad.mad2018.data.LocalUserProfile;

public class MAD2018Application extends Application
        implements Application.ActivityLifecycleCallbacks {

    private static final long UPLOAD_RETRY_TIMEOUT = 15000;
    private static Context applicationContext;
    private int activitiesStartedCount;

    public static Context getApplicationContextStatic() {
        return applicationContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MAD2018Application.applicationContext = getApplicationContext();
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        FirebaseStorage.getInstance().setMaxUploadRetryTimeMillis(UPLOAD_RETRY_TIMEOUT);
        registerActivityLifecycleCallbacks(this);
        this.activitiesStartedCount = 0;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (this.activitiesStartedCount == 0 && LocalUserProfile.getInstance() != null) {
            LocalUserProfile.getInstance().addOnProfileUpdatedListener();
        }
        this.activitiesStartedCount++;
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        this.activitiesStartedCount--;
        if (this.activitiesStartedCount == 0 && LocalUserProfile.getInstance() != null) {
            LocalUserProfile.getInstance().removeOnProfileUpdatedListener();
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
