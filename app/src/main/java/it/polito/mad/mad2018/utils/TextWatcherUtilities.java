package it.polito.mad.mad2018.utils;

import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public class TextWatcherUtilities {

    public interface TextWatcherValidator {
        boolean isValid(String string);
    }

    public interface TextWatcherFunction {
        void apply(Editable editable);
    }

    public static class TextWatcherError implements TextWatcher {

        private final EditText textField;
        private final String errorMessage;
        private final TextWatcherValidator validator;

        public TextWatcherError(@NonNull EditText textField, @NonNull String errorMessage,
                                @NonNull TextWatcherValidator validator) {
            this.textField = textField;
            this.errorMessage = errorMessage;
            this.validator = validator;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (!validator.isValid(editable.toString())) {
                textField.setError(errorMessage);
            }
        }
    }

    public static class GenericTextWatcher implements TextWatcher {

        private final TextWatcherFunction action;

        public GenericTextWatcher(@NonNull TextWatcherFunction action) {
            this.action = action;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            action.apply(editable);
        }
    }
}
