package com.example.fitness;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

public class MainActivity extends AppCompatActivity {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    int PERMISSION_REQUESTS = 1;

    PreviewView previewView;

    // Base pose detector with streaming frames, when depending on the pose-detection sdk
    PoseDetectorOptions options =
            new PoseDetectorOptions.Builder()
                    .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                    .build();

    PoseDetector poseDetector = PoseDetection.getClient(options);

    Canvas canvas;

    Paint mPaint = new Paint();

    Display display;

    Bitmap bitmap4Save;
    ArrayList<Bitmap> bitmapArrayList = new ArrayList<>();
    ArrayList<Bitmap> bitmap4DisplayArrayList = new ArrayList<>();

    ArrayList<Pose> poseArrayList = new ArrayList<>();

    boolean isRunning = false;

    @ExperimentalGetImage
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        previewView = findViewById(R.id.previewView);

        display = findViewById(R.id.displayOverlay);

        mPaint.setColor(Color.GREEN);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setStrokeWidth(10);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));

        if (!allPermissionsGranted()) {
            getRuntimePermissions();
        }
    }

    Runnable RunMlkit = new Runnable() {
        @Override
        public void run() {
            poseDetector.process(InputImage.fromBitmap(bitmapArrayList.get(0),0)).addOnSuccessListener(new OnSuccessListener<Pose>() {
                @Override
                public void onSuccess(Pose pose) {
                    poseArrayList.add(pose);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {

                }
            });
        }
    };

    @ExperimentalGetImage
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
//                         enable the following line if RGBA output is needed.
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalysis.setAnalyzer(ActivityCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {


            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                ByteBuffer byteBuffer = Objects.requireNonNull(imageProxy.getImage()).getPlanes()[0].getBuffer();
                byteBuffer.rewind();
                Bitmap bitmap = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(byteBuffer);

                Matrix matrix = new Matrix();
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, imageProxy.getWidth(), imageProxy.getHeight(), matrix, false);

                bitmapArrayList.add(rotatedBitmap);

                if (poseArrayList.size() >= 1) {
                    canvas = new Canvas(bitmapArrayList.get(0));

                    Paint linePaint = new Paint();
                    linePaint.setColor(Color.GREEN);
                    linePaint.setStrokeWidth(2); // Slimmer lines

                    Paint dotPaint = new Paint();
                    dotPaint.setColor(Color.RED);
                    dotPaint.setStyle(Paint.Style.FILL); // Filled circle for landmark points

                    PoseLandmark[] poseLandmarks = poseArrayList.get(0).getAllPoseLandmarks().toArray(new PoseLandmark[0]);

                    for (PoseLandmark landmark : poseLandmarks) {
                        float landmarkX = landmark.getPosition().x;
                        float landmarkY = landmark.getPosition().y;
                        canvas.drawCircle(landmarkX, landmarkY, 4, dotPaint); // Slimmer dots for landmarks
                    }

                    // Define the landmark pairs that need lines drawn between them
                    int[][] landmarkPairs = {
                            {11, 12}, {12, 14}, {14, 16}, {16, 18}, {18, 20}, {16, 20}, {16, 22},
                            {12, 24}, {11, 23}, {24, 23}, {11, 13}, {13, 15}, {15, 17}, {17, 19},
                            {15, 19}, {15, 21}, {6, 5}, {5, 4}, {1, 2}, {2, 3}, {0, 4}, {0, 1},
                            {6, 8}, {3, 7}, {9, 10}, {24, 23}, {24, 26}, {26, 28}, {23, 25},
                            {25, 27},{28, 30}, {28, 32}, {30, 32}, {27, 29}, {27, 31}, {29,31}
                    };

// Iterate over landmark pairs and draw lines
                    for (int[] pair : landmarkPairs) {
                        int index1 = pair[0];
                        int index2 = pair[1];
                        if (index1 >= 0 && index1 < poseLandmarks.length && index2 >= 0 && index2 < poseLandmarks.length) {
                            PoseLandmark landmark1 = poseLandmarks[index1];
                            PoseLandmark landmark2 = poseLandmarks[index2];
                            canvas.drawLine(
                                    landmark1.getPosition().x, landmark1.getPosition().y,
                                    landmark2.getPosition().x, landmark2.getPosition().y,
                                    linePaint
                            );
                        }
                    }


                    bitmap4DisplayArrayList.clear();
                    bitmap4DisplayArrayList.add(bitmapArrayList.get(0));
                    bitmap4Save = bitmapArrayList.get(bitmapArrayList.size() - 1);
                    bitmapArrayList.clear();
                    bitmapArrayList.add(bitmap4Save);
                    poseArrayList.clear();
                    isRunning = false;
                }

                poseArrayList.size();
                if (bitmapArrayList.size() >= 1 && !isRunning) {
                    RunMlkit.run();
                    isRunning = true;
                }

                if (bitmap4DisplayArrayList.size() >= 1) {
                    display.getBitmap(bitmap4DisplayArrayList.get(0));
                }

                imageProxy.close();
            }
        });

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis, preview);
    }


    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }
}
