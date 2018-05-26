package it.polito.mad.mad2018.chat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.Conversation;
import it.polito.mad.mad2018.data.OwnedBook;
import it.polito.mad.mad2018.data.UserProfile;
import it.polito.mad.mad2018.utils.FragmentDialog;
import it.polito.mad.mad2018.utils.TextWatcherUtilities;
import it.polito.mad.mad2018.utils.Utilities;

public class SingleChatFragment extends FragmentDialog<SingleChatFragment.DialogID>
        implements RatingFragment.OnDismissListener {
    private static final String RATING_DIALOG_FRAGMENT_TAG = "rating_dialog_fragment_tag";

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

    private Toolbar popupToolbar;

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
    public void onStart() {
        super.onStart();

        if (!conversation.isNew()) {
            setupMessages();
            if (adapter != null) {
                adapter.startListening();
            }
        }

        this.setupToolbar();
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

        hideFeedbackToolbar();
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
            this.setupToolbar();
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

    private void setupToolbar() {
        assert getActivity() != null;
        popupToolbar = getActivity().findViewById(R.id.popup_toolbar); // Extra part of the toolbar for the actions

        popupToolbar.findViewById(R.id.toolbar_request_book_layout).setVisibility(View.GONE);
        popupToolbar.findViewById(R.id.toolbar_return_book_layout).setVisibility(View.GONE);
        popupToolbar.findViewById(R.id.toolbar_accept_decline_request_layout).setVisibility(View.GONE);
        popupToolbar.findViewById(R.id.toolbar_leave_feedback_layout).setVisibility(View.GONE);

        if (conversation.canRequestBorrowing()) { // If I'm not the owner I have to make sure of being able to ask for the book
            setupToolbarForRequestBook();
        } else if (conversation.canRequestReturn()) {
            setupToolbarForReturnBook();
        } else if (conversation.isBookOwner() && (conversation.isPendingReturnRequest() || conversation.isPendingBorrowingRequest())) { // Am I the owner and have I a pending borrowing request?
            setupToolbarForAnsweringRequest();
        } else if (conversation.canUploadRating()) {
            setupToolbarForFeedback();
        } else {
            popupToolbar.setVisibility(View.GONE); // Just hide the extra part
        }
    }

    private void setupToolbarForRequestBook() {
        popupToolbar.setVisibility(View.VISIBLE);
        popupToolbar.findViewById(R.id.toolbar_request_book_layout).setVisibility(View.VISIBLE);
        popupToolbar.findViewById(R.id.pt_ask_for_book).setOnClickListener(v -> {
            if (conversation.canRequestBorrowing()) {

                boolean wasNewConversation = conversation.isNew();
                conversation.requestBorrowing(book);

                if (wasNewConversation) {
                    setupMessages();
                }
                this.setupToolbar();
            }
        });
    }

    private void setupToolbarForReturnBook() {
        popupToolbar.setVisibility(View.VISIBLE);
        popupToolbar.findViewById(R.id.toolbar_return_book_layout).setVisibility(View.VISIBLE);
        popupToolbar.findViewById(R.id.pt_return_book).setOnClickListener(v -> {
            if (conversation.canRequestReturn()) {
                conversation.requestReturn(book);
                popupToolbar.setVisibility(View.GONE);
            } else {
                Toast.makeText(this.getContext(), getResources().getString(R.string.can_not_return_book), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupToolbarForAnsweringRequest() {

        popupToolbar.setVisibility(View.VISIBLE);
        popupToolbar.findViewById(R.id.toolbar_accept_decline_request_layout).setVisibility(View.VISIBLE);
        TextView question = popupToolbar.findViewById(R.id.pt_accept_decline_question);

        if (conversation.isPendingReturnRequest()) {
            question.setText(getResources().getString(R.string.request_for_return_from_peer));
        } else {
            question.setText(getResources().getString(R.string.request_for_borrow_from_peer));
        }
        Button accept = popupToolbar.findViewById(R.id.pt_accept);
        Button decline = popupToolbar.findViewById(R.id.pt_decline);

        // Set actions of the buttons
        accept.setOnClickListener(v -> {
            if (conversation.isPendingReturnRequest()) {
                if (conversation.canConfirmReturnRequest())
                    this.confirmReturn();
                else {
                    Toast.makeText(this.getContext(), getResources().getString(R.string.can_not_return_book), Toast.LENGTH_SHORT).show();
                }
            } else {
                if (conversation.canAnswerBorrowingRequest()) {
                    this.acceptBorrowingRequest();
                } else {
                    Toast.makeText(this.getContext(), getResources().getString(R.string.can_not_borrowing_request), Toast.LENGTH_SHORT).show();
                }
            }
            this.setupToolbar();
        });

        decline.setOnClickListener(v -> {
            if (conversation.canAnswerBorrowingRequest()) {
                conversation.rejectBorrowingRequest();
            } else {
                Toast.makeText(this.getContext(), getResources().getString(R.string.can_not_borrowing_request), Toast.LENGTH_SHORT).show();
            }
            this.setupToolbar();
        });

        accept.setText(conversation.isPendingReturnRequest() ? R.string.confirm : R.string.accept);
        decline.setVisibility(conversation.isPendingReturnRequest() ? View.GONE : View.VISIBLE);
    }

    private void setupToolbarForFeedback() {
        popupToolbar.setVisibility(View.VISIBLE);
        popupToolbar.findViewById(R.id.toolbar_leave_feedback_layout).setVisibility(View.VISIBLE);
        popupToolbar.findViewById(R.id.pb_leave_feedback).setOnClickListener(v -> {
            RatingFragment dialog = RatingFragment.Factory.newInstance(conversation);
            dialog.show(getChildFragmentManager(), RATING_DIALOG_FRAGMENT_TAG);
        });
    }

    private void hideFeedbackToolbar() {
        popupToolbar.setVisibility(View.GONE);
    }

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

    @Override
    public void onDialogDismiss() {
        if (getActivity() != null) {
            this.setupToolbar();
        }
    }

    public enum DialogID {
        DIALOG_ARCHIVED,
        DIALOG_ERROR_PERFORM_ACTION,
    }
}
