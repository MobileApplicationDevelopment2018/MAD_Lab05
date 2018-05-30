package it.polito.mad.mad2018.library;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.chat.SingleChatActivity;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.LocalUserProfile;
import it.polito.mad.mad2018.data.OwnedBook;
import it.polito.mad.mad2018.data.UserProfile;
import it.polito.mad.mad2018.profile.ShowProfileFragment;
import it.polito.mad.mad2018.utils.FragmentDialog;
import it.polito.mad.mad2018.utils.GlideApp;
import it.polito.mad.mad2018.utils.GlideRequest;
import it.polito.mad.mad2018.utils.Utilities;
import me.gujun.android.taggroup.TagGroup;
import rm.com.longpresspopup.LongPressPopup;
import rm.com.longpresspopup.LongPressPopupBuilder;
import rm.com.longpresspopup.PopupInflaterListener;
import rm.com.longpresspopup.PopupStateListener;

public class BookInfoFragment extends FragmentDialog<BookInfoFragment.DialogID>
        implements PopupInflaterListener, PopupStateListener, Book.OnBookFlagsUpdatedListener {

    public final static String BOOK_SHOW_OWNER_KEY = "book_show_owner_key";
    public final static String BOOK_DELETABLE_KEY = "book_deletable_key";

    private Book book;
    private UserProfile owner;

    private ValueEventListener profileListener;
    private LongPressPopup popup;
    private ImageView popupImage;

    private MenuItem showProfileMenuItem, showChatMenuItem;

    public BookInfoFragment() { /* Required empty public constructor */ }

    public static BookInfoFragment newInstance(@NonNull Book book, boolean showOwner, boolean deletable) {
        showOwner = showOwner && !book.isOwnedBook();
        deletable = deletable && book.isDeletable();

        BookInfoFragment fragment = new BookInfoFragment();
        Bundle args = new Bundle();
        args.putSerializable(Book.BOOK_KEY, book);
        args.putBoolean(BOOK_SHOW_OWNER_KEY, showOwner);
        args.putBoolean(BOOK_DELETABLE_KEY, deletable);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {

        setHasOptionsMenu(true);
        assert getActivity() != null;
        getActivity().setTitle(R.string.bookinfo_title);

        assert getArguments() != null;
        book = (Book) getArguments().getSerializable(Book.BOOK_KEY);

        assert book != null;

        if (savedInstanceState != null) {
            owner = (UserProfile) savedInstanceState.getSerializable(UserProfile.PROFILE_INFO_KEY);
        }

        View view = inflater.inflate(R.layout.fragment_book_info, container, false);
        fillViews(view);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_book_info, menu);

        assert getArguments() != null;
        showProfileMenuItem = menu.findItem(R.id.fbi_show_profile);
        showChatMenuItem = menu.findItem(R.id.fbi_show_chat);
        MenuItem deleteBookItem = menu.findItem(R.id.fbi_delete_book);

        showProfileMenuItem.setVisible(owner != null && getArguments().getBoolean(BOOK_SHOW_OWNER_KEY));
        showChatMenuItem.setVisible(owner != null && getArguments().getBoolean(BOOK_SHOW_OWNER_KEY));
        deleteBookItem.setVisible(getArguments().getBoolean(BOOK_DELETABLE_KEY));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.fbi_show_profile:
                assert getFragmentManager() != null;
                getFragmentManager().beginTransaction()
                        .replace(R.id.bi_main_fragment, ShowProfileFragment.newInstance(owner, false))
                        .addToBackStack(null)
                        .commit();
                return true;

            case R.id.fbi_show_chat:
                Intent intent = new Intent(getActivity(), SingleChatActivity.class);
                intent.putExtra(UserProfile.PROFILE_INFO_KEY, owner);
                intent.putExtra(Book.BOOK_KEY, book);
                startActivity(intent);
                return true;

            case R.id.fbi_delete_book:
                this.openDialog(DialogID.DIALOG_DELETE, true);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        setOnProfileLoadedListener();

        this.onBookFlagsUpdated();
        book.startOnBookFlagsUpdatedListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        unsetOnProfileLoadedListener();
        book.stopOnBookFlagsUpdatedListener();
        if (popup != null) {
            popup.unregister();
            popup.dismissNow();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(UserProfile.PROFILE_INFO_KEY, owner);
    }

    private void fillViews(View view) {
        assert this.getActivity() != null;
        assert this.getActivity().getResources() != null;

        String unknown = getString(R.string.unknown);

        TextView isbn = view.findViewById(R.id.fbi_book_isbn);
        TextView title = view.findViewById(R.id.fbi_book_title);
        TextView authors = view.findViewById(R.id.fbi_book_authors);
        TextView publisher = view.findViewById(R.id.fbi_book_publisher);
        TextView editionYear = view.findViewById(R.id.fbi_book_edition_year);
        TextView language = view.findViewById(R.id.fbi_book_language);
        TextView conditions = view.findViewById(R.id.fbi_book_conditions);

        ImageView bookThumbnail = view.findViewById(R.id.fbi_book_picture);
        TagGroup tagGroup = view.findViewById(R.id.fbi_book_tags);

        isbn.setText(book.getIsbn());
        title.setText(book.getTitle());
        authors.setText(book.getAuthors(", "));
        publisher.setText(Utilities.isNullOrWhitespace(book.getPublisher()) ? unknown : book.getPublisher());
        editionYear.setText(String.valueOf(book.getYear()));
        language.setText(Utilities.isNullOrWhitespace(book.getLanguage()) ? unknown : book.getLanguage());
        conditions.setText(book.getConditions());

        List<String> tags = book.getTags();
        if (tags.size() == 0) {
            tags.add(getString(R.string.no_tags_found));
        }
        tagGroup.setTags(tags);

        GlideApp.with(view.getContext())
                .load(book.getBookThumbnailReferenceOrNull())
                .placeholder(R.drawable.ic_default_book_preview)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(bookThumbnail);

        if (book.hasImage()) {
            popup = new LongPressPopupBuilder(getContext())
                    .setTarget(bookThumbnail)
                    .setPopupView(R.layout.popup_view_image, this)
                    .setPopupListener(this)
                    .setAnimationType(LongPressPopup.ANIMATION_TYPE_FROM_CENTER)
                    .build();

            popup.register();
        }
    }

    private void setOnProfileLoadedListener() {
        assert getArguments() != null;
        boolean showOwner = getArguments().getBoolean(BOOK_SHOW_OWNER_KEY);
        if (owner != null || !showOwner)
            return;

        this.profileListener = UserProfile.setOnProfileLoadedListener(
                this.book.getOwnerId(),
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (!unsetOnProfileLoadedListener()) {
                            return;
                        }

                        UserProfile.Data data = dataSnapshot.getValue(UserProfile.Data.class);
                        if (data != null) {
                            owner = new UserProfile(book.getOwnerId(), data);
                            if (showProfileMenuItem != null && showChatMenuItem != null) {
                                showProfileMenuItem.setVisible(getArguments().getBoolean(BOOK_SHOW_OWNER_KEY));
                                showChatMenuItem.setVisible(getArguments().getBoolean(BOOK_SHOW_OWNER_KEY));
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
            UserProfile.unsetOnProfileLoadedListener(book.getOwnerId(), this.profileListener);
            this.profileListener = null;
            return true;
        }
        return false;
    }

    private void deleteBook() {
        OwnedBook book = new OwnedBook(this.book);
        book.deleteFromAlgolia((json, e) -> {
            if (e != null) {
                Toast.makeText(getContext(), R.string.error_occurred, Toast.LENGTH_LONG).show();
                return;
            }

            book.deleteFromFirebase(LocalUserProfile.getInstance());
            assert getActivity() != null;
            getActivity().onBackPressed();
        });
    }

    @Override
    protected void openDialog(@NonNull BookInfoFragment.DialogID dialogId, boolean dialogPersist) {
        super.openDialog(dialogId, dialogPersist);

        Dialog dialogInstance = null;
        switch (dialogId) {
            case DIALOG_DELETE:
                dialogInstance = new AlertDialog.Builder(getContext())
                        .setMessage(R.string.confirm_delete)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> deleteBook())
                        .setNegativeButton(android.R.string.no, null)
                        .show();
        }

        if (dialogInstance != null) {
            setDialogInstance(dialogInstance);
        }
    }

    @Override
    public void onViewInflated(@Nullable String popupTag, View root) {
        popupImage = root.findViewById(R.id.pvi_image);
    }

    @Override
    public void onPopupShow(@Nullable String popupTag) {
        GlideRequest<Drawable> thumbnail = GlideApp
                .with(this)
                .load(book.getBookThumbnailReferenceOrNull())
                .fitCenter();

        assert getContext() != null;
        GlideApp.with(getContext())
                .load(book.getBookPictureReferenceOrNull())
                .thumbnail(thumbnail)
                .transition(DrawableTransitionOptions.withCrossFade())
                .fitCenter()
                .into(popupImage);
    }

    @Override
    public void onPopupDismiss(@Nullable String popupTag) {
    }

    @Override
    public void onBookFlagsUpdated() {
        assert getView() != null;

        View circle = getView().findViewById(R.id.fbi_color_circle);
        TextView availability = getView().findViewById(R.id.fbi_book_availability);

        availability.setText(
                (book.isAvailable()) ?
                        R.string.available :
                        R.string.not_available);

        availability.setTextColor(
                (book.isAvailable()) ?
                        this.getResources().getColor(R.color.colorPrimary) :
                        this.getResources().getColor(R.color.colorRed)
        );
        circle.getBackground().setColorFilter(
                (book.isAvailable()) ?
                        this.getResources().getColor(R.color.colorPrimary) :
                        this.getResources().getColor(R.color.colorRed),
                PorterDuff.Mode.SRC_ATOP);
    }

    public enum DialogID {
        DIALOG_DELETE,
    }
}
