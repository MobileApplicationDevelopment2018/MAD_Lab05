package it.polito.mad.mad2018.library;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.api.services.books.model.Volume;
import com.google.api.services.books.model.Volumes;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import it.polito.mad.mad2018.BuildConfig;
import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.barcodereader.BarcodeCaptureActivity;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.LocalUserProfile;
import it.polito.mad.mad2018.data.OwnedBook;
import it.polito.mad.mad2018.utils.FileUtilities;
import it.polito.mad.mad2018.utils.FragmentDialog;
import it.polito.mad.mad2018.utils.IsbnQuery;
import it.polito.mad.mad2018.utils.PictureUtilities;
import it.polito.mad.mad2018.utils.Utilities;
import me.gujun.android.taggroup.TagGroup;

import static android.app.Activity.RESULT_OK;

public class AddBookFragment extends FragmentDialog<AddBookFragment.DialogID>
        implements IsbnQuery.TaskListener {

    private static final int CAMERA = 2;
    private static final int GALLERY = 3;
    private static final int PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 4;
    private static final int PERMISSIONS_REQUEST_CAMERA = 5;
    private static final int RC_BARCODE_CAPTURE = 9001;

    private static final String TO_BE_DELETED_KEY = "to_be_deleted";

    private static final String IMAGE_PATH_TMP = "book_picture_tmp";

    private IsbnQuery isbnQuery;

    private EditText isbnEdit, titleEt, publisherEt, languageEt;
    private Spinner yearSpinner, conditionSpinner;
    private Button scanBarcodeBtn, addBookBtn, resetBtn, autocompleteBtn;
    private TagGroup tagGroup, authorEtGroup;

    private OwnedBook book;
    private boolean fileToBeDeleted;

    private OnBookAddedListener onBookAddedListener;

    public AddBookFragment() { /* Required empty public constructor */ }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_add_book, container, false);
        findViews(view);

        // Buttons watchers
        scanBarcodeBtn.setOnClickListener(v -> {
            // launch barcode activity.
            Intent intent = new Intent(this.getContext(), BarcodeCaptureActivity.class);
            startActivityForResult(intent, RC_BARCODE_CAPTURE);
        });

        assert getContext() != null;
        autocompleteBtn.setOnClickListener(v -> {
            isbnQuery = new IsbnQuery(getContext(), this);
            isbnQuery.execute(isbnEdit.getText().toString());
        });

        resetBtn.setOnClickListener(v -> clearViews(true));
        addBookBtn.setOnClickListener(v -> startBookUpload());

        // Tag watcher
        tagGroup.setOnClickListener(v -> ((TagGroup) v.findViewById(R.id.tag_group)).submitTag());

        // Fields watchers
        isbnEdit.requestFocus();
        isbnEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

                boolean isbnValid = Utilities.validateIsbn(isbnEdit.getText().toString());
                autocompleteBtn.setEnabled(isbnValid);
                if (!isbnValid && isbnEdit.getText().length() != 0) {
                    isbnEdit.setError(getString(R.string.add_book_invalid_isbn));
                }
            }
        });

        fillSpinnerYear(yearSpinner);
        fillSpinnerConditions(conditionSpinner);

        book = null;
        fileToBeDeleted = false;
        if (savedInstanceState != null) {
            book = (OwnedBook) savedInstanceState.getSerializable(Book.BOOK_KEY);
            fileToBeDeleted = savedInstanceState.getBoolean(TO_BE_DELETED_KEY, false);
        }

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (getActivity() instanceof AddBookFragment.OnBookAddedListener) {
            this.onBookAddedListener = (AddBookFragment.OnBookAddedListener) getActivity();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.onBookAddedListener = null;
    }

    @Override
    public void onTaskStarted() {
        openDialog(DialogID.DIALOG_LOADING, true);
    }

    @Override
    public void onTaskFinished(Volumes volumes) {
        this.closeDialog();

        if (volumes == null) {
            Toast.makeText(getContext(), getResources().getString(R.string.add_book_query_failed), Toast.LENGTH_LONG).show();
        } else if (volumes.getTotalItems() == 0 || volumes.getItems() == null) {
            Toast.makeText(getContext(), getResources().getString(R.string.add_book_query_no_results), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), R.string.add_book_query_ok, Toast.LENGTH_SHORT).show();

            final Volume.VolumeInfo volumeInfo = volumes.getItems().get(0).getVolumeInfo();
            OwnedBook book = new OwnedBook(isbnEdit.getText().toString(), volumeInfo);
            fillViews(book);
            hideSoftKeyboard();
        }
    }

    @Override
    public void onTaskCancelled(String msg) {
        Toast.makeText(getContext(), getString(R.string.add_book_query_failed) + " " + msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(Book.BOOK_KEY, book);
        outState.putBoolean(TO_BE_DELETED_KEY, fileToBeDeleted);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case RC_BARCODE_CAPTURE:
                if (resultCode == CommonStatusCodes.SUCCESS) {
                    if (data != null) {

                        Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                        if (!barcode.displayValue.equals(isbnEdit.getText().toString())) {
                            clearViews(false);
                        }

                        assert getContext() != null;
                        if (Utilities.validateIsbn(barcode.displayValue)) {
                            isbnEdit.setText(barcode.displayValue);
                            isbnQuery = new IsbnQuery(getContext(), this);
                            isbnQuery.execute(isbnEdit.getText().toString());
                        }
                    }
                } else {
                    Toast.makeText(this.getContext(), String.format(getString(R.string.barcode_error),
                            CommonStatusCodes.getStatusCodeString(resultCode)), Toast.LENGTH_LONG)
                            .show();
                }
                break;

            case CAMERA:

                if (resultCode == RESULT_OK) {
                    assert getActivity() != null;
                    File imageFileCamera = new File(getActivity().getApplicationContext()
                            .getExternalFilesDir(Environment.DIRECTORY_PICTURES), IMAGE_PATH_TMP);

                    if (imageFileCamera.exists()) {
                        fileToBeDeleted = true;
                        processPicture(imageFileCamera.getPath());
                    }
                } else {
                    Toast.makeText(getContext(), R.string.operation_aborted, Toast.LENGTH_LONG).show();
                }
                break;


            case GALLERY:
                String imagePath;

                assert getContext() != null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null &&
                        (imagePath = FileUtilities.getRealPathFromUri(getContext(), data.getData())) != null) {

                    processPicture(imagePath);
                } else {
                    Toast.makeText(getContext(), R.string.operation_aborted, Toast.LENGTH_LONG).show();
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {

            case PERMISSIONS_REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    galleryLoadPicture();
                }

                return;
            }

            case PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraTakePicture();
                }

                return;
            }

            default:
                break;
        }
    }

    private void startBookUpload() {

        authorEtGroup.submitTag();
        tagGroup.submitTag();

        String isbn = isbnEdit.getText().toString();
        String title = titleEt.getText().toString();
        String[] authors = authorEtGroup.getTags();
        String language = languageEt.getText().toString();
        String publisher = publisherEt.getText().toString();
        int year = Integer.parseInt(yearSpinner.getSelectedItem().toString());

        if (!checkMandatoryFieldsInput(isbn, title, authors))
            return;

        Book.BookConditions condition = (Book.BookConditions) conditionSpinner.getSelectedItem();
        List<String> tags = Arrays.asList(tagGroup.getTags());
        book = new OwnedBook(isbn, title, Arrays.asList(authors), language, publisher, year,
                condition, tags);

        openDialog(DialogID.DIALOG_ADD_PICTURE, true);
    }

    private void processPicture(@NonNull String imagePath) {
        new PictureUtilities.CompressImageAsync(
                imagePath, OwnedBook.BOOK_PICTURE_SIZE, OwnedBook.BOOK_THUMBNAIL_SIZE,
                OwnedBook.BOOK_PICTURE_QUALITY, picture -> {

            if (fileToBeDeleted) {
                assert getActivity() != null;
                File tmpImageFile = new File(getActivity().getApplicationContext()
                        .getExternalFilesDir(Environment.DIRECTORY_PICTURES), IMAGE_PATH_TMP);
                tmpImageFile.deleteOnExit();
                fileToBeDeleted = false;
            }

            if (picture == null) {
                Toast.makeText(getContext(), getResources().getString(R.string.add_book_error),
                        Toast.LENGTH_LONG).show();
                return;
            }

            uploadBook(picture);
        }).execute();
    }

    private void uploadBook() {
        uploadBook(null);
    }

    private void uploadBook(PictureUtilities.CompressedImage picture) {
        openDialog(DialogID.DIALOG_SAVING, true);

        book.setHasImage(picture != null);

        OnSuccessListener<Object> onSuccess = v -> {
            book.saveToFirebase(LocalUserProfile.getInstance());
            Toast.makeText(getContext(), getResources().getString(R.string.add_book_saved), Toast.LENGTH_LONG).show();
            clearViews(true);

            if (onBookAddedListener != null) {
                onBookAddedListener.OnBookAdded();
            }
        };
        OnFailureListener onFailure = v -> {
            this.closeDialog();
            Toast.makeText(getContext(), getResources().getString(R.string.add_book_error),
                    Toast.LENGTH_LONG).show();
        };

        book.saveToAlgolia(LocalUserProfile.getInstance(), (obj, e) -> {
            if (e != null) {
                onFailure.onFailure(e);
                return;
            }

            if (picture != null) {
                book.savePictureToFirebase(LocalUserProfile.getInstance(), picture.getPicture(), picture.getThumbnail())
                        .addOnCompleteListener(v -> closeDialog())
                        .addOnSuccessListener(onSuccess)
                        .addOnFailureListener(onFailure)
                        .addOnFailureListener(v -> book.deleteFromAlgolia(null));
            } else {
                onSuccess.onSuccess(null);
                this.closeDialog();
            }
        });
    }

    private void findViews(View view) {
        // Buttons
        scanBarcodeBtn = view.findViewById(R.id.ab_barcode_scan);
        autocompleteBtn = view.findViewById(R.id.ab_autocomplete);
        addBookBtn = view.findViewById(R.id.ab_add_book);
        resetBtn = view.findViewById(R.id.ab_clear_fields);

        // Isbn view
        isbnEdit = view.findViewById(R.id.ab_isbn_edit);

        // User-filled views
        titleEt = view.findViewById(R.id.ab_title_edit);
        authorEtGroup = view.findViewById(R.id.tag_group_authors);
        publisherEt = view.findViewById(R.id.ab_publisher_edit);
        languageEt = view.findViewById(R.id.ab_language_edit);
        yearSpinner = view.findViewById(R.id.ab_edition_year_edit);
        conditionSpinner = view.findViewById(R.id.ab_conditions);

        // Tags
        tagGroup = view.findViewById(R.id.tag_group);
    }

    private void fillViews(@NonNull Book book) {
        clearViews(false);

        titleEt.setText(book.getTitle());
        publisherEt.setText(book.getPublisher());
        authorEtGroup.setTags(book.getAuthors());

        int selection = book.getYear() - Book.INITIAL_YEAR;
        if (selection < 0) {
            selection = 0;
        }
        yearSpinner.setSelection(selection);
        languageEt.setText(book.getLanguage());
        tagGroup.setTags(book.getTags());
    }

    private void clearViews(boolean clearIsbn) {
        if (clearIsbn) {
            isbnEdit.setText(null);
        }

        titleEt.setText(null);
        authorEtGroup.setTags(new LinkedList<>());
        publisherEt.setText(null);
        yearSpinner.setSelection(0);
        languageEt.setText(null);
        tagGroup.setTags(new LinkedList<>());

        isbnEdit.requestFocus();
    }

    private void fillSpinnerYear(Spinner spinYear) {
        ArrayList<String> years = new ArrayList<>();
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);

        for (int i = Book.INITIAL_YEAR; i <= thisYear; i++)
            years.add(Integer.toString(i));

        assert getActivity() != null;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_spinner_item, years);

        spinYear.setAdapter(adapter);
        spinYear.setSelection(adapter.getCount() - 1);
    }

    private void fillSpinnerConditions(Spinner spinConditions) {
        assert getActivity() != null;
        ArrayAdapter<Book.BookConditions> adapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_spinner_item, Book.BookConditions.values());

        spinConditions.setAdapter(adapter);
        spinConditions.setSelection(adapter.getCount() - 2);
    }

    private boolean checkMandatoryFieldsInput(String isbn, String title, String[] authors) {

        boolean ok = true;
        if (Utilities.isNullOrWhitespace(title) || authors.length == 0) {
            ok = false;
        }

        for (String author : authors) {
            if (Utilities.isNullOrWhitespace(author)) {
                ok = false;
                break;
            }
        }

        if (!ok) {
            Toast.makeText(getContext(), getResources().getString(R.string.ab_field_must_not_be_empty), Toast.LENGTH_LONG).show();
        }
        return ok && isbn.length() == 0 || Utilities.validateIsbn(isbn);
    }

    private void cameraTakePicture() {
        assert getActivity() != null;
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getActivity().getPackageManager()) != null) {

            File imageFile = new File(getActivity().getApplicationContext()
                    .getExternalFilesDir(Environment.DIRECTORY_PICTURES), IMAGE_PATH_TMP);
            Uri imageUri = FileProvider.getUriForFile(getActivity(),
                    BuildConfig.APPLICATION_ID.concat(".fileprovider"), imageFile);

            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(cameraIntent, CAMERA);
        }
    }

    private void galleryLoadPicture() {
        assert getActivity() != null;
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        if (galleryIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(galleryIntent, GALLERY);
        }
    }

    private void hideSoftKeyboard() {
        assert getActivity() != null;
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager inputMethodManager =
                    (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void openDialog(@NonNull DialogID dialogId, boolean dialogPersist) {
        super.openDialog(dialogId, dialogPersist);

        Dialog dialogInstance = null;
        switch (dialogId) {
            case DIALOG_LOADING:
                dialogInstance = ProgressDialog.show(getContext(),
                        getResources().getString(R.string.add_book_isbn_loading_title),
                        getResources().getString(R.string.add_book_isbn_loading_message), true);
                break;

            case DIALOG_SAVING:
                dialogInstance = ProgressDialog.show(getContext(), null,
                        getString(R.string.saving_data), true);
                break;

            case DIALOG_ADD_PICTURE:
                dialogInstance = new AlertDialog.Builder(getContext())
                        .setMessage(R.string.would_take_picture)
                        .setPositiveButton(R.string.take_picture_camera, (dialog, which) -> {
                            assert getActivity() != null;
                            if (ContextCompat.checkSelfPermission(getActivity(),
                                    android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(
                                        new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
                            } else {
                                cameraTakePicture();
                            }
                        })
                        .setNegativeButton(R.string.take_picture_gallery, (dialog, which) -> {
                            assert getActivity() != null;
                            if (ContextCompat.checkSelfPermission(getActivity(),
                                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(
                                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_EXTERNAL_STORAGE);
                            } else {
                                galleryLoadPicture();
                            }
                        })
                        .setNeutralButton(R.string.no, (dialog, which) -> uploadBook())
                        .setOnCancelListener(dialog -> Toast.makeText(getContext(),
                                getResources().getString(R.string.operation_aborted), Toast.LENGTH_LONG).show())
                        .show();
        }

        if (dialogInstance != null) {
            setDialogInstance(dialogInstance);
        }

    }

    public enum DialogID {
        DIALOG_LOADING,
        DIALOG_SAVING,
        DIALOG_ADD_PICTURE,
    }

    public interface OnBookAddedListener {
        void OnBookAdded();
    }
}
