package it.polito.mad.mad2018.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;

public class Utilities {

    public static Dialog openErrorDialog(Context context, @StringRes int message) {
        return Utilities.openErrorDialog(context, message, null);
    }

    public static Dialog openErrorDialog(Context context, @StringRes int message,
                                         DialogInterface.OnClickListener listener) {
        return new AlertDialog.Builder(context)
                .setMessage(context.getString(message))
                .setPositiveButton(context.getString(android.R.string.ok), listener)
                .setCancelable(false)
                .show();
    }

    public static boolean isNetworkConnected(@NonNull Context context) {

        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return connectivityManager != null &&
                connectivityManager.getActiveNetworkInfo() != null &&
                connectivityManager.getActiveNetworkInfo().isConnected();
    }

    public static boolean isNullOrWhitespace(String s) {
        if (s == null)
            return true;

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String trimString(String string) {
        return trimString(string, 0);
    }

    public static String trimString(String string, int maxLength) {
        if (string == null) {
            return null;
        }

        string = string.trim().replaceAll("\\p{Zs}+", " ");
        if (maxLength > 0 && string.length() > maxLength) {
            string = string.substring(0, maxLength);
        }
        return string;
    }

    public static boolean equals(Object a, Object b) {
        return a == b || (a != null && b != null && a.equals(b));
    }

    public static boolean equalsNullOrWhiteSpace(String a, String b) {
        return Utilities.equals(a, b) ||
                (Utilities.isNullOrWhitespace(a) && Utilities.isNullOrWhitespace(b));
    }

    public static boolean validateIsbn(String isbn) {
        if (isbn == null)
            return false;

        //remove any hyphens
        isbn = isbn.replaceAll("-", "");

        try {
            if (isbn.length() == 13) {
                int tot = 0;
                for (int i = 0; i < 12; i++) {
                    int digit = Integer.parseInt(isbn.substring(i, i + 1));
                    tot += (i % 2 == 0) ? digit : digit * 3;
                }

                //checksum must be 0-9. If calculated as 10 then = 0
                int checksum = 10 - (tot % 10);
                if (checksum == 10) {
                    checksum = 0;
                }

                return checksum == Integer.parseInt(isbn.substring(12));

            } else if (isbn.length() == 10) {
                int tot = 0;
                for (int i = 0; i < 9; i++) {
                    int digit = Integer.parseInt(isbn.substring(i, i + 1));
                    tot += ((10 - i) * digit);
                }

                String checksum = Integer.toString((11 - (tot % 11)) % 11);
                if ("10".equals(checksum)) {
                    checksum = "X";
                }

                return checksum.equals(isbn.substring(9));

            } else return false;

        } catch (NumberFormatException nfe) {
            return false;
        }
    }
}
