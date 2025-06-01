package com.attendance.system;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.Pair;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FaceActivity extends AppCompatActivity {
    private FaceDetector detector;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private ImageView facePreview;
    private Interpreter tfLite;
    private TextView recoName, previewInfo, textAbovePreview;
    private Button recognize, cameraSwitch, actions;
    private ImageButton addFace;
    private Dialog progressDialog;
    private CameraSelector cameraSelector;
    private boolean start = true, flipX = false;
    private int camFace = CameraSelector.LENS_FACING_BACK; // Default Back Camera
    private float distance = 1.0f;
    private static final int SELECT_PICTURE = 1;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private ProcessCameraProvider cameraProvider;
    private String modelFile = "mobile_face_net.tflite";
    private HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>();
    private int inputSize = 112; // Input size for the model
    private boolean isModelQuantized = false;
    private float IMAGE_MEAN = 128.0f;
    private float IMAGE_STD = 128.0f;
    private int OUTPUT_SIZE = 192; // Output size of the model
    private int[] intValues;
    private float[][] embeddings;
    private String fName;
    private RTDataRepository dataRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_face);
        String year = getIntent().getStringExtra("year");
        if (year != null) {
            fName = "recognitions-" + LectureSelectionActivity.getClassFolder(year) + ".json";
        }

        dataRepo = new RTDataRepository();
        downloadRecognitions(fName);

        facePreview = findViewById(R.id.imageView);
        recoName = findViewById(R.id.textView);
        previewInfo = findViewById(R.id.textView2);
        textAbovePreview = findViewById(R.id.textAbovePreview);
        addFace = findViewById(R.id.imageButton);
        recognize = findViewById(R.id.button3);
        cameraSwitch = findViewById(R.id.button5);
        actions = findViewById(R.id.button2);
        textAbovePreview.setText("Recognized Face:");
        facePreview.setVisibility(View.INVISIBLE);
        addFace.setVisibility(View.INVISIBLE);

        SharedPreferences sharedPref = getSharedPreferences("Distance", Context.MODE_PRIVATE);
        distance = sharedPref.getFloat("distance", 1.00f);

        File file = new File(/*getExternalFilesDir(null)*/getFilesDir(), fName);
        if (file.exists()) {
            registered = readFromSP(file);
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }

        actions.setOnClickListener(v -> showActionsDialog());
        cameraSwitch.setOnClickListener(v -> toggleCamera());
        addFace.setOnClickListener(v -> addFace());
        recognize.setOnClickListener(v -> toggleRecognitionMode());

        try {
            tfLite = new Interpreter(loadModelFile(this, modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        FaceDetectorOptions highAccuracyOpts = new FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).build();
        detector = FaceDetection.getClient(highAccuracyOpts);
        cameraBind();
    }

    void downloadRecognitions(String filename) {
        dataRepo.downloadRecognitions(this, filename, new RTDataRepository.DownloadCallback() {
            @Override
            public void onSuccess(String filePath) {
                Toast.makeText(FaceActivity.this, "Downloaded Recognitions Successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(FaceActivity.this, "Downloaded Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showActionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Action:");
        String[] names = {
                "Update Recognition List",
                "Upload Recognitions",
                "Load Recognitions",
                "Hyperparameters",
        };
        builder.setItems(names, (dialog, which) -> handleActionSelection(which));
        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void handleActionSelection(int which) {
        switch (which) {
            case 0:
                updateRecognitionList();
                break;
            case 1:
                saveRecognitions();
                break;
            case 2:
                File file = new File(/*getExternalFilesDir(null)*/getFilesDir(), fName);
                if (file.exists()) {
                    registered = readFromSP(file);
                }
                break;
            case 3:
                adjustHyperparameters();
                break;
        }
    }

    private void toggleCamera() {
        camFace = (camFace == CameraSelector.LENS_FACING_BACK) ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        flipX = (camFace == CameraSelector.LENS_FACING_FRONT);
        cameraProvider.unbindAll();
        cameraBind();
    }

    private void addFace() {
        start = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Details");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText nameInput = createEditText("Enter Name", InputType.TYPE_CLASS_TEXT);
        EditText rollNoInput = createEditText("Enter Roll No", InputType.TYPE_CLASS_NUMBER);
        layout.addView(nameInput);
        layout.addView(rollNoInput);
        builder.setView(layout);
        builder.setPositiveButton("ADD", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String rollNo = rollNoInput.getText().toString().trim();
            if (name.isEmpty() || rollNo.isEmpty()) {
                Toast.makeText(this, "Name and Roll No cannot be empty", Toast.LENGTH_SHORT).show();
                start = true;
                return;
            }
            boolean rollNoExists = false;
            for (String key : registered.keySet()) {
                if (key.split(":")[1].equals(rollNo)) {
                    rollNoExists = true;
                    break;
                }
            }
            if (rollNoExists) {
                Toast.makeText(this, "Roll No already exists! Please enter a different Roll No.", Toast.LENGTH_SHORT).show();
            } else {
                String key = name + ":" + rollNo;
                SimilarityClassifier.Recognition recognition = new SimilarityClassifier.Recognition("0", "", -1f);
                recognition.setExtra(embeddings);
                registered.put(key, recognition);
                Toast.makeText(this, "Face Added Successfully!", Toast.LENGTH_SHORT).show();
            }
            start = true;
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> start = true);
        builder.show();
    }

    private EditText createEditText(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        return editText;
    }

    private void toggleRecognitionMode() {
        if (recognize.getText().toString().equals("Recognize")) {
            recognize.setText("Add Face");
            addFace.setVisibility(View.INVISIBLE);
            recoName.setVisibility(View.VISIBLE);
            facePreview.setVisibility(View.INVISIBLE);
            previewInfo.setText("");
            textAbovePreview.setText("Recognized Face:");
        } else {
            recognize.setText("Recognize");
            addFace.setVisibility(View.VISIBLE);
            recoName.setVisibility(View.INVISIBLE);
            facePreview.setVisibility(View.VISIBLE);
            previewInfo.setText("1. Bring Face in view of Camera.\n2. Your Face preview will appear here.\n3. Click Add button to save face.");
            textAbovePreview.setText("Face Preview: ");
        }
    }

    private void cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        previewView = findViewById(R.id.previewView);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        cameraSelector = new CameraSelector.Builder().requireLensFacing(camFace).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(640, 480)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, this::analyzeImage);
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
    }

    private void analyzeImage(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            detector.process(inputImage).addOnSuccessListener(faces -> processFaces(faces, mediaImage, imageProxy)).addOnFailureListener(e -> imageProxy.close()).addOnCompleteListener(task -> imageProxy.close());
        }
    }

    private void processFaces(List<Face> faces, Image mediaImage, ImageProxy imageProxy) {
        if (!faces.isEmpty()) {
            Face face = faces.get(0);
            Bitmap frameBmp = toBitmap(mediaImage);
            Bitmap rotatedBmp = rotateBitmap(frameBmp, imageProxy.getImageInfo().getRotationDegrees(), flipX, false);
            RectF boundingBox = new RectF(face.getBoundingBox());
            Bitmap croppedFace = getCropBitmapByCPU(rotatedBmp, boundingBox);
            Bitmap scaledFace = getResizedBitmap(croppedFace, inputSize, inputSize);
            if (start) {
                recognizeImage(scaledFace);
            }
        } else {
            recoName.setText(registered.isEmpty() ? "Add Face" : "No Face Detected!");
        }
    }

    private void recognizeImage(Bitmap bitmap) {
        facePreview.setImageBitmap(bitmap);
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());
        intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        embeddings = new float[1][OUTPUT_SIZE];
        outputMap.put(0, embeddings);
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        findNearest(embeddings[0]);
    }

    private void findNearest(float[] emb) {
        Pair<String, Float> nearest = null, secondNearest = null;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];
            float distance = calculateEuclideanDistance(emb, knownEmb);
            if (nearest == null || distance < nearest.second) {
                secondNearest = nearest;
                nearest = new Pair<>(entry.getKey(), distance);
            }
        }
        if (nearest != null) {
            updateRecognitionResult(nearest, secondNearest);
        }
    }

    private float calculateEuclideanDistance(float[] emb1, float[] emb2) {
        float distance = 0;
        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            distance += diff * diff;
        }
        return (float) Math.sqrt(distance);
    }

    private void updateRecognitionResult(Pair<String, Float> nearest, Pair<String, Float> secondNearest) {
        float nearestDistance = nearest.second;
        String nearestName = nearest.first;
        recoName.setText(nearestDistance < distance ? nearestName : "Unknown");
    }

    private void updateRecognitionList() {
        if (registered.isEmpty()) {
            Toast.makeText(this, "No Faces Added!!", Toast.LENGTH_SHORT).show();
        } else {
            String[] names = registered.keySet().toArray(new String[0]);
            boolean[] checkedItems = new boolean[names.length];
            new AlertDialog.Builder(this)
                    .setTitle("Select Recognition to Delete:")
                    .setMultiChoiceItems(names, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                    .setPositiveButton("OK", (dialog, which) -> {
                        for (int i = 0; i < checkedItems.length; i++) {
                            if (checkedItems[i]) {
                                registered.remove(names[i]);
                            }
                        }
                        Toast.makeText(this, "Recognitions Updated", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void saveRecognitions() {
        try {
            String jsonString = new Gson().toJson(registered);
            File file = new File(/*getExternalFilesDir(null)*/getFilesDir(), fName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(jsonString.getBytes());
            fos.close();

            progressDialog = CustomAlertDialog.showProcessDialog(this, "Uploading...");
//            ProgressDialog progressDialog= new ProgressDialog(FaceActivity.this);
//            progressDialog.setMessage("Uploading...");
//            progressDialog.setCancelable(false);
//            progressDialog.show();

            // Upload to Firebase Storage
            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
            StorageReference fileRef = storageRef.child(fName);

            InputStream stream = new FileInputStream(file);
            UploadTask uploadTask = fileRef.putStream(stream);
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                progressDialog.dismiss();
                Toast.makeText(this, "Recognitions uploaded to Firebase Storage", Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(exception -> {
                progressDialog.dismiss();
                Toast.makeText(this, "Upload failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            });
        } catch (IOException e) {
            Toast.makeText(this, "Error Uploading Recognitions", Toast.LENGTH_SHORT).show();
        }
    }

    private void adjustHyperparameters() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Maximum Nearest Neighbor Distance");

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(distance));
        builder.setView(input);

        builder.setPositiveButton("Update", (dialog, which) -> {
            distance = Float.parseFloat(input.getText().toString());
            SharedPreferences sharedPref = getSharedPreferences("Distance", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putFloat("distance", distance);
            editor.apply();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera Permission Granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelFile) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
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

    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(), (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawRect(new RectF(0, 0, cropRectF.width(), cropRectF.height()), paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);
        canvas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }
        return resultBitmap;
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

    private static byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;

        byte[] nv21 = new byte[ySize + uvSize * 2];
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

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
            } catch (ReadOnlyBufferException ignored) {
            }
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

    private Bitmap toBitmap(Image image) {
        byte[] nv21 = YUV_420_888toNV21(image);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
    }

    private HashMap<String, SimilarityClassifier.Recognition> readFromSP(File file) {
        HashMap<String, SimilarityClassifier.Recognition> retrievedMap = new HashMap<>();
        try {
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;

            // Read file content line by line
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            br.close();
            isr.close();

            // Parse JSON from the file into a HashMap
            TypeToken<HashMap<String, SimilarityClassifier.Recognition>> token = new TypeToken<HashMap<String, SimilarityClassifier.Recognition>>() {
            };
            retrievedMap = new Gson().fromJson(sb.toString(), token.getType());

            // Convert the stored ArrayList back to float[][] for each Recognition
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
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error Reading Recognitions from File", Toast.LENGTH_SHORT).show();
        }
        return retrievedMap;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {
            Uri selectedImageUri = data.getData();
            try {
                Bitmap bitmap = getBitmapFromUri(selectedImageUri);
                InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
                detector.process(inputImage)
                        .addOnSuccessListener(faces -> processImportedFace(faces, bitmap))
                        .addOnFailureListener(e -> {
                            start = true;
                            Toast.makeText(this, "Failed to Add", Toast.LENGTH_SHORT).show();
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processImportedFace(List<Face> faces, Bitmap bitmap) {
        if (!faces.isEmpty()) {
            Face face = faces.get(0);
            Bitmap rotatedBmp = rotateBitmap(bitmap, 0, flipX, false);
            RectF boundingBox = new RectF(face.getBoundingBox());
            Bitmap croppedFace = getCropBitmapByCPU(rotatedBmp, boundingBox);
            Bitmap scaledFace = getResizedBitmap(croppedFace, inputSize, inputSize);

            recognizeImage(scaledFace);
            addFace();
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }
}