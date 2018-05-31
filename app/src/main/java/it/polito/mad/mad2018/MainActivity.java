package it.polito.mad.mad2018;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Arrays;

import it.polito.mad.mad2018.chat.ChatIDService;
import it.polito.mad.mad2018.chat.MyChatsFragment;
import it.polito.mad.mad2018.data.LocalUserProfile;
import it.polito.mad.mad2018.data.UserProfile;
import it.polito.mad.mad2018.explore.ExploreFragment;
import it.polito.mad.mad2018.library.LibraryFragment;
import it.polito.mad.mad2018.profile.EditProfileActivity;
import it.polito.mad.mad2018.profile.ShowProfileFragment;
import it.polito.mad.mad2018.utils.AppCompatActivityDialog;
import it.polito.mad.mad2018.utils.GlideApp;
import it.polito.mad.mad2018.utils.GlideRequest;
import it.polito.mad.mad2018.utils.Utilities;

public class MainActivity extends AppCompatActivityDialog<MainActivity.DialogID>
        implements NavigationView.OnNavigationItemSelectedListener,
        ShowProfileFragment.OnShowOwnedBooksClickListener {

    private static final int RC_SIGN_IN = 1;
    private static final int RC_EDIT_PROFILE = 5;
    private static final int RC_EDIT_PROFILE_WELCOME = 6;

    private static final String PREFERENCES_FIRST_TIME = "preferences_first_time";

    private FirebaseAuth firebaseAuth;
    private ValueEventListener profileListener;

    private boolean signInActivityShown;
    private boolean splashScreenShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        firebaseAuth = FirebaseAuth.getInstance();
        signInActivityShown = splashScreenShown = false;

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if (savedInstanceState == null && !Utilities.isNetworkConnected(this)) {
            openDialog(DialogID.DIALOG_NO_CONNECTION, true);
            return;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean(PREFERENCES_FIRST_TIME, true)) {
            preferences.edit().putBoolean(PREFERENCES_FIRST_TIME, false).apply();
            Intent onboardingIntent = new Intent(getApplicationContext(), OnboardingActivity.class);
            startActivity(onboardingIntent);
            splashScreenShown = true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (splashScreenShown) {
            splashScreenShown = false;
            return;
        }

        if (firebaseAuth.getCurrentUser() != null) {
            if (LocalUserProfile.getInstance() == null) {
                setOnProfileLoadedListener();
            } else {
                findViewById(R.id.main_loading).setVisibility(View.GONE);
                updateNavigationView();
                if (getCurrentFragment() == null) {
                    showDefaultFragment();
                }
            }
        } else {
            signIn();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        this.unsetOnProfileLoadedListener();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof ExploreFragment) {
                if (((ExploreFragment) fragment).getCurrentDisplayedFragment() == 0) {
                    super.onBackPressed();
                } else {
                    ((ExploreFragment) fragment).onBackPressed();
                }
            } else {
                showDefaultFragment();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();

        switch (id) {
            case R.id.nav_explore:
                this.replaceFragment(ExploreFragment.newInstance());
                break;

            case R.id.nav_library:
                this.replaceFragment(LibraryFragment.newInstance());
                break;

            case R.id.nav_profile:
                this.replaceFragment(ShowProfileFragment.newInstance(LocalUserProfile.getInstance(), true));
                break;

            case R.id.nav_chat:
                this.replaceFragment(MyChatsFragment.newInstance());
                break;

            case R.id.nav_sign_out:
                signOut();
                break;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sp_edit_profile:
                this.showEditProfileActivity(RC_EDIT_PROFILE);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RC_SIGN_IN:
                IdpResponse response = IdpResponse.fromResultIntent(data);
                signInActivityShown = false;

                // Successfully signed in
                if (resultCode == RESULT_OK && firebaseAuth.getCurrentUser() != null) {
                    setOnProfileLoadedListener();
                    return;
                }

                if (response != null) {
                    showToast(R.string.sign_in_unknown_error);
                }

                finish();
                return;

            case RC_EDIT_PROFILE:
                if (resultCode == RESULT_OK) {
                    LocalUserProfile.setInstance((LocalUserProfile) data.getSerializableExtra(UserProfile.PROFILE_INFO_KEY));
                    LocalUserProfile.getInstance().postCommit();
                    updateNavigationView(); // Need to update the drawer information
                    this.replaceFragment(ShowProfileFragment.newInstance(LocalUserProfile.getInstance(), true), true);
                }
                break;

            case RC_EDIT_PROFILE_WELCOME:
                if (resultCode == RESULT_OK) {
                    LocalUserProfile.setInstance((LocalUserProfile) data.getSerializableExtra(UserProfile.PROFILE_INFO_KEY));
                    LocalUserProfile.getInstance().postCommit();
                    updateNavigationView(); // Need to update the drawer information
                }
                break;

            default:
                break;
        }
    }

    private void updateNavigationView() {

        if (this.isDestroyed()) {
            return;
        }

        NavigationView drawer = findViewById(R.id.nav_view);
        View header = drawer.getHeaderView(0);

        ImageView profilePicture = header.findViewById(R.id.nh_profile_picture);
        TextView username = header.findViewById(R.id.nh_username);
        TextView email = header.findViewById(R.id.nh_email);

        UserProfile localProfile = LocalUserProfile.getInstance();

        username.setText(localProfile.getUsername());
        email.setText(localProfile.getEmail());

        GlideRequest<Drawable> thumbnail = GlideApp
                .with(this)
                .load(localProfile.getProfilePictureThumbnail())
                .apply(RequestOptions.circleCropTransform());

        GlideApp.with(this)
                .load(localProfile.getProfilePictureReference())
                .signature(new ObjectKey(localProfile.getProfilePictureLastModified()))
                .thumbnail(thumbnail)
                .fallback(R.mipmap.ic_drawer_picture_round)
                .transition(DrawableTransitionOptions.withCrossFade())
                .apply(RequestOptions.circleCropTransform())
                .into(profilePicture);

        profilePicture
                .setOnClickListener(v -> {
                    replaceFragment(ShowProfileFragment.newInstance(localProfile, true));
                    DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
                    drawerLayout.closeDrawer(GravityCompat.START);
                    drawer.getMenu().findItem(R.id.nav_profile).setChecked(true);
                });
    }

    private void signIn() {

        if (signInActivityShown) {
            return;
        }

        if (!Utilities.isNetworkConnected(this)) {
            openDialog(DialogID.DIALOG_NO_CONNECTION, true);
            return;
        }

        signInActivityShown = true;
        startActivityForResult(
                AuthUI.getInstance().createSignInIntentBuilder()
                        .setAvailableProviders(Arrays.asList(
                                new AuthUI.IdpConfig.EmailBuilder().build(),
                                new AuthUI.IdpConfig.GoogleBuilder().build(),
                                new AuthUI.IdpConfig.FacebookBuilder().build()))
                        .build(),
                RC_SIGN_IN);
    }

    private void signOut() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnSuccessListener(this, t -> onSignOut())
                .addOnFailureListener(this, t -> showToast(R.string.sign_out_failed));
    }

    private void onSignOut() {
        LocalUserProfile.setInstance(null);
        removeAllFragments();
        ChatIDService.deleteToken();
        signIn();
    }

    private void showToast(@StringRes int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showToast(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setOnProfileLoadedListener() {
        this.openDialog(DialogID.DIALOG_LOADING, false);

        this.profileListener = LocalUserProfile.setOnProfileLoadedListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!unsetOnProfileLoadedListener()) {
                    return;
                }
                closeDialog();
                showDefaultFragment();

                UserProfile.Data data = dataSnapshot.getValue(UserProfile.Data.class);
                if (data == null) {
                    completeRegistration();
                } else {
                    LocalUserProfile.setInstance(new LocalUserProfile(data));
                    updateNavigationView();
                    showToast(getString(R.string.sign_in_welcome_back) + " " + LocalUserProfile.getInstance().getUsername());
                }
                ChatIDService.uploadToken(LocalUserProfile.getInstance(), FirebaseInstanceId.getInstance().getToken());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                if (!unsetOnProfileLoadedListener()) {
                    return;
                }
                openDialog(DialogID.DIALOG_ERROR_RETRIEVE_DIALOG, true);
            }
        });
    }

    private boolean unsetOnProfileLoadedListener() {
        if (this.profileListener != null) {
            LocalUserProfile.unsetOnProfileLoadedListener(this.profileListener);
            this.profileListener = null;
            return true;
        }
        return false;
    }

    private void completeRegistration() {

        assert firebaseAuth.getCurrentUser() != null;
        LocalUserProfile.setInstance(new LocalUserProfile(firebaseAuth.getCurrentUser()));
        LocalUserProfile.getInstance().saveToFirebase();

        String message = getString(R.string.sign_in_welcome) + " " + LocalUserProfile.getInstance().getUsername();
        Snackbar.make(findViewById(R.id.main_coordinator_layout), message, Snackbar.LENGTH_LONG)
                .setAction(R.string.editprofile_title, v -> showEditProfileActivity(RC_EDIT_PROFILE_WELCOME))
                .show();

        updateNavigationView();
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.content_frame);
    }

    private void replaceFragment(@NonNull Fragment instance) {
        replaceFragment(instance, false);
    }

    private void replaceFragment(@NonNull Fragment newInstance, boolean force) {

        FragmentManager fragmentManager = getSupportFragmentManager();

        Fragment oldInstance = fragmentManager.findFragmentByTag(newInstance.getClass().getSimpleName());
        Fragment currentInstance = getCurrentFragment();

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        if (currentInstance != null && !currentInstance.isDetached() && !currentInstance.getClass().equals(newInstance.getClass())) {
            fragmentTransaction.detach(currentInstance);
        }

        if (oldInstance == null || force) {
            if (oldInstance != null) {
                fragmentTransaction.remove(oldInstance);
            }
            fragmentTransaction.add(R.id.content_frame, newInstance, newInstance.getClass().getSimpleName());
        } else if (currentInstance == null || currentInstance.isDetached() || !currentInstance.getClass().equals(newInstance.getClass())) {
            fragmentTransaction.attach(oldInstance);
        }

        fragmentTransaction.commit();

        hideSoftKeyboard();
    }

    private void removeAllFragments() {

        Class[] tags = {ExploreFragment.class, LibraryFragment.class,
                MyChatsFragment.class, ShowProfileFragment.class};

        findViewById(R.id.main_loading).setVisibility(View.VISIBLE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (Class tag : tags) {
            Fragment instance = getSupportFragmentManager().findFragmentByTag(tag.getSimpleName());
            if (instance != null) {
                transaction.remove(instance);
            }
        }
        transaction.commit();
    }

    private void showDefaultFragment() {
        findViewById(R.id.main_loading).setVisibility(View.GONE);
        NavigationView drawer = findViewById(R.id.nav_view);
        drawer.getMenu().findItem(R.id.nav_explore).setChecked(true);
        replaceFragment(ExploreFragment.newInstance());
    }

    private void hideSoftKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputMethodManager =
                    (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void showEditProfileActivity(int code) {
        Intent toEditProfile = new Intent(getApplicationContext(), EditProfileActivity.class);
        toEditProfile.putExtra(UserProfile.PROFILE_INFO_KEY, LocalUserProfile.getInstance());
        startActivityForResult(toEditProfile, code);
    }

    @Override
    protected void openDialog(@NonNull DialogID dialogId, boolean dialogPersist) {
        super.openDialog(dialogId, dialogPersist);

        Dialog dialog = null;
        switch (dialogId) {
            case DIALOG_LOADING:
                dialog = ProgressDialog.show(this, null,
                        getString(R.string.fui_progress_dialog_loading), true);
                break;
            case DIALOG_ERROR_RETRIEVE_DIALOG:
                dialog = Utilities.openErrorDialog(this,
                        R.string.failed_load_data,
                        (dlg, which) -> signOut());
                break;
            case DIALOG_NO_CONNECTION:
                dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.no_internet_connection)
                        .setMessage(R.string.internet_needed)
                        .setPositiveButton(R.string.exit, (dlg, which) -> finish())
                        .setCancelable(false)
                        .show();
                break;
        }

        if (dialog != null) {
            setDialogInstance(dialog);
        }
    }

    @Override
    public void OnShowOwnedBooksClick(@NonNull UserProfile profile) {
        NavigationView drawer = findViewById(R.id.nav_view);
        drawer.getMenu().findItem(R.id.nav_library).setChecked(true);
        this.replaceFragment(LibraryFragment.newInstance());
    }

    public enum DialogID {
        DIALOG_LOADING,
        DIALOG_ERROR_RETRIEVE_DIALOG,
        DIALOG_NO_CONNECTION,
    }
}
