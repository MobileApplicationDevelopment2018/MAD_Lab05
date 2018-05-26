package it.polito.mad.mad2018.library;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.UserProfile;
import it.polito.mad.mad2018.profile.ShowProfileFragment;

public class BookInfoActivity extends AppCompatActivity
        implements ShowProfileFragment.OnShowOwnedBooksClickListener {

    private Book book;
    private String bookId;
    private boolean bookShowOwner;
    private boolean bookDeletable;

    private ValueEventListener bookListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_info);

        // Set the toolbar
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (savedInstanceState != null) {
            this.book = (Book) savedInstanceState.getSerializable(Book.BOOK_KEY);
            this.bookId = savedInstanceState.getString(Book.BOOK_ID_KEY);
            this.bookShowOwner = savedInstanceState.getBoolean(BookInfoFragment.BOOK_SHOW_OWNER_KEY);
            this.bookDeletable = savedInstanceState.getBoolean(BookInfoFragment.BOOK_DELETABLE_KEY);
        } else {
            this.book = (Book) this.getIntent().getSerializableExtra(Book.BOOK_KEY);
            this.bookId = book == null ? this.getIntent().getStringExtra(Book.BOOK_ID_KEY) : book.getBookId();
            this.bookShowOwner = this.getIntent().getBooleanExtra(BookInfoFragment.BOOK_SHOW_OWNER_KEY, true);
            this.bookDeletable = this.getIntent().getBooleanExtra(BookInfoFragment.BOOK_DELETABLE_KEY, false);

            if (book != null) {
                showBookInfoFragment();
            } else {
                setOnBookLoadedListener();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(Book.BOOK_KEY, book);
        outState.putSerializable(Book.BOOK_ID_KEY, bookId);
        outState.putBoolean(BookInfoFragment.BOOK_SHOW_OWNER_KEY, bookShowOwner);
        outState.putBoolean(BookInfoFragment.BOOK_DELETABLE_KEY, bookDeletable);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (book == null) {
            setOnBookLoadedListener();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unsetOnBookLoadedListener();
    }

    private void showBookInfoFragment() {
        findViewById(R.id.bi_loading).setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.bi_main_fragment, BookInfoFragment.newInstance(book, bookShowOwner, bookDeletable))
                .commit();
    }

    private void setOnBookLoadedListener() {

        findViewById(R.id.bi_loading).setVisibility(View.VISIBLE);
        this.bookListener = Book.setOnBookLoadedListener(bookId, new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!unsetOnBookLoadedListener()) {
                    return;
                }

                Book.Data data = dataSnapshot.getValue(Book.Data.class);
                if (data != null) {
                    book = new Book(bookId, data);
                    showBookInfoFragment();
                } else {
                    Toast.makeText(BookInfoActivity.this,
                            R.string.error_occurred, Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                unsetOnBookLoadedListener();
                Toast.makeText(BookInfoActivity.this,
                        R.string.error_occurred, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private boolean unsetOnBookLoadedListener() {
        if (this.bookListener != null) {
            Book.unsetOnBookLoadedListener(bookId, this.bookListener);
            this.bookListener = null;
            return true;
        }
        return false;
    }

    @Override
    public void OnShowOwnedBooksClick(@NonNull UserProfile profile) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.bi_main_fragment, MyBooksFragment.newInstance(profile))
                .addToBackStack(null)
                .commit();
    }
}
