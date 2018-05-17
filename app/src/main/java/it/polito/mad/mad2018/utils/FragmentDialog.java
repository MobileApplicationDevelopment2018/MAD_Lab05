package it.polito.mad.mad2018.utils;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

public class FragmentDialog<E extends Enum<E>> extends Fragment {
    private static final String CURRENT_DIALOG_ID_KEY = "current_dialog_id";

    private Dialog dialogInstance;
    private E dialogId;
    private boolean dialogPersist;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        this.dialogPersist = false;
        if (savedInstanceState != null) {
            @SuppressWarnings("unchecked")
            E dialogId = (E) savedInstanceState.getSerializable(CURRENT_DIALOG_ID_KEY);
            this.dialogId = dialogId;
        }

        if (dialogId != null) {
            this.openDialog(dialogId, true);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.closeDialog();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (dialogPersist && dialogId != null &&
                dialogInstance != null && dialogInstance.isShowing()) {
            outState.putSerializable(CURRENT_DIALOG_ID_KEY, dialogId);
        }
    }

    @CallSuper
    protected void openDialog(@NonNull E dialogId, boolean dialogPersist) {
        this.closeDialog();
        this.dialogId = dialogId;
        this.dialogPersist = dialogPersist;
    }

    protected final void setDialogInstance(@NonNull Dialog dialogInstance) {
        this.dialogInstance = dialogInstance;
    }

    protected final void closeDialog() {
        if (this.dialogInstance != null && this.dialogInstance.isShowing()) {
            this.dialogInstance.dismiss();
        }
        this.dialogId = null;
        this.dialogInstance = null;
        this.dialogPersist = false;
    }
}
