package com.attendance.system;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.File;
import java.util.List;

public class LectureSelectionActivity extends AppCompatActivity {

    private RTDataRepository subjectRepository; // Declare the SubjectRepository
    private int totalCount;

    private MaterialAutoCompleteTextView yearSpinner, lecLabSpinner, subjectSpinner, daySpinner, timeSlotSpinner;

    public static String[] years = {"Second Year", "Third Year", "Final Year"};
    public static String[] lecOrlab = {"Lecture", "Laboratory"};
    public static String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    public static String[] timeSlots = {
            "9.15 to 10.15",
            "10.15 to 11.15",
            "11.30 to 12.30",
            "12.30 to 1.30",
            "2.15 to 3.15",
            "3.15 to 4.15"
    };

    @SuppressLint("UseCompatLoadingForColorStateLists")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecture_selection);
        yearSpinner = findViewById(R.id.yearSpinner);
        lecLabSpinner = findViewById(R.id.lecLabSpinner);
        subjectSpinner = findViewById(R.id.subjectSpinner);
        daySpinner = findViewById(R.id.daySpinner);
        timeSlotSpinner = findViewById(R.id.timeSlotSpinner);
        Button submitBtn = findViewById(R.id.submitBtn);
        Button admin = findViewById(R.id.admin);

        subjectRepository = new RTDataRepository();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years);
        yearSpinner.setAdapter(adapter);
        ArrayAdapter<String> lecLabAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, lecOrlab);
        lecLabSpinner.setAdapter(lecLabAdapter);
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, days);
        daySpinner.setAdapter(dayAdapter);
        ArrayAdapter<String> timeSlotAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, timeSlots);
        timeSlotSpinner.setAdapter(timeSlotAdapter);

        String user = getIntent().getStringExtra("user");
        if (user != null && user.equals("hod_aids@adcet.in")) {
            admin.setVisibility(View.VISIBLE);
        } else {
            admin.setVisibility(View.GONE);
        }

        yearSpinner.setOnClickListener(v -> yearSpinner.showDropDown());
        lecLabSpinner.setOnClickListener(v -> lecLabSpinner.showDropDown());
        daySpinner.setOnClickListener(v -> daySpinner.showDropDown());
        timeSlotSpinner.setOnClickListener(v -> timeSlotSpinner.showDropDown());
        subjectSpinner.setOnClickListener(v -> subjectSpinner.showDropDown());

        yearSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = (String) parent.getItemAtPosition(position);
            String selectedLecLab = lecLabSpinner.getText().toString();
            studCount(selectedYear);
            if ("Lecture".equals(selectedLecLab) || "Laboratory".equals(selectedLecLab)) {
                subjectSpinner.setText("", false);
                updateSubjectSpinner(selectedYear, selectedLecLab);
            }
        });

        lecLabSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = yearSpinner.getText().toString();
            subjectSpinner.setText("", false);
            updateSubjectSpinner(selectedYear, lecOrlab[position]);
        });

        // Submit button click listener
        submitBtn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                String selectedYear = yearSpinner.getText().toString();
                String selectedDay = daySpinner.getText().toString();
                String selectedTimeSlot = timeSlotSpinner.getText().toString();
                String selectedSubject = subjectSpinner.getText().toString();
                String fileName = "recognitions-" + getClassFolder(selectedYear) + ".json";
                if (selectedYear.isEmpty() || selectedDay.isEmpty() || selectedTimeSlot.isEmpty() || selectedSubject.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Please select all fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                String confirmationMessage = "Year: " + selectedYear + "\n" +
                        "Day: " + selectedDay + "\n" +
                        "Time Slot: " + selectedTimeSlot + "\n" +
                        "Subject: " + selectedSubject;

//                LinearLayout layout = new LinearLayout(LectureSelectionActivity.this);
//                layout.setOrientation(LinearLayout.VERTICAL);
//                layout.setPadding(40, 40, 40, 40);
//                layout.setGravity(Gravity.CENTER);
//
//                // Create and style a TextView to show confirmation message
//                TextView messageView = new TextView(LectureSelectionActivity.this);
//                messageView.setText(confirmationMessage);
//                messageView.setTextSize(16);
//                messageView.setTextColor(Color.WHITE);
//                messageView.setPadding(0, 0, 0, 30);
//                layout.addView(messageView);
//
//                // Create and style the download button
//                Button downloadBtn = new Button(LectureSelectionActivity.this);
//                downloadBtn.setText("Download Face Data");
//                downloadBtn.setTextColor(Color.WHITE);
//                downloadBtn.setTextSize(14);
//                downloadBtn.setAllCaps(false);
//                downloadBtn.setPadding(20, 20, 20, 20);
//
//                // Set custom background using GradientDrawable
//                GradientDrawable drawable = new GradientDrawable();
//                drawable.setShape(GradientDrawable.RECTANGLE);
//                drawable.setCornerRadius(30); // Rounded corners
//                drawable.setColor(Color.parseColor("#4CAF50")); // Green background
//                downloadBtn.setBackground(drawable);
//
//                // Handle download button click
//                downloadBtn.setOnClickListener(v1 -> {
//                    File localFile = new File(/*getExternalFilesDir(null)*/getFilesDir(), fileName);
//                    if (localFile.exists()) {
//                        Toast.makeText(LectureSelectionActivity.this, "File already exists!", Toast.LENGTH_SHORT).show();
//                    } else {
//                        downloadRecognitions(fileName); // Download file when clicked
//                    }
//                });
//
//                downloadBtn.setOnLongClickListener(v1 -> {
//                    File localFile = new File(/*getExternalFilesDir(null)*/getFilesDir(), fileName);
//                    if (localFile.delete()) {
//                        downloadRecognitions(fileName);
//                    } else {
//                        Toast.makeText(LectureSelectionActivity.this, "Updating Failed", Toast.LENGTH_SHORT).show();
//                    }
//                    return false;
//                });
//
//
//                // Add the button to the layout
//                layout.addView(downloadBtn);
//
//                // Create and show AlertDialog with custom view
//                new AlertDialog.Builder(LectureSelectionActivity.this)
//                        .setTitle("Confirm Selection")
//                        .setView(layout)
//                        .setPositiveButton("Confirm", (dialog, which) -> {
//                            // Proceed to MainActivity
//                            Intent intent = new Intent(LectureSelectionActivity.this, MainActivity.class);
//                            intent.putExtra("year", selectedYear);
//                            intent.putExtra("day", selectedDay);
//                            intent.putExtra("timeSlot", selectedTimeSlot);
//                            intent.putExtra("subject", selectedSubject);
//                            switch (selectedYear) {
//                                case "Second Year":
//                                    intent.putExtra("studCount", totalCount + 2000);
//                                    break;
//                                case "Third Year":
//                                    intent.putExtra("studCount", totalCount + 3000);
//                                    break;
//                                case "Final Year":
//                                    intent.putExtra("studCount", totalCount + 4000);
//                                    break;
//                            }
//                            intent.putExtra("fileName", fileName);
//                            startActivity(intent);
//                        })
//                        .setNegativeButton("Cancel", null)
//                        .setIcon(android.R.drawable.ic_dialog_alert)
//                        .show();
                CustomAlertDialog.showCustomAlertDialog(LectureSelectionActivity.this, "Proceed With This Selection?", confirmationMessage, "Yes", "No", "Download recognitions", new CustomAlertDialog.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick() {
                        Intent intent = new Intent(LectureSelectionActivity.this, MainActivity.class);
                        intent.putExtra("year", selectedYear);
                        intent.putExtra("day", selectedDay);
                        intent.putExtra("timeSlot", selectedTimeSlot);
                        intent.putExtra("subject", selectedSubject);
                        switch (selectedYear) {
                            case "Second Year":
                                intent.putExtra("studCount", totalCount + 2000);
                                break;
                            case "Third Year":
                                intent.putExtra("studCount", totalCount + 3000);
                                break;
                            case "Final Year":
                                intent.putExtra("studCount", totalCount + 4000);
                                break;
                        }
                        intent.putExtra("fileName", fileName);
                        startActivity(intent);
                    }

                    @Override
                    public void onNegativeButtonClick() {

                    }

                    @Override
                    public void onNeutralButtonClick(boolean isLongClick) {
                        if (isLongClick) {
                            File localFile = new File(/*getExternalFilesDir(null)*/getFilesDir(), fileName);
                            if (localFile.delete()) {
                                downloadRecognitions(fileName);
                            } else {
                                Toast.makeText(LectureSelectionActivity.this, "Updating Failed", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            File localFile = new File(/*getExternalFilesDir(null)*/getFilesDir(), fileName);
                            if (localFile.exists()) {
                                Toast.makeText(LectureSelectionActivity.this, "File already exists!", Toast.LENGTH_SHORT).show();
                            } else {
                                downloadRecognitions(fileName);
                                Toast.makeText(LectureSelectionActivity.this, "Download success", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }
        });

        admin.setOnClickListener(v -> {
            Intent intent = new Intent(LectureSelectionActivity.this, DataManagementActivity.class);
            startActivity(intent);
        });
    }

    public void studCount(String selectedYear) {
        subjectRepository.fetchStudentCounts(this, selectedYear, new RTDataRepository.StudentCountFetchCallback() {
            @Override
            public void onSuccess(Integer studentCounts) {
                totalCount = studentCounts;
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(LectureSelectionActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    void downloadRecognitions(String filename) {
        subjectRepository.downloadRecognitions(this, filename, new RTDataRepository.DownloadCallback() {
            @Override
            public void onSuccess(String filePath) {
                Toast.makeText(LectureSelectionActivity.this, "Recognitions Downloaded", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(LectureSelectionActivity.this, "Recognitions Download Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Method to update the subject spinner based on the selected year
    private void updateSubjectSpinner(String selectedYear, String selectedSession) {
        if (selectedSession.equals("Lecture")) {
            subjectRepository.fetchTheorySubjects(this, selectedYear, new RTDataRepository.SubjectFetchCallback() {
                @Override
                public void onSuccess(List<String> subjects) {
                    ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(LectureSelectionActivity.this, android.R.layout.simple_spinner_dropdown_item, subjects);
                    subjectSpinner.setAdapter(subjectAdapter);
                }

                @Override
                public void onFailure(String errorMessage) {
                    Toast.makeText(LectureSelectionActivity.this, "Failed to load subjects: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (selectedSession.equals("Laboratory")) {
            subjectRepository.fetchPracticalSubjects(this, selectedYear, new RTDataRepository.SubjectFetchCallback() {
                @Override
                public void onSuccess(List<String> subjects) {
                    ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(LectureSelectionActivity.this, android.R.layout.simple_spinner_dropdown_item, subjects);
                    subjectSpinner.setAdapter(subjectAdapter);
                }

                @Override
                public void onFailure(String errorMessage) {
                    Toast.makeText(LectureSelectionActivity.this, "Failed to load subjects: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static String getClassFolder(String className) {
        switch (className) {
            case "Second Year":
                return "SY";
            case "Third Year":
                return "TY";
            case "Final Year":
                return "FY";
            default:
                return "";
        }
    }
}