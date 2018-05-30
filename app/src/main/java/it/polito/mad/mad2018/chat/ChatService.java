package it.polito.mad.mad2018.chat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Random;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Conversation;

public class ChatService extends FirebaseMessagingService {

    private static final String ADMIN_CHANNEL_ID = "admin_channel";
    //private final static String GROUP_BOOKS_CHAT = "it.polito.mad.mad2018.chat.BOOKS_CHAT";
    private NotificationManager notificationManager;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        Intent resultIntent = new Intent(this, SingleChatActivity.class);
        String conversationId = remoteMessage.getData().get("conversationId");
        resultIntent.putExtra(Conversation.CONVERSATION_ID_KEY, conversationId);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        //Setting up Notification channels for android O and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setupChannels();
        }
        int notificationId = new Random().nextInt(60000);

        Notification parentMessageNotification = new NotificationCompat.Builder(this, ADMIN_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_24dp)
                //.setContentTitle(title)
                //.setContentText(content)
                .setGroupSummary(true)
                .setGroup(conversationId)
                .setAutoCancel(true)
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(resultPendingIntent)
                .build();

        //conversationId, title, message
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Notification childMessageNotification = new NotificationCompat.Builder(this, ADMIN_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_24dp)
                .setContentTitle(remoteMessage.getData().get("title"))
                .setContentText(remoteMessage.getData().get("message"))
                .setGroup(conversationId)
                .setAutoCancel(true)  //dismisses the notification on click
                .setSound(defaultSoundUri)
                .setContentIntent(resultPendingIntent)
                .build();

        notificationManager.notify(conversationId.hashCode(), parentMessageNotification);
        notificationManager.notify(notificationId, childMessageNotification);

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupChannels() {
        CharSequence adminChannelName = getString(R.string.notifications_admin_channel_name);
        String adminChannelDescription = getString(R.string.notifications_admin_channel_description);

        NotificationChannel adminChannel;
        adminChannel = new NotificationChannel(ADMIN_CHANNEL_ID, adminChannelName, NotificationManager.IMPORTANCE_LOW);
        adminChannel.setDescription(adminChannelDescription);
        adminChannel.enableLights(true);
        adminChannel.setLightColor(Color.WHITE);
        adminChannel.enableVibration(true);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(adminChannel);
        }
    }
}
