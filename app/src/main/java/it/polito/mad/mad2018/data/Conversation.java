package it.polito.mad.mad2018.data;

import android.support.annotation.IntDef;
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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.polito.mad.mad2018.MAD2018Application;
import it.polito.mad.mad2018.R;
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
    private static final String FIREBASE_FLAG_BORROWING_STATE_KEY = "borrowingState";
    private static final String FIREBASE_FLAG_RETURN_STATE_KEY = "returnState";
    private static final String FIREBASE_CONVERSATION_ORDER_BY_KEY = "timestamp";
    private static final String FIREBASE_RATING_KEY = "rating";

    private final String conversationId;
    private final Conversation.Data data;
    private transient ValueEventListener onConversationFlagsUpdatedListener;

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

    public void startOnConversationFlagsUpdatedListener(@NonNull OnConversationFlagsUpdatedListener listener) {

        onConversationFlagsUpdatedListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Conversation.Data.Flags flags = dataSnapshot.getValue(Conversation.Data.Flags.class);
                if (flags != null) {
                    Conversation.this.data.flags = flags;
                    listener.onConversationFlagsChanged();
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
                .addValueEventListener(onConversationFlagsUpdatedListener);
    }

    public void stopOnConversationFlagsUpdatedListener() {

        if (onConversationFlagsUpdatedListener != null) {
            FirebaseDatabase.getInstance().getReference()
                    .child(FIREBASE_CONVERSATIONS_KEY)
                    .child(conversationId)
                    .child(FIREBASE_FLAGS_KEY)
                    .removeEventListener(onConversationFlagsUpdatedListener);
            onConversationFlagsUpdatedListener = null;
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

    public boolean isArchivable() {
        return !isArchived() &&
                (this.data.flags.borrowingState != Data.Flags.ACCEPTED ||
                        this.data.flags.returnState == Data.Flags.ACCEPTED);
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

    private Task<?> sendMessage(@NonNull String text, boolean special) {
        Data.Message message = new Data.Message();
        message.recipient = getPeerUserId();
        message.text = text;
        message.special = special;

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

    public Task<?> sendMessage(@NonNull String text) {
        return sendMessage(text, false);
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

    public boolean canRequestBorrowing() {
        return !this.isBookOwner() && !this.isArchived() &&
                this.data.flags.borrowingState == Data.Flags.NOT_REQUESTED;
    }

    private boolean isPendingBorrowingRequest() {
        return !this.isArchived() && this.data.flags.borrowingState == Data.Flags.REQUESTED;
    }

    public boolean canAnswerBorrowingRequest(@NonNull Book book) {
        return this.isBookOwner() && this.isPendingBorrowingRequest() && book.isAvailable();
    }

    public boolean canRequestReturn() {
        return !this.isBookOwner() && !this.isArchived() &&
                this.data.flags.borrowingState == Data.Flags.ACCEPTED &&
                this.data.flags.returnState == Data.Flags.NOT_REQUESTED;
    }

    private boolean isPendingReturnRequest() {
        return !this.isArchived() &&
                this.data.flags.borrowingState == Data.Flags.ACCEPTED &&
                this.data.flags.returnState == Data.Flags.REQUESTED;
    }

    public boolean canConfirmReturnRequest() {
        return this.isBookOwner() && isPendingReturnRequest();
    }

    public boolean canUploadRating() {
        return data.flags.returnState == Data.Flags.ACCEPTED &&
                !(isBookOwner() ? this.data.flags.ownerFeedback : this.data.flags.peerFeedback);
    }

    public Task<?> requestBorrowing(@NonNull Book book) {
        this.data.flags.borrowingState = Data.Flags.REQUESTED;
        List<Task<?>> tasks = new ArrayList<>();

        tasks.add(FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(FIREBASE_FLAGS_KEY)
                .child(FIREBASE_FLAG_BORROWING_STATE_KEY)
                .setValue(this.data.flags.borrowingState));

        tasks.add(sendMessage(MAD2018Application.getApplicationContextStatic()
                .getString(R.string.message_request_borrowing, book.getTitle()), true));

        return Tasks.whenAllSuccess(tasks);
    }

    public Task<?> acceptBorrowingRequest() {
        this.data.flags.borrowingState = Data.Flags.ACCEPTED;

        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(FIREBASE_FLAGS_KEY)
                .child(FIREBASE_FLAG_BORROWING_STATE_KEY)
                .setValue(this.data.flags.borrowingState);

        // The message is sent through the cloud functions to reduce the possibility of race conditions
    }

    public Task<?> rejectBorrowingRequest() {
        this.data.flags.borrowingState = Data.Flags.NOT_REQUESTED;
        List<Task<?>> tasks = new ArrayList<>();

        tasks.add(FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(FIREBASE_FLAGS_KEY)
                .child(FIREBASE_FLAG_BORROWING_STATE_KEY)
                .setValue(this.data.flags.borrowingState));

        tasks.add(sendMessage(MAD2018Application.getApplicationContextStatic()
                .getString(R.string.message_borrowing_request_reject), true));

        return Tasks.whenAllSuccess(tasks);
    }

    public Task<?> requestReturn(@NonNull Book book) {
        this.data.flags.returnState = Data.Flags.REQUESTED;
        List<Task<?>> tasks = new ArrayList<>();

        tasks.add(FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(FIREBASE_FLAGS_KEY)
                .child(FIREBASE_FLAG_RETURN_STATE_KEY)
                .setValue(this.data.flags.returnState));

        tasks.add(sendMessage(MAD2018Application.getApplicationContextStatic()
                .getString(R.string.message_request_return, book.getTitle()), true));

        return Tasks.whenAllSuccess(tasks);
    }

    public Task<?> confirmReturn() {
        this.data.flags.returnState = Data.Flags.ACCEPTED;
        List<Task<?>> tasks = new ArrayList<>();

        tasks.add(FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(FIREBASE_FLAGS_KEY)
                .child(FIREBASE_FLAG_RETURN_STATE_KEY)
                .setValue(this.data.flags.returnState));

        tasks.add(sendMessage(MAD2018Application.getApplicationContextStatic()
                .getString(R.string.message_request_return_confirm), true));

        return Tasks.whenAllSuccess(tasks);
    }

    public Task<?> uploadRating(Rating rating) {
        if (isBookOwner()) {
            this.data.flags.ownerFeedback = true;
        } else {
            this.data.flags.peerFeedback = true;
        }

        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(isBookOwner() ? FIREBASE_OWNER_KEY : FIREBASE_PEER_KEY)
                .child(FIREBASE_RATING_KEY)
                .setValue(rating);
    }

    public interface OnConversationFlagsUpdatedListener {
        void onConversationFlagsChanged();
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

        public boolean isSpecial() {
            return message.special;
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
    @SuppressWarnings({"WeakerAccess", "CanBeFinal", "unused"})
    public static class Data implements Serializable {

        public String bookId;
        public User owner;
        public User peer;
        public Conversation.Data.Flags flags;
        public String language;
        public Map<String, Message> messages;

        public Data() {
            this.bookId = null;
            this.owner = new User();
            this.peer = new User();
            this.flags = new Conversation.Data.Flags();
            this.language = MAD2018Application.getApplicationContextStatic()
                    .getResources().getConfiguration().locale.getLanguage();
            this.messages = new HashMap<>();
        }

        private static class Flags implements Serializable {
            private static final int NOT_REQUESTED = 1;
            private static final int REQUESTED = 2;
            private static final int ACCEPTED = 3;

            public boolean archived;
            public boolean bookDeleted;
            public @State
            int borrowingState;
            public @State
            int returnState;
            public boolean ownerFeedback;
            public boolean peerFeedback;

            public Flags() {
                this.archived = false;
                this.bookDeleted = false;
                this.borrowingState = NOT_REQUESTED;
                this.returnState = NOT_REQUESTED;
            }

            @Retention(RetentionPolicy.SOURCE)
            @IntDef({NOT_REQUESTED, REQUESTED, ACCEPTED})
            private @interface State {
            }
        }

        private static class Message implements Serializable {
            public String recipient;
            public String text;
            public Object timestamp;
            public boolean special;

            public Message() {
                this.recipient = null;
                this.text = null;
                this.timestamp = ServerValue.TIMESTAMP;
                this.special = false;
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
