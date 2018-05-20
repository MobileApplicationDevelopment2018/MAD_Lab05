package it.polito.mad.mad2018.chat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.Conversation;
import it.polito.mad.mad2018.data.OwnedBook;
import it.polito.mad.mad2018.data.UserProfile;
import it.polito.mad.mad2018.utils.FragmentDialog;
import it.polito.mad.mad2018.utils.TextWatcherUtilities;
import it.polito.mad.mad2018.utils.Utilities;

public class SingleChatFragment extends FragmentDialog<SingleChatFragment.DialogID> {

    private Conversation conversation;
    private UserProfile peer;
    private Book book;

    private EditText editTextMessage;
    private TextView viewNoMessages;
    private ImageButton buttonSend;
    private RecyclerView recyclerViewMessages;

    private SingleChatAdapter adapter;
    private Handler handlerUpdateMessageTime;
    private Runnable runnableUpdateMessageTime;

    public SingleChatFragment() { /* Required empty public constructor */ }

    public static SingleChatFragment newInstance(@NonNull Conversation conversation,
                                                 @NonNull UserProfile peer,
                                                 @NonNull Book book) {

        SingleChatFragment fragment = new SingleChatFragment();
        Bundle args = new Bundle();
        args.putSerializable(Conversation.CONVERSATION_KEY, conversation);
        args.putSerializable(UserProfile.PROFILE_INFO_KEY, peer);
        args.putSerializable(Book.BOOK_KEY, book);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assert getArguments() != null;
        conversation = (Conversation) getArguments().getSerializable(Conversation.CONVERSATION_KEY);
        peer = (UserProfile) getArguments().getSerializable(UserProfile.PROFILE_INFO_KEY);
        book = (Book) getArguments().getSerializable(Book.BOOK_KEY);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_single_chat, container, false);

        findViews(view);

        buttonSend.setEnabled(false);
        buttonSend.setOnClickListener(v -> {
            boolean wasNewConversation = conversation.isNew();
            conversation.sendMessage(editTextMessage.getText().toString());
            editTextMessage.setText(null);

            if (wasNewConversation) {
                setupMessages();
            }
        });

        editTextMessage.addTextChangedListener(new TextWatcherUtilities.GenericTextWatcher(
                editable -> buttonSend.setEnabled(!Utilities.isNullOrWhitespace(editable.toString()))
        ));

        if (!conversation.isNew()) {
            setupMessages();
        }

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        assert getActivity() != null;
        getActivity().setTitle(peer.getUsername());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_single_chat, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            /* To be changed */
            case R.id.sc_request_borrowing:
                this.requestBorrowing();
                return true;
            case R.id.sc_accept_borrowing:
                this.acceptBorrowingRequest();
                return true;
            case R.id.sc_reject_borrowing:
                conversation.rejectBorrowingRequest();
                return true;
            case R.id.sc_request_return:
                conversation.requestReturn(book);
                return true;
            case R.id.sc_confirm_return:
                this.confirmReturn();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (adapter != null) {
            adapter.startListening();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (adapter != null) {
            adapter.stopListening();
        }

        if (handlerUpdateMessageTime != null && runnableUpdateMessageTime != null) {
            handlerUpdateMessageTime.removeCallbacks(runnableUpdateMessageTime);
        }

        conversation.stopOnConversationFlagsUpdatedListener();
    }

    private void findViews(@NonNull View view) {
        editTextMessage = view.findViewById(R.id.message_send);
        buttonSend = view.findViewById(R.id.button_send);
        viewNoMessages = view.findViewById(R.id.chat_no_messages);
        recyclerViewMessages = view.findViewById(R.id.chat_messages);
    }

    private void setupMessages() {
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerViewMessages.setLayoutManager(linearLayoutManager);

        adapter = new SingleChatAdapter(conversation.getMessages(), (count) -> {
            viewNoMessages.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            recyclerViewMessages.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            if (count > 0) {
                linearLayoutManager.scrollToPosition(count - 1 - conversation.getUnreadMessagesCount());
            }
            conversation.setMessagesAllRead();
        });
        recyclerViewMessages.setAdapter(adapter);
        adapter.startListening();
        editTextMessage.setEnabled(true);

        conversation.startOnConversationFlagsUpdatedListener(() -> {
            if (conversation.isArchived()) {
                openDialog(SingleChatFragment.DialogID.DIALOG_ARCHIVED, true);
                conversation.stopOnConversationFlagsUpdatedListener();
            }
        });

        runnableUpdateMessageTime = () -> {
            adapter.notifyDataSetChanged();
            handlerUpdateMessageTime.postDelayed(runnableUpdateMessageTime, Conversation.UPDATE_TIME);
        };
        handlerUpdateMessageTime = new Handler();
        handlerUpdateMessageTime.postDelayed(runnableUpdateMessageTime, Conversation.UPDATE_TIME);
    }

    /* TODO: TO BE MODIFIED */
    private void requestBorrowing() {
        boolean wasNewConversation = conversation.isNew();
        conversation.requestBorrowing(book);

        if (wasNewConversation) {
            setupMessages();
        }
    }

    /* TODO: TO BE MODIFIED */
    private void acceptBorrowingRequest() {
        OwnedBook book = new OwnedBook(this.book);
        book.updateAlgoliaAvailability(false, (json, ex) -> {
            if (ex != null) {
                openDialog(SingleChatFragment.DialogID.DIALOG_ERROR_PERFORM_ACTION, true);
                return;
            }

            conversation.acceptBorrowingRequest();
        });
    }

    /* TODO: TO BE MODIFIED */
    private void confirmReturn() {
        OwnedBook book = new OwnedBook(this.book);
        book.updateAlgoliaAvailability(true, (json, ex) -> {
            if (ex != null) {
                openDialog(SingleChatFragment.DialogID.DIALOG_ERROR_PERFORM_ACTION, true);
                return;
            }

            conversation.confirmReturn();
        });
    }

    @Override
    protected void openDialog(@NonNull DialogID dialogId, boolean dialogPersist) {
        super.openDialog(dialogId, dialogPersist);

        Dialog dialogInstance = null;
        switch (dialogId) {
            case DIALOG_ARCHIVED:
                dialogInstance = new AlertDialog.Builder(getContext())
                        .setTitle(R.string.conversation_archived)
                        .setMessage(R.string.conversation_archived_message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            assert getActivity() != null;
                            getActivity().finish();
                        })
                        .setCancelable(false)
                        .show();
                break;

            case DIALOG_ERROR_PERFORM_ACTION:
                dialogInstance = Utilities.openErrorDialog(getContext(), R.string.error_perform_action);
                break;
        }

        if (dialogInstance != null) {
            this.setDialogInstance(dialogInstance);
        }
    }

    public enum DialogID {
        DIALOG_ARCHIVED,
        DIALOG_ERROR_PERFORM_ACTION,
    }
}
