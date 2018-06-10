package it.polito.mad.mad2018.chat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.firebase.ui.database.FirebaseRecyclerOptions;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.Conversation;
import it.polito.mad.mad2018.data.UserProfile;
import it.polito.mad.mad2018.utils.SwipeToActionCallback;

public class ActiveChatsFragment extends Fragment {

    private ChatAdapter adapter;
    private Dialog archiveDialog;

    private Handler handlerUpdateMessageTime;
    private Runnable runnableUpdateMessageTime;

    public ActiveChatsFragment() { /* Required empty public constructor */ }

    public static ActiveChatsFragment newInstance() {
        return new ActiveChatsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_active_chats, container, false);

        View noChatsView = view.findViewById(R.id.ac_no_active_chats);
        RecyclerView recyclerView = view.findViewById(R.id.ac_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        ProgressBar loading = view.findViewById(R.id.active_chats_loading);

        FirebaseRecyclerOptions<Conversation> options = Conversation.getActiveConversations();
        adapter = new ChatAdapter(options, (v, conversation, peer, book) -> {
            Intent toChat = new Intent(getActivity(), SingleChatActivity.class);
            toChat.putExtra(Conversation.CONVERSATION_KEY, conversation);
            toChat.putExtra(UserProfile.PROFILE_INFO_KEY, peer);
            toChat.putExtra(Book.BOOK_KEY, book);
            startActivity(toChat);
        }, (count) -> {
            loading.setVisibility(View.GONE);
            noChatsView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        });

        recyclerView.setAdapter(adapter);

        assert getContext() != null;
        ItemTouchHelper itemTouchhelper = new ItemTouchHelper(new SwipeController(getContext()));
        itemTouchhelper.attachToRecyclerView(recyclerView);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        adapter.startListening();

        runnableUpdateMessageTime = () -> {
            adapter.notifyDataSetChanged();
            handlerUpdateMessageTime.postDelayed(runnableUpdateMessageTime, Conversation.UPDATE_TIME);
        };
        handlerUpdateMessageTime = new Handler();
        handlerUpdateMessageTime.postDelayed(runnableUpdateMessageTime, Conversation.UPDATE_TIME);
    }

    @Override
    public void onStop() {
        super.onStop();
        adapter.stopListening();

        if (archiveDialog != null && archiveDialog.isShowing()) {
            archiveDialog.cancel();
        }
        if (handlerUpdateMessageTime != null && runnableUpdateMessageTime != null) {
            handlerUpdateMessageTime.removeCallbacks(runnableUpdateMessageTime);
        }
    }

    private class SwipeController extends SwipeToActionCallback {

        SwipeController(@NonNull Context context) {
            super(context, R.drawable.ic_archive_white_24dp, R.color.colorAccent);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

            Conversation conversation = adapter.getItem(viewHolder.getAdapterPosition());
            if (conversation.isArchivable()) {
                archiveDialog = new AlertDialog.Builder(getContext())
                        .setTitle(R.string.archive_conversation)
                        .setMessage(R.string.archive_conversation_message)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            conversation.archiveConversation();
                            adapter.notifyDataSetChanged();
                        })
                        .setNegativeButton(android.R.string.no, (dialog, which) ->
                                adapter.notifyItemChanged(viewHolder.getAdapterPosition())
                        )
                        .setOnCancelListener(dialog ->
                                adapter.notifyItemChanged(viewHolder.getAdapterPosition())
                        )
                        .show();
            } else {
                archiveDialog = new AlertDialog.Builder(getContext())
                        .setTitle(R.string.archive_conversation_not_possible)
                        .setMessage(R.string.archive_conversation_message_not_possible)
                        .setPositiveButton(android.R.string.yes, null)
                        .setOnDismissListener(dialog ->
                                adapter.notifyItemChanged(viewHolder.getAdapterPosition())
                        )
                        .show();
            }
        }
    }
}



