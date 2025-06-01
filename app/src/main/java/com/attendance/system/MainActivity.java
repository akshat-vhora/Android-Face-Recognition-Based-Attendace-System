package com.attendance.system;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Pair;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private final HashMap<Integer, ToggleButton> toggleButtonMap = new HashMap<>();
    List<ToggleButton> toggleButtons = new ArrayList<>();
    PreviewView previewView;
    boolean start = true, flipX = false;
    Context context = com.attendance.system.MainActivity.this;
    int cam_face = CameraSelector.LENS_FACING_BACK;
    float distance = 1.0f;
    String modelFile = "mobile_face_net.tflite";
    TextView reco_name;
    ProcessCameraProvider cameraProvider;
    CameraSelector cameraSelector;
    FaceDetector detector;
    Interpreter tfLite;
    int[] intValues;
    int inputSize = 112;
    boolean isModelQuantized = false;
    float[][] embeedings;
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    int OUTPUT_SIZE = 192;
    private boolean doubleBackToExitPressedOnce = false;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>();

    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(), (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(new RectF(0, 0, cropRectF.width(), cropRectF.height()), paint);
        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);
        cavas.drawBitmap(source, matrix, paint);
        if (source != null && !source.isRecycled()) {
            source.recycle();
        }
        return resultBitmap;
    }

    private static byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;
        byte[] nv21 = new byte[ySize + uvSize * 2];
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V
        int rowStride = image.getPlanes()[0].getRowStride();
        assert (image.getPlanes()[0].getPixelStride() == 1);
        int pos = 0;
        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        } else {
            long yBufferPos = -rowStride;
            for (; pos < ySize; pos += width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }
        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();
        assert (rowStride == image.getPlanes()[1].getRowStride());
        assert (pixelStride == image.getPlanes()[1].getPixelStride());
        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte) ~savePixel);
                if (uBuffer.get(0) == (byte) ~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());
                    return nv21;
                }
            } catch (ReadOnlyBufferException ignored) { }
            vBuffer.put(1, savePixel);
        }
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = col * pixelStride + row * rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }
        return nv21;
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
        String selectedYear = getIntent().getStringExtra("year");
        String selectedDay = getIntent().getStringExtra("day");
        String selectedTimeSlot = getIntent().getStringExtra("timeSlot");
        String selectedSubject = getIntent().getStringExtra("subject");
        int rollNo = getIntent().getIntExtra("studCount", 2001);
        String fileName = getIntent().getStringExtra("fileName");
        registered = readFromFile(fileName);
        String displayMessage = "Class Year: " + selectedYear
                + "\nDay: " + selectedDay
                + "\nTime Slot: " + selectedTimeSlot
                + "\nSubject: " + selectedSubject;
        TextView detailsTextView = findViewById(R.id.detailsTextView);
        detailsTextView.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
        detailsTextView.setOnClickListener(v -> CustomAlertDialog.showCustomAlertDialog(this, "Attendance Details", displayMessage, "OK", "", "", null));
        GridLayout gridLayout = findViewById(R.id.linear);
        reco_name = findViewById(R.id.reco_name);
        int startNum = 0;
        if (rollNo >= 4000) {
            startNum = 4001;
        } else if (rollNo >= 3000) {
            startNum = 3001;
        } else if (rollNo >= 2000) {
            startNum = 2001;
        }
        GradientDrawable defaultBackground = new GradientDrawable();
        defaultBackground.setColor(Color.GRAY);
        defaultBackground.setCornerRadius(10);

        GradientDrawable checkedBackground = new GradientDrawable();
        checkedBackground.setColor(Color.parseColor("#669900"));
        checkedBackground.setCornerRadius(10);

        for (int i = startNum; i <= rollNo; i++) {
            final ToggleButton toggleButton = new ToggleButton(this);
            toggleButton.setTypeface(null, Typeface.BOLD_ITALIC);
            toggleButton.setTextOn(String.valueOf(i));
            toggleButton.setTextOff(String.valueOf(i));
            toggleButton.setText(String.valueOf(i));
            toggleButton.setBackground(defaultBackground);
            toggleButton.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        if (isChecked) {
                            toggleButton.setBackground(checkedBackground);
                            toggleButton.setTextColor(Color.BLACK);
                        } else {
                            toggleButton.setBackground(defaultBackground);
                            toggleButton.setTextColor(Color.WHITE);
                        }
                    });
            toggleButtonMap.put(i, toggleButton);
            toggleButtons.add(toggleButton);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.setMargins(5, 0, 5, 10);
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            toggleButton.setLayoutParams(params);
            gridLayout.addView(toggleButton);
        }

        Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> {
            try {
                if (selectedYear != null) {
                    exportAttendanceToExcel(selectedSubject, selectedTimeSlot, selectedYear);
                }
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, "Failed to save attendance", Toast.LENGTH_SHORT).show();
            }
        });
        Button camera_switch = findViewById(R.id.button5);
        camera_switch.setOnClickListener(v -> {
            if (cam_face == CameraSelector.LENS_FACING_BACK) {
                cam_face = CameraSelector.LENS_FACING_FRONT;
                flipX = true;
            } else {
                cam_face = CameraSelector.LENS_FACING_BACK;
                flipX = false;
            }
            cameraProvider.unbindAll();
            cameraBind();
        });

        ImageView faceButton = findViewById(R.id.faceButton);
        CardView container = findViewById(R.id.container);
        faceButton.setOnClickListener(v -> {
            if (container.getVisibility() == View.GONE) {
                container.setVisibility(View.VISIBLE);
                cameraBind();
            } else {
                container.setVisibility(View.GONE);
                cameraProvider.unbindAll();
            }
        });

        try {
            tfLite = new Interpreter(loadModelFile(MainActivity.this, modelFile));
        } catch (IOException ignored) { }
        FaceDetectorOptions highAccuracyOpts = new FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).build();
        detector = FaceDetection.getClient(highAccuracyOpts);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private HashMap<String, SimilarityClassifier.Recognition> readFromFile(String fileName) {
        HashMap<String, SimilarityClassifier.Recognition> retrievedMap = new HashMap<>();
        try {
            File file = new File(/*getExternalFilesDir(null)*/getFilesDir(), fileName);
            if (!file.exists()) {
                Toast.makeText(context, "No Recognitions Found", Toast.LENGTH_SHORT).show();
                return retrievedMap;
            }

            FileInputStream fis = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            isr.close();
            TypeToken<HashMap<String, SimilarityClassifier.Recognition>> token = new TypeToken<HashMap<String, SimilarityClassifier.Recognition>>() {};
            retrievedMap = new Gson().fromJson(sb.toString(), token.getType());
            for (Map.Entry<String, SimilarityClassifier.Recognition> entry : retrievedMap.entrySet()) {
                float[][] output = new float[1][OUTPUT_SIZE];
                ArrayList<?> arrayList = (ArrayList<?>) entry.getValue().getExtra();
                if (arrayList != null && !arrayList.isEmpty()) {
                    arrayList = (ArrayList<?>) arrayList.get(0);
                    for (int counter = 0; counter < arrayList.size(); counter++) {
                        output[0][counter] = ((Double) arrayList.get(counter)).floatValue();
                    }
                    entry.getValue().setExtra(output);
                }
            }
            Toast.makeText(context, "Recognitions Loaded from File", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(context, "Error Reading Recognitions from File", Toast.LENGTH_SHORT).show();
        }
        return retrievedMap;
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        previewView = findViewById(R.id.previewView);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
            }
        }, ContextCompat.getMainExecutor(this));
    }

    public void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        cameraSelector = new CameraSelector.Builder().requireLensFacing(cam_face).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(640, 480)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                try {
                    Thread.sleep(0);
                } catch (InterruptedException ignored) { }
                InputImage image = null;
                Image mediaImage = imageProxy.getImage();
                if (mediaImage != null) {
                    image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                }
                Task<List<Face>> result = detector.process(image)
                        .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                if (!faces.isEmpty()) {
                                    Face face = faces.get(0);
                                    Bitmap frame_bmp = toBitmap(mediaImage);
                                    int rot = imageProxy.getImageInfo().getRotationDegrees();
                                    Bitmap frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false);
                                    RectF boundingBox = new RectF(face.getBoundingBox());
                                    Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);
                                    if (flipX)
                                        cropped_face = rotateBitmap(cropped_face, 0, flipX, false);
                                    Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);
                                    if (start)
                                        recognizeImage(scaled);
                                } else {
                                    reco_name.setText("No Face Detected!");
                                }
                            }
                        })
                        .addOnFailureListener(e -> {})
                        .addOnCompleteListener(task -> imageProxy.close());
            }
        });
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
    }

    public void recognizeImage(final Bitmap bitmap) {
        ByteBuffer imgData = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());
        intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else {
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }
        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        embeedings = new float[1][OUTPUT_SIZE];
        outputMap.put(0, embeedings);
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        float distance_local = Float.MAX_VALUE;
        String id = "0";
        String label = "?";
        if (!registered.isEmpty()) {
            final List<Pair<String, Float>> nearest = findNearest(embeedings[0]);
            if (nearest.get(0) != null) {
                final String name = nearest.get(0).first;
                distance_local = nearest.get(0).second;
                if (distance_local < distance) {
                    String[] parts = name.split(":");
                    if (parts.length == 2) {
                        String personName = parts[0];
                        int rollNo = Integer.parseInt(parts[1]);
                        reco_name.setText(String.format("%s : %d", personName, rollNo));
                        if (toggleButtonMap.containsKey(rollNo)) {
                            ToggleButton detectedButton = toggleButtonMap.get(rollNo);
                            if (detectedButton != null) {
                                detectedButton.setChecked(true);
                                detectedButton.setBackgroundColor(Color.GREEN);
                                detectedButton.setTextColor(Color.BLACK);
                            }
                        }
                    } else {
                        reco_name.setText("Invalid Format");
                    }
                } else reco_name.setText("Unknown");
            }
        }
    }

    private List<Pair<String, Float>> findNearest(float[] emb) {
        List<Pair<String, Float>> neighbour_list = new ArrayList<Pair<String, Float>>();
        Pair<String, Float> ret = null;
        Pair<String, Float> prev_ret = null;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];
            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                prev_ret = ret;
                ret = new Pair<>(name, distance);
            }
        }
        if (prev_ret == null) prev_ret = ret;
        neighbour_list.add(ret);
        neighbour_list.add(prev_ret);
        return neighbour_list;
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    private Bitmap toBitmap(Image image) {
        byte[] nv21 = YUV_420_888toNV21(image);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void exportAttendanceToExcel(String subjectShortName, String timeSlot, String selectedYear) throws IOException {
        String currentMonthYear = new SimpleDateFormat("MM-yyyy").format(new Date());
        String classFolder;
        switch (selectedYear) {
            case "Second Year":
                classFolder = "SY";
                break;
            case "Third Year":
                classFolder = "TY";
                break;
            case "Final Year":
                classFolder = "FY";
                break;
            default:
                classFolder = "BTECH";
                break;
        }
        File directory = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), currentMonthYear + "-" + classFolder);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File excelFile = new File(directory, subjectShortName + ".xls");
        Workbook workbook;
        Sheet sheet;
        if (excelFile.exists()) {
            FileInputStream fis = new FileInputStream(excelFile);
            workbook = new HSSFWorkbook(fis);
            sheet = workbook.getSheetAt(0);
            fis.close();
        } else {
            workbook = new HSSFWorkbook();
            sheet = workbook.createSheet("Attendance");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Roll Number");
        }
        String currentDate = new SimpleDateFormat("dd/MM").format(new Date());
        String columnHeader = currentDate + "-" + timeSlot;
        int columnIndex = getColumnIndex(sheet, columnHeader);
        if (columnIndex == -1) {
            columnIndex = sheet.getRow(0).getLastCellNum();
            sheet.getRow(0).createCell(columnIndex).setCellValue(columnHeader);
        }
        for (int i = 0; i < toggleButtons.size(); i++) {
            ToggleButton toggleButton = toggleButtons.get(i);
            String rollNumber = toggleButton.getText().toString();
            boolean isPresent = toggleButton.isChecked();
            Row row = findOrCreateRow(sheet, rollNumber, i + 1);
            row.createCell(columnIndex).setCellValue(isPresent ? "P" : "A");
        }
        FileOutputStream fos = new FileOutputStream(excelFile);
        workbook.write(fos);
        fos.close();
        workbook.close();
        Toast.makeText(this, "Attendance saved to " + excelFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
    }

    private int getColumnIndex(Sheet sheet, String headerValue) {
        Row headerRow = sheet.getRow(0);
        for (int cellNum = 1; cellNum < headerRow.getLastCellNum(); cellNum++) {
            if (headerRow.getCell(cellNum).getStringCellValue().equals(headerValue)) {
                return cellNum;
            }
        }
        return -1;
    }

    private Row findOrCreateRow(Sheet sheet, String rollNumber, int rowIndex) {
        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row.getCell(0).getStringCellValue().equals(rollNumber)) {
                return row;
            }
        }
        // Create a new row for the roll number
        Row newRow = sheet.createRow(rowIndex);
        newRow.createCell(0).setCellValue(rollNumber);
        return newRow;
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed(); // Navigate back to previous activity
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press once again to go to the previous window", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }
}