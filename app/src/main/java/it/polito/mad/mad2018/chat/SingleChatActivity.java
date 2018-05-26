package it.polito.mad.mad2018.chat;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.Conversation;
import it.polito.mad.mad2018.data.LocalUserProfile;
import it.polito.mad.mad2018.data.UserProfile;
import it.polito.mad.mad2018.library.BookInfoActivity;
import it.polito.mad.mad2018.library.BookInfoFragment;
import it.polito.mad.mad2018.library.MyBooksFragment;
import it.polito.mad.mad2018.profile.ShowProfileFragment;

public class SingleChatActivity extends AppCompatActivity
        implements ShowProfileFragment.OnShowOwnedBooksClickListener {

    private static final String SINGLE_CHAT_FRAGMENT_TAG = "single_chat_fragment_tag";

    private Conversation conversation;
    private UserProfile peer;
    private Book book;
    private String conversationId;

    private ValueEventListener conversationListener, profileListener, bookListener;
    private ValueEventListener localProfileListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Set the toolbar
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (savedInstanceState != null) {
            conversation = (Conversation) savedInstanceState.getSerializable(Conversation.CONVERSATION_KEY);
            peer = (UserProfile) savedInstanceState.getSerializable(UserProfile.PROFILE_INFO_KEY);
            book = (Book) savedInstanceState.getSerializable(Book.BOOK_KEY);
            conversationId = savedInstanceState.getString(Conversation.CONVERSATION_ID_KEY);
        } else {
            conversation = (Conversation) getIntent().getSerializableExtra(Conversation.CONVERSATION_KEY);
            peer = (UserProfile) getIntent().getSerializableExtra(UserProfile.PROFILE_INFO_KEY);
            book = (Book) getIntent().getSerializableExtra(Book.BOOK_KEY);
            conversationId = getIntent().getStringExtra(Conversation.CONVERSATION_ID_KEY);
        }

        setTitle(peer != null ? peer.getUsername() : getString(R.string.app_name));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(Conversation.CONVERSATION_KEY, conversation);
        outState.putSerializable(UserProfile.PROFILE_INFO_KEY, peer);
        outState.putSerializable(Book.BOOK_KEY, book);
        outState.putSerializable(Conversation.CONVERSATION_ID_KEY, conversationId);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sc_show_peer_profile:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.sca_main_fragment, ShowProfileFragment.newInstance(peer, false))
                        .addToBackStack(null)
                        .commit();
                return true;

            case R.id.sc_show_book_info:
                Intent toBookInfo = new Intent(this, BookInfoActivity.class);
                toBookInfo.putExtra(Book.BOOK_KEY, book);
                toBookInfo.putExtra(BookInfoFragment.BOOK_SHOW_OWNER_KEY, false);
                startActivity(toBookInfo);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void OnShowOwnedBooksClick(@NonNull UserProfile profile) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.sca_main_fragment, MyBooksFragment.newInstance(profile))
                .addToBackStack(null)
                .commit();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (LocalUserProfile.getInstance() != null) {
            afterLocalProfileLoaded();
        } else {
            setOnLocalProfileLoadedListener();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        unsetOnConversationLoadedListener();
        unsetOnProfileLoadedListener();
        unsetOnLocalProfileLoadedListener();
        unsetOnBookLoadedListener();
    }

    private void afterLocalProfileLoaded() {
        if (conversation == null) {
            if (conversationId == null) {
                conversationId = LocalUserProfile.getInstance().findConversationByBookId(book.getBookId());
                if (conversationId == null) {
                    conversation = new Conversation(book);
                    conversationId = conversation.getConversationId();
                    afterConversationLoaded();
                } else {
                    setOnConversationLoadedListener();
                }
            } else {
                setOnConversationLoadedListener();
            }
        } else {
            afterConversationLoaded();
        }
    }

    private void afterConversationLoaded() {
        if (peer != null && book != null) {
            afterAllDataLoaded();
            return;
        }

        if (peer == null) {
            setOnProfileLoadedListener();
        }
        if (book == null) {
            setOnBookLoadedListener();
        }
    }

    private void afterAllDataLoaded() {
        findViewById(R.id.sca_chat_loading).setVisibility(View.GONE);

        if (getSupportFragmentManager().findFragmentByTag(SINGLE_CHAT_FRAGMENT_TAG) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.sca_main_fragment,
                            SingleChatFragment.newInstance(conversation, peer, book),
                            SINGLE_CHAT_FRAGMENT_TAG)
                    .commit();
        }
    }

    private void setOnLocalProfileLoadedListener() {

        this.localProfileListener = LocalUserProfile.setOnProfileLoadedListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (!unsetOnLocalProfileLoadedListener()) {
                            return;
                        }

                        UserProfile.Data data = dataSnapshot.getValue(UserProfile.Data.class);
                        if (data != null) {
                            LocalUserProfile.setInstance(new LocalUserProfile(data));
                            afterLocalProfileLoaded();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        unsetOnLocalProfileLoadedListener();
                    }
                });
    }

    private boolean unsetOnLocalProfileLoadedListener() {
        if (this.localProfileListener != null) {
            LocalUserProfile.unsetOnProfileLoadedListener(this.localProfileListener);
            this.localProfileListener = null;
            return true;
        }
        return false;
    }

    private void setOnConversationLoadedListener() {

        conversationListener = Conversation.setOnConversationLoadedListener(conversationId, new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!unsetOnConversationLoadedListener())
                    return;

                Conversation.Data data = dataSnapshot.getValue(Conversation.Data.class);
                if (data != null) {
                    conversation = new Conversation(conversationId, data);
                    afterConversationLoaded();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                unsetOnConversationLoadedListener();
            }
        });
    }

    private boolean unsetOnConversationLoadedListener() {
        if (conversationListener != null) {

            Conversation.unsetOnConversationLoadedListener(conversationId, conversationListener);
            this.conversationListener = null;
            return true;
        }
        return false;
    }

    private void setOnProfileLoadedListener() {

        this.profileListener = UserProfile.setOnProfileLoadedListener(
                conversation.getPeerUserId(),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (!unsetOnProfileLoadedListener()) {
                            return;
                        }

                        UserProfile.Data data = dataSnapshot.getValue(UserProfile.Data.class);
                        if (data != null) {
                            peer = new UserProfile(conversation.getPeerUserId(), data);
                            if (book != null) {
                                afterAllDataLoaded();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        unsetOnProfileLoadedListener();
                    }
                });
    }

    private boolean unsetOnProfileLoadedListener() {
        if (this.profileListener != null) {
            UserProfile.unsetOnProfileLoadedListener(conversation.getPeerUserId(), this.profileListener);
            this.profileListener = null;
            return true;
        }
        return false;
    }

    private void setOnBookLoadedListener() {

        this.bookListener = Book.setOnBookLoadedListener(
                conversation.getBookId(),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (!unsetOnBookLoadedListener()) {
                            return;
                        }

                        Book.Data data = dataSnapshot.getValue(Book.Data.class);
                        if (data != null) {
                            book = new Book(conversation.getBookId(), data);
                            if (peer != null) {
                                afterAllDataLoaded();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        unsetOnProfileLoadedListener();
                    }
                });
    }

    private boolean unsetOnBookLoadedListener() {
        if (this.bookListener != null) {
            Book.unsetOnBookLoadedListener(conversation.getBookId(), this.bookListener);
            this.bookListener = null;
            return true;
        }
        return false;
    }
}
