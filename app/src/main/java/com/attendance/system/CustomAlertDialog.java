package com.attendance.system;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Objects;

public class CustomAlertDialog {

    public static void showCustomAlertDialog(Context context, String title, String message, String positiveButtonText, String negativeButtonText, String neutralButtonText, final OnDialogButtonClickListener listener) {
        final Dialog dialog = new Dialog(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.custom_alert_dialog, null);
        dialog.setContentView(dialogView);

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView titleTextView = dialogView.findViewById(R.id.alert_title);
        TextView messageTextView = dialogView.findViewById(R.id.alert_message);
        Button positiveButton = dialogView.findViewById(R.id.alert_positive_button);
        Button negativeButton = dialogView.findViewById(R.id.alert_negative_button);
        Button neutralButton = dialogView.findViewById(R.id.alert_neutral_button);

        titleTextView.setText(title);
        messageTextView.setText(message);
        positiveButton.setText(positiveButtonText);
        if (!negativeButtonText.isEmpty()) {
            negativeButton.setVisibility(View.VISIBLE);
            negativeButton.setText(negativeButtonText);
        }

        if (!neutralButtonText.isEmpty()) {
            neutralButton.setVisibility(View.VISIBLE);
            neutralButton.setText(neutralButtonText);
        }

        positiveButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPositiveButtonClick();
            }
            dialog.dismiss();
        });

        negativeButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNegativeButtonClick();
            }
            dialog.dismiss();
        });

        neutralButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNeutralButtonClick(false);
            }
        });

        neutralButton.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onNeutralButtonClick(true);
            }
            return false;
        });
        dialog.show();
    }

    public static Dialog showProcessDialog(Context context, String message) {
        final Dialog dialog = new Dialog(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.custom_process_dialog, null); // Create process_dialog.xml
        dialog.setContentView(dialogView);

        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCancelable(false); // Prevents dialog dismissal by touching outside

        TextView messageTextView = dialogView.findViewById(R.id.process_message);
        messageTextView.setText(message);

        dialog.show();
        return dialog; // Return the dialog so it can be dismissed later
    }

    public static Dialog createDialogInterface(Context context) {
        final Dialog dialog = new Dialog(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.custom_alert_dialog, null);
        dialog.setContentView(dialogView);

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return dialog;
    }

    public interface OnDialogButtonClickListener {
        void onPositiveButtonClick();
        void onNegativeButtonClick();
        void onNeutralButtonClick(boolean isLongClick);
    }

}
