package com.attendance.system;

import android.app.Dialog;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RTDataRepository {
    private final DatabaseReference databaseReference;
    private final StorageReference storageReference;

    public RTDataRepository() {
        databaseReference = FirebaseDatabase.getInstance().getReference();
        storageReference = FirebaseStorage.getInstance().getReference();
    }

    public void fetchTheorySubjects(Context context, String year, SubjectFetchCallback callback) {
        fetchSubjects(context, "subjects", year, callback);
    }

    public void fetchPracticalSubjects(Context context, String year, SubjectFetchCallback callback) {
        fetchSubjects(context, "practicals", year, callback);
    }

    private void fetchSubjects(Context context, String category, String year, SubjectFetchCallback callback) {
        Dialog progressDialog = CustomAlertDialog.showProcessDialog(context, "Fetching subjects...");
        databaseReference.child(category).child(year).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<String> subjects = new ArrayList<>();
                if (dataSnapshot.exists()) {
                    for (DataSnapshot subjectSnapshot : dataSnapshot.getChildren()) {
                        subjects.add(subjectSnapshot.getValue(String.class));
                    }
                    callback.onSuccess(subjects);
                } else {
                    callback.onFailure("No subjects found for " + year);
                }
                progressDialog.dismiss();
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onFailure(databaseError.getMessage());
                progressDialog.dismiss();
            }
        });
    }

    public void fetchStudentCounts(Context context, String year, StudentCountFetchCallback callback) {
        Dialog progressDialog = CustomAlertDialog.showProcessDialog(context, "Fetching student counts...");
        databaseReference.child("studcount").child(year).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() == 1) { // Check if only one value exists
                    for (DataSnapshot childSnapshot : dataSnapshot.getChildren()) { // Iterate through the single child
                        String studentCounts = childSnapshot.getValue(String.class);
                        if (studentCounts != null) {
                            try {
                                int count = Integer.parseInt(studentCounts);
                                progressDialog.dismiss();
                                callback.onSuccess(count);
                            } catch (NumberFormatException e) {
                                progressDialog.dismiss();
                                callback.onFailure("Invalid student count format for " + year);
                            }
                        } else {
                            progressDialog.dismiss();
                            callback.onFailure("Student count is null for " + year);
                        }
                        return; // Exit after processing the single value
                    }
                } else if (!dataSnapshot.exists()) {
                    progressDialog.dismiss();
                    callback.onFailure("No student counts available for " + year);
                } else {
                    progressDialog.dismiss();
                    callback.onFailure("Multiple values found for " + year + ". Expected only one.");
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onFailure("Database error: " + databaseError.getMessage());
                progressDialog.dismiss();
            }
        });
    }

    public void downloadRecognitions(Context context, String fileName, DownloadCallback callback) {
        Dialog progressDialog = CustomAlertDialog.showProcessDialog(context, "Downloading Recognitions");
        StorageReference fileRef = storageReference.child(fileName);
        File fileDir = new File(context.getFilesDir(), fileName);
        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
            new Thread(() -> {
                try {
                    InputStream inputStream = new URL(uri.toString()).openStream();
                    FileOutputStream outputStream = new FileOutputStream(fileDir);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.close();
                    inputStream.close();
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        progressDialog.dismiss();
                        callback.onSuccess(fileDir.getAbsolutePath());
                    });
                } catch (Exception e) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        progressDialog.dismiss();
                        callback.onFailure("Failed to download recognitions: " + e.getMessage());
                    });
                }
            }).start();
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            callback.onFailure("Error: " + e.getMessage());
        });
    }

    public interface SubjectFetchCallback {
        void onSuccess(List<String> subjects);
        void onFailure(String errorMessage);
    }

    public interface StudentCountFetchCallback {
        void onSuccess(Integer studentCounts);
        void onFailure(String errorMessage);
    }

    public interface DownloadCallback {
        void onSuccess(String filePath);
        void onFailure(String errorMessage);
    }
}