package com.attendance.system;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DataManagementActivity extends AppCompatActivity {

    private DatabaseReference database;
    private TableLayout practicalsTable, studCountTable, subjectsTable;
    private MaterialAutoCompleteTextView yearSpinnerFace, yearSpinnerPracticals, yearSpinnerStudCount, yearSpinnerSubjects;

    private Button updatePracticalsBtn, updateStudCountBtn, updateSubjectsBtn, updateFaceBtn;
    private Button addPracticalsBtn, addStudCountBtn, addSubjectsBtn;

    private List<String> yearList = new ArrayList<>(Arrays.asList("Second Year", "Third Year", "Final Year"));

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_management);

        database = FirebaseDatabase.getInstance().getReference();

        practicalsTable = findViewById(R.id.practicalsTable);
        studCountTable = findViewById(R.id.studCountTable);
        subjectsTable = findViewById(R.id.subjectsTable);

        yearSpinnerPracticals = findViewById(R.id.yearSpinnerPracticals);
        yearSpinnerPracticals.setOnClickListener(v -> yearSpinnerPracticals.showDropDown());
        yearSpinnerStudCount = findViewById(R.id.yearSpinnerStudCount);
        yearSpinnerStudCount.setOnClickListener(v -> yearSpinnerStudCount.showDropDown());
        yearSpinnerSubjects = findViewById(R.id.yearSpinnerSubjects);
        yearSpinnerSubjects.setOnClickListener(v -> yearSpinnerSubjects.showDropDown());
        yearSpinnerFace = findViewById(R.id.yearSpinnerFace);
        yearSpinnerFace.setOnClickListener(v -> yearSpinnerFace.showDropDown());
        yearSpinnerFace.setText("Second Year");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, yearList);
        yearSpinnerFace.setAdapter(adapter);
        yearSpinnerPracticals.setAdapter(adapter);
        yearSpinnerStudCount.setAdapter(adapter);
        yearSpinnerSubjects.setAdapter(adapter);

        updatePracticalsBtn = findViewById(R.id.updatePracticalsBtn);
        updateStudCountBtn = findViewById(R.id.updateStudCountBtn);
        updateSubjectsBtn = findViewById(R.id.updateSubjectsBtn);
        updateFaceBtn = findViewById(R.id.updateFace);
        addPracticalsBtn = findViewById(R.id.addPracticalsBtn);
        addStudCountBtn = findViewById(R.id.addStudCountBtn);
        addSubjectsBtn = findViewById(R.id.addSubjectsBtn);

        updatePracticalsBtn.setOnClickListener(v -> saveData("practicals", practicalsTable));
        updateStudCountBtn.setOnClickListener(v -> saveData("studcount", studCountTable));
        updateSubjectsBtn.setOnClickListener(v -> saveData("subjects", subjectsTable));
        updateFaceBtn.setOnClickListener(v -> newActivity());

        yearSpinnerPracticals.setOnItemClickListener((parent, view, position, id) ->{
            String selectedYear = parent.getItemAtPosition(position).toString();
            loadData("practicals", practicalsTable, addPracticalsBtn, selectedYear);
        });
        yearSpinnerStudCount.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = parent.getItemAtPosition(position).toString();
            loadData("studcount", studCountTable, addStudCountBtn, selectedYear);
        });
        yearSpinnerSubjects.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = parent.getItemAtPosition(position).toString();
            loadData("subjects", subjectsTable, addSubjectsBtn, selectedYear);
        });
    }

    private void newActivity() {
        Intent intent = new Intent(DataManagementActivity.this, FaceActivity.class);
        intent.putExtra("year", yearSpinnerFace.getText().toString());
        startActivity(intent);
    }

    private void loadData(String tableName, TableLayout table, Button addRow, String year) {
        Dialog processDialog = CustomAlertDialog.showProcessDialog(this, "Fetching subjects...");
        database.child(tableName).child(year).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                table.setVisibility(View.VISIBLE);
                table.removeAllViews();
                for (DataSnapshot itemSnapshot : task.getResult().getChildren()) {
                    String key = itemSnapshot.getKey();
                    String value = itemSnapshot.getValue().toString();
                    addDataRow(table, key, value, tableName, false);
                }
                if (!tableName.equals("studcount")) {
                    addRow.setVisibility(View.VISIBLE);
                }
                addRow.setOnClickListener(v -> addDataRow(table, "", "", tableName, true));
                processDialog.dismiss();
            }
        });
    }

    private void addDataRow(TableLayout table, String key, String value, String tableName, boolean isEmptyRow) {
        TableRow row = new TableRow(this);
        row.setGravity(Gravity.CENTER);
        TableLayout.LayoutParams rowParams = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 4);
        row.setLayoutParams(rowParams);

        int valueInputType = tableName.equals("studcount") ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT;
        EditText valueEdit = createEditText(value, valueInputType);
        TableRow.LayoutParams valParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
        valParams.setMargins(7, 0, 0, 0);
        valueEdit.setLayoutParams(valParams);

        row.addView(valueEdit);

        if (isEmptyRow) {
            table.addView(row, table.getChildCount());
        } else {
            table.addView(row);
        }
    }

    private EditText createEditText(String text, int inputType) {
        EditText editText = new EditText(this);
        editText.setText(text);
        editText.setPadding(8, 8, 8, 8);
        editText.setTextColor(Color.BLACK);
        editText.setBackgroundResource(R.drawable.border);
        editText.setInputType(inputType);
        return editText;
    }

    private void saveData(String tableName, TableLayout table) {
        CustomAlertDialog.showCustomAlertDialog(this, "Commit changes?", "Update changes to the database.", "YES", "NO", "", new CustomAlertDialog.OnDialogButtonClickListener() {
            @Override
            public void onPositiveButtonClick() {
                String year = getSelectedYearForTable(tableName);
                database.child(tableName).child(year).removeValue();
                for (int i = 0; i < table.getChildCount(); i++) {
                    TableRow row = (TableRow) table.getChildAt(i);
                    EditText valueEdit = (EditText) row.getChildAt(0);
                    String value = valueEdit.getText().toString().trim();
                    if (!value.isEmpty()) {
                        DatabaseReference newRef = database.child(tableName).child(year).push();
                        newRef.setValue(value);
                    }
                }
                Toast.makeText(DataManagementActivity.this, "Data Updated!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNegativeButtonClick() { }

            @Override
            public void onNeutralButtonClick(boolean isLongClick) { }
        });
    }

    private String getSelectedYearForTable(String tableName) {
        if (tableName.equals("practicals")) {
            return yearSpinnerPracticals.getText().toString();
        } else if (tableName.equals("studcount")) {
            return yearSpinnerStudCount.getText().toString();
        } else {
            return yearSpinnerSubjects.getText().toString();
        }
    }
}