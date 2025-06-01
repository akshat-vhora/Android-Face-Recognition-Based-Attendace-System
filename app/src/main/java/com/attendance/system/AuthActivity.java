package com.attendance.system;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.opengl.Visibility;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText, passwordCnfEditText;
    private TextInputLayout passCnfInputLayout;
    private Button signInButton, signUpButton;
    private FirebaseAuth mAuth;
    private TextView accTxt, signUpText, signInText, resetPasswordTextView;

    private Dialog processDialog;

    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        passwordCnfEditText = findViewById(R.id.passwordCnfEditText);
        passCnfInputLayout = findViewById(R.id.passCnfInputLayout);
        signInButton = findViewById(R.id.signInButton);
        signUpButton = findViewById(R.id.signUpButton);
        accTxt = findViewById(R.id.accTxt);
        signUpText = findViewById(R.id.signUpText);
        signInText = findViewById(R.id.signInText);
        resetPasswordTextView = findViewById(R.id.resetPasswordTextView);

        mAuth = FirebaseAuth.getInstance();

        loadSavedCredentials();

        signInButton.setOnClickListener(v -> signInWithProgress(emailEditText.getText().toString(), passwordEditText.getText().toString()));
        signUpButton.setOnClickListener(v -> signUpWithProgress(emailEditText.getText().toString(), passwordEditText.getText().toString(), passwordCnfEditText.getText().toString()));
        resetPasswordTextView.setOnClickListener(v -> showResetPasswordConfirmation(emailEditText.getText().toString()));

        signUpText.setOnClickListener(v -> {
            signInButton.setVisibility(View.GONE);
            passCnfInputLayout.setVisibility(View.VISIBLE);
            signUpButton.setVisibility(View.VISIBLE);
            resetPasswordTextView.setVisibility(View.GONE);
            signUpText.setVisibility(View.GONE);
            signInText.setVisibility(View.VISIBLE);
            accTxt.setText("Already have an account?");
        });

        signInText.setOnClickListener(v -> {
            signInButton.setVisibility(View.VISIBLE);
            passCnfInputLayout.setVisibility(View.GONE);
            signUpButton.setVisibility(View.GONE);
            resetPasswordTextView.setVisibility(View.VISIBLE);
            signUpText.setVisibility(View.VISIBLE);
            signInText.setVisibility(View.GONE);
            accTxt.setText("Don't have an account?");
        });
    }

    private boolean isValidEmail(String email) {
        return email.endsWith("@adcet.in") || email.endsWith("@adcet.ac.in");
    }

    private void signInWithProgress(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            if (email.isEmpty()) {
                emailEditText.setError("Cannot be empty");
            }
            if (password.isEmpty()) {
                passwordEditText.setError("Cannot be empty");
            }
        } else {
            processDialog = CustomAlertDialog.showProcessDialog(this, "Signing In...");
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        processDialog.dismiss();
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null && user.isEmailVerified()) {
                                updateUI(user);
                            } else {
                                if (user != null) {
                                    mAuth.signOut(); // Sign out unverified users
                                }
                                Toast.makeText(AuthActivity.this, "Please verify your email before signing in.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(AuthActivity.this, "Authentication failed, Incorrect email or password.", Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                    });
        }
    }

    private void signUpWithProgress(String email, String password, String passwordCnf) {
        if (isValidEmail(email)) {
            if (password.equals(passwordCnf)) {
                processDialog = CustomAlertDialog.showProcessDialog(this, "Sending verification mail...");
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            processDialog.dismiss();
                            if (task.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    user.sendEmailVerification()
                                            .addOnCompleteListener(sendTask -> {
                                                if (sendTask.isSuccessful()) {
                                                    CustomAlertDialog.showCustomAlertDialog(this, "Verification email sent", "Follow the steps mentioned in the mail to complete your verification.", "Ok", "", "", null);
//                                                    Toast.makeText(AuthActivity.this, "Verification email sent. Please check your inbox.", Toast.LENGTH_SHORT).show();
                                                    mAuth.signOut();
                                                } else {
                                                    Toast.makeText(AuthActivity.this, "Failed to send verification email.", Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                }
                            } else {
                                Toast.makeText(AuthActivity.this, "Sign up failed.", Toast.LENGTH_SHORT).show();
                                updateUI(null);
                            }
                        });
            } else {
                passwordCnfEditText.setError("Password does not match.");
            }
        } else {
            emailEditText.setError("Invalid email");
            Toast.makeText(AuthActivity.this, "Invalid email domain. Use @adcet.in or @adcet.ac.in", Toast.LENGTH_SHORT).show();
        }
    }

    private void showResetPasswordConfirmation(String email) {
        if (!email.isEmpty() && isValidEmail(email)) {
            CustomAlertDialog.showCustomAlertDialog(this, "Reset password?", "A reset password email will be sent to " + email + ", Continue?", "Yes", "No", "", new CustomAlertDialog.OnDialogButtonClickListener() {
                @Override
                public void onPositiveButtonClick() {
                    mAuth.sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(AuthActivity.this, "Password reset email sent.", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(AuthActivity.this, "Failed to send reset email.", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
                @Override
                public void onNegativeButtonClick() { }

                @Override
                public void onNeutralButtonClick(boolean isLongClick) { }
            });
        } else {
            Toast.makeText(AuthActivity.this, "Please enter a valid email.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedCredentials() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedEmail = sharedPreferences.getString(KEY_EMAIL, "");
        String savedPassword = sharedPreferences.getString(KEY_PASSWORD, "");
        if (!savedEmail.isEmpty() && !savedPassword.isEmpty()) {
            emailEditText.setText(savedEmail);
            passwordEditText.setText(savedPassword);
        }
    }

    private void saveCredentials(String email, String password) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_PASSWORD, password);
        editor.apply();
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            Toast.makeText(AuthActivity.this, "Authentication successful.", Toast.LENGTH_SHORT).show();
            saveCredentials(user.getEmail(), passwordEditText.getText().toString());
            Intent intent = new Intent(AuthActivity.this, LectureSelectionActivity.class);
            intent.putExtra("user", user.getEmail());
            startActivity(intent);
            finish();
        }
    }
}