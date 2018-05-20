package it.polito.mad.mad2018.chat;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import java.io.IOException;

import it.polito.mad.mad2018.data.LocalUserProfile;
import it.polito.mad.mad2018.data.UserProfile;

public class ChatIDService extends FirebaseInstanceIdService {

    public static void uploadToken(@NonNull UserProfile profile, String token) {
        final String FIREBASE_TOKENS_KEY = "tokens";

        if (token != null) {
            FirebaseDatabase.getInstance().getReference().child(FIREBASE_TOKENS_KEY)
                    .child(profile.getUserId())
                    .child(token).setValue(true);
        }
    }

    public static void deleteToken() {
        AsyncTask.execute(() -> {
            try {
                FirebaseInstanceId.getInstance().deleteInstanceId();
            } catch (IOException e) { /* Do nothing */ }
        });
    }

    @Override
    public void onTokenRefresh() {

        super.onTokenRefresh();
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();

        if (refreshedToken != null && LocalUserProfile.getInstance() != null) {
            uploadToken(LocalUserProfile.getInstance(), refreshedToken);
        }
    }
}