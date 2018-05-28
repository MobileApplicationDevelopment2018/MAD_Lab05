package it.polito.mad.mad2018.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.firebase.ui.common.ChangeEventType;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.Conversation;
import it.polito.mad.mad2018.data.UserProfile;
import it.polito.mad.mad2018.utils.GlideApp;

public class ChatAdapter extends FirebaseRecyclerAdapter<Conversation, ChatAdapter.ChatHolder> {

    private final OnItemClickListener onItemClickListener;
    private final OnItemCountChangedListener onItemCountChangedListener;

    private final Map<String, UserProfile> userProfiles;
    private final Map<String, Pair<ValueEventListener, Set<Integer>>> profileListeners;

    private final Map<String, Book> books;
    private final Map<String, Pair<ValueEventListener, Set<Integer>>> bookListeners;


    ChatAdapter(@NonNull FirebaseRecyclerOptions<Conversation> options,
                @NonNull OnItemClickListener onItemClickListener,
                @NonNull OnItemCountChangedListener onItemCountChangedListener) {
        super(options);

        this.onItemClickListener = onItemClickListener;
        this.onItemCountChangedListener = onItemCountChangedListener;

        this.userProfiles = new HashMap<>();
        this.profileListeners = new HashMap<>();
        this.books = new HashMap<>();
        this.bookListeners = new HashMap<>();
    }

    @NonNull
    @Override
    public ChatHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatAdapter.ChatHolder(view, onItemClickListener);
    }

    @Override
    protected void onBindViewHolder(@NonNull ChatHolder holder, int position, @NonNull Conversation conversation) {

        String peerId = conversation.getPeerUserId();
        String bookId = conversation.getBookId();

        UserProfile peer = userProfiles.get(peerId);
        Book book = books.get(bookId);

        if (peer == null) {
            this.addListener(peerId, position, true);
        }
        if (book == null) {
            this.addListener(bookId, position, false);
        }

        holder.update(conversation, peer, book);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);

        for (Map.Entry<String, Pair<ValueEventListener, Set<Integer>>> entry :
                profileListeners.entrySet()) {
            UserProfile.unsetOnProfileLoadedListener(entry.getKey(), entry.getValue().first);
        }

        for (Map.Entry<String, Pair<ValueEventListener, Set<Integer>>> entry :
                bookListeners.entrySet()) {
            Book.unsetOnBookLoadedListener(entry.getKey(), entry.getValue().first);
        }
    }

    @Override
    public void onDataChanged() {
        super.onDataChanged();
        onItemCountChangedListener.onCountChangedListener(this.getItemCount());
    }

    @Override
    public void onChildChanged(@NonNull ChangeEventType type, @NonNull DataSnapshot snapshot, int newIndex, int oldIndex) {
        super.onChildChanged(type, snapshot, newIndex, oldIndex);

        if (type == ChangeEventType.MOVED && newIndex != oldIndex) {

            for (Pair<ValueEventListener, Set<Integer>> listener : profileListeners.values()) {
                if (listener.second.remove(oldIndex)) {
                    listener.second.add(newIndex);
                }
            }

            for (Pair<ValueEventListener, Set<Integer>> listener : bookListeners.values()) {
                if (listener.second.remove(oldIndex)) {
                    listener.second.add(newIndex);
                }
            }
        }
    }

    private void addListener(String id, int position, boolean requestProfile) {

        Map<String, Pair<ValueEventListener, Set<Integer>>> listeners =
                requestProfile ? profileListeners : bookListeners;

        Pair<ValueEventListener, Set<Integer>> listenerPair = listeners.get(id);
        if (listenerPair == null) {

            ValueEventListener listener = requestProfile
                    ? setOnProfileLoadedListener(id)
                    : setOnBookLoadedListener(id);

            listenerPair = new Pair<>(listener, new HashSet<>(Collections.singletonList(position)));
            listeners.put(id, listenerPair);
        } else {
            listenerPair.second.add(position);
        }
    }

    private ValueEventListener setOnProfileLoadedListener(String peerId) {

        return UserProfile.setOnProfileLoadedListener(
                peerId,
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        Pair<ValueEventListener, Set<Integer>> listener = profileListeners.remove(peerId);
                        UserProfile.Data data = dataSnapshot.getValue(UserProfile.Data.class);
                        if (listener == null || data == null) {
                            return;
                        }

                        UserProfile.unsetOnProfileLoadedListener(peerId, listener.first);

                        userProfiles.put(peerId, new UserProfile(peerId, data));
                        for (int position : listener.second) {
                            notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Pair<ValueEventListener, Set<Integer>> listener = profileListeners.remove(peerId);
                        UserProfile.unsetOnProfileLoadedListener(peerId, listener.first);
                    }
                });
    }

    private ValueEventListener setOnBookLoadedListener(String bookId) {

        return Book.setOnBookLoadedListener(
                bookId,
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        Pair<ValueEventListener, Set<Integer>> listener = bookListeners.remove(bookId);
                        Book.Data data = dataSnapshot.getValue(Book.Data.class);
                        if (listener == null || data == null) {
                            return;
                        }

                        Book.unsetOnBookLoadedListener(bookId, listener.first);

                        books.put(bookId, new Book(bookId, data));
                        for (int position : listener.second) {
                            notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Pair<ValueEventListener, Set<Integer>> listener = bookListeners.remove(bookId);
                        Book.unsetOnBookLoadedListener(bookId, listener.first);
                    }
                });
    }

    interface OnItemClickListener {
        void onClick(@NonNull View view, @NonNull Conversation conversation,
                     UserProfile peer, Book book);
    }

    interface OnItemCountChangedListener {
        void onCountChangedListener(int count);
    }

    static class ChatHolder extends RecyclerView.ViewHolder {

        private final Context context;
        private final TextView peerName;
        private final TextView bookTitle;
        private final TextView message;
        private final TextView date;
        private final ImageView bookPicture;
        private final TextView newMessagesCount;

        private Conversation conversation;
        private UserProfile peer;
        private Book book;

        ChatHolder(View view, @NonNull OnItemClickListener listener) {
            super(view);

            this.context = view.getContext();
            this.peerName = view.findViewById(R.id.cl_chat_item_peer);
            this.message = view.findViewById(R.id.cl_chat_item_message);
            this.date = view.findViewById(R.id.cl_chat_item_date);
            this.bookTitle = view.findViewById(R.id.cl_chat_item_book_title);
            this.bookPicture = view.findViewById(R.id.cl_chat_item_book_image);
            this.newMessagesCount = view.findViewById(R.id.cl_chat_new_messages_count);

            view.setOnClickListener(v -> listener.onClick(v, conversation, peer, book));
        }

        private void update(@NonNull Conversation conversation, UserProfile peer, Book book) {

            this.conversation = conversation;
            this.peer = peer;
            this.book = book;

            Conversation.Message last = conversation.getLastMessage();
            this.message.setText(last.getText());
            this.date.setText(last.getDateTime());

            GlideApp.with(context)
                    .load(book == null ? null : book.getBookThumbnailReferenceOrNull())
                    .placeholder(R.drawable.ic_default_book_preview)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(bookPicture);

            this.peerName.setText(peer == null ? context.getString(R.string.loading) : peer.getUsername());
            this.bookTitle.setText(book == null ? context.getString(R.string.loading) : book.getTitle());

            if (conversation.getUnreadMessagesCount() != 0) {
                this.newMessagesCount.setVisibility(View.VISIBLE);
                this.newMessagesCount.setText(String.valueOf(conversation.getUnreadMessagesCount()));
                this.message.setTypeface(message.getTypeface(), Typeface.BOLD);
            } else {
                this.message.setTypeface(null, Typeface.NORMAL);
                this.newMessagesCount.setVisibility(View.GONE);
            }
        }
    }
}
