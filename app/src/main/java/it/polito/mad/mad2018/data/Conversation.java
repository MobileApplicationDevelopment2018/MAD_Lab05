package it.polito.mad.mad2018.data;

import android.support.annotation.NonNull;
import android.text.format.DateUtils;

import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.polito.mad.mad2018.utils.Utilities;

public class Conversation implements Serializable {

    public static final String CONVERSATION_KEY = "conversation_key";
    public static final String CONVERSATION_ID_KEY = "conversation_id_key";

    public static final long UPDATE_TIME = 30000;

    private static final String FIREBASE_CONVERSATIONS_KEY = "conversations";
    private static final String FIREBASE_MESSAGES_KEY = "messages";
    private static final String FIREBASE_OWNER_KEY = "owner";
    private static final String FIREBASE_PEER_KEY = "peer";
    private static final String FIREBASE_UNREAD_MESSAGES_KEY = "unreadMessages";
    private static final String FIREBASE_FLAGS_KEY = "flags";
    private static final String FIREBASE_FLAG_ARCHIVED_KEY = "archived";
    private static final String FIREBASE_CONVERSATION_ORDER_BY_KEY = "timestamp";

    private final String conversationId;
    private final Conversation.Data data;

    private transient ValueEventListener archivedListener;

    public Conversation(@NonNull Book book) {
        this.conversationId = Conversation.generateConversationId();

        this.data = new Data();
        this.data.bookId = book.getBookId();
        this.data.owner.uid = book.getOwnerId();
        this.data.peer.uid = LocalUserProfile.getInstance().getUserId();
    }

    public Conversation(@NonNull String conversationId,
                        @NonNull Data data) {
        this.conversationId = conversationId;
        this.data = data;
    }

    private static String generateConversationId() {
        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY).push().getKey();
    }

    private static FirebaseRecyclerOptions<Conversation> getConversations(
            @NonNull DatabaseReference keyQuery) {

        DatabaseReference dataRef = FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY);

        return new FirebaseRecyclerOptions.Builder<Conversation>()
                .setIndexedQuery(keyQuery.orderByChild(FIREBASE_CONVERSATION_ORDER_BY_KEY), dataRef,
                        snapshot -> {
                            String conversationId = snapshot.getKey();
                            Conversation.Data data = snapshot.getValue(Conversation.Data.class);
                            assert data != null;
                            return new Conversation(conversationId, data);
                        })
                .build();
    }

    public static FirebaseRecyclerOptions<Conversation> getActiveConversations() {
        return Conversation.getConversations(LocalUserProfile.getActiveConversationsReference());
    }

    public static FirebaseRecyclerOptions<Conversation> getArchivedConversations() {
        return Conversation.getConversations(LocalUserProfile.getArchivedConversationsReference());
    }

    public static ValueEventListener setOnConversationLoadedListener(@NonNull String conversationId,
                                                                     @NonNull ValueEventListener listener) {

        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .addValueEventListener(listener);
    }

    public static void unsetOnConversationLoadedListener(@NonNull String conversationId,
                                                         @NonNull ValueEventListener listener) {

        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .removeEventListener(listener);
    }

    public void startArchivedListener(@NonNull OnConversationArchivedListener listener) {

        archivedListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object archived = dataSnapshot.getValue();
                if (archived instanceof Boolean && !Conversation.this.data.flags.archived
                        && (boolean) archived) {
                    Conversation.this.data.flags.archived = true;
                    listener.onConversationArchived();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                /* Do nothing */
            }
        };

        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(FIREBASE_FLAGS_KEY)
                .child(FIREBASE_FLAG_ARCHIVED_KEY)
                .addValueEventListener(archivedListener);
    }

    public void stopArchivedListener() {

        if (archivedListener != null) {
            FirebaseDatabase.getInstance().getReference()
                    .child(FIREBASE_CONVERSATIONS_KEY)
                    .child(conversationId)
                    .child(FIREBASE_FLAGS_KEY)
                    .child(FIREBASE_FLAG_ARCHIVED_KEY)
                    .removeEventListener(archivedListener);
            archivedListener = null;
        }
    }

    public String getConversationId() {
        return conversationId;
    }

    public FirebaseRecyclerOptions<Message> getMessages() {
        DatabaseReference dataRef = FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(FIREBASE_MESSAGES_KEY);

        return new FirebaseRecyclerOptions.Builder<Conversation.Message>()
                .setQuery(dataRef,
                        snapshot -> {
                            Conversation.Data.Message data = snapshot.getValue(Conversation.Data.Message.class);
                            assert data != null;
                            return new Conversation.Message(data);
                        })
                .build();
    }

    public boolean isNew() {
        return this.data.messages.size() == 0;
    }

    public boolean isArchived() {
        return this.data.flags.archived;
    }

    private boolean isBookOwner() {
        return LocalUserProfile.isLocal(this.data.owner.uid);
    }

    public String getPeerUserId() {
        return isBookOwner() ? this.data.peer.uid : this.data.owner.uid;
    }

    public String getBookId() {
        return this.data.bookId;
    }

    public int getUnreadMessagesCount() {
        return this.isBookOwner()
                ? this.data.owner.unreadMessages
                : this.data.peer.unreadMessages;
    }

    public void setMessagesAllRead() {
        String user_key = this.isBookOwner() ? FIREBASE_OWNER_KEY : FIREBASE_PEER_KEY;
        Conversation.Data.User user = this.isBookOwner() ? this.data.owner : this.data.peer;

        user.unreadMessages = 0;
        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(user_key)
                .child(FIREBASE_UNREAD_MESSAGES_KEY)
                .setValue(0);
    }

    public Message getLastMessage() {
        if (this.data.messages.size() == 0) {
            return null;
        }

        String last = Collections.max(this.data.messages.keySet());
        return new Message(this.data.messages.get(last));
    }

    public Task<?> sendMessage(@NonNull String text) {
        Data.Message message = new Data.Message();
        message.recipient = getPeerUserId();
        message.text = text;

        List<Task<?>> tasks = new ArrayList<>();
        DatabaseReference conversationReference = FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(this.conversationId);

        if (this.data.messages.size() == 0) {
            tasks.add(conversationReference.setValue(this.data));
            tasks.add(LocalUserProfile.getInstance().addConversation(this.conversationId, this.data.bookId));
        }

        DatabaseReference messageReference = conversationReference
                .child(FIREBASE_MESSAGES_KEY).push();
        tasks.add(messageReference.setValue(message));

        this.data.messages.put(messageReference.getKey(), message);

        return Tasks.whenAllSuccess(tasks);
    }

    public Task<?> archiveConversation() {
        this.data.flags.archived = true;

        List<Task<?>> tasks = new ArrayList<>();

        tasks.add(FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(FIREBASE_FLAGS_KEY)
                .child(FIREBASE_FLAG_ARCHIVED_KEY)
                .setValue(true));

        Task<?> task = LocalUserProfile.getInstance().archiveConversation(conversationId);
        if (task != null) {
            tasks.add(task);
        }

        return Tasks.whenAllSuccess(tasks);
    }

    public Task<?> deleteConversation() {
        return LocalUserProfile.getInstance().deleteConversation(conversationId);
    }

    public interface OnConversationArchivedListener {
        void onConversationArchived();
    }

    public static class Message implements Serializable {

        private final String localUserId;
        private final Conversation.Data.Message message;

        private Message(@NonNull Conversation.Data.Message message) {

            this.localUserId = LocalUserProfile.getInstance().getUserId();
            this.message = message;
        }

        public boolean isRecipient() {
            return Utilities.equals(message.recipient, localUserId);
        }

        public String getText() {
            return message.text;
        }

        public String getDateTime() {
            long now = System.currentTimeMillis();
            long messageTimeStamp = message.getTimestamp();
            if (messageTimeStamp > now) {
                now = messageTimeStamp;
            }
            return DateUtils.getRelativeTimeSpanString(messageTimeStamp, now, DateUtils.MINUTE_IN_MILLIS).toString();
        }
    }

    /* Fields need to be public to enable Firebase to access them */
    @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
    public static class Data implements Serializable {

        public String bookId;
        public User owner;
        public User peer;
        public Conversation.Data.Flags flags;

        public Map<String, Message> messages;

        public Data() {
            this.bookId = null;
            this.owner = new User();
            this.peer = new User();
            this.flags = new Conversation.Data.Flags();
            this.messages = new HashMap<>();
        }

        private static class Flags implements Serializable {
            public boolean archived;
            public boolean bookDeleted;

            public Flags() {
                this.archived = false;
                this.bookDeleted = false;
            }
        }

        private static class Message implements Serializable {
            public String recipient;
            public String text;
            public Object timestamp;

            public Message() {
                this.recipient = null;
                this.text = null;
                this.timestamp = ServerValue.TIMESTAMP;
            }

            @Exclude
            private long getTimestamp() {
                return this.timestamp instanceof Long
                        ? (long) this.timestamp
                        : System.currentTimeMillis();
            }
        }

        private static class User implements Serializable {
            public String uid;
            public int unreadMessages;

            public User() {
                this.uid = null;
                this.unreadMessages = 0;
            }
        }
    }
}
