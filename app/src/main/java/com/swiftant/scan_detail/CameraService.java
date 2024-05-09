package com.swiftant.scan_detail;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

public class CameraService {
    private boolean isFlashLightOn = false;
    private int cameraPosition = CameraSelector.LENS_FACING_BACK;
    private CameraInfo currentCameraInfo;
    private CameraControl currentCameraControl;
    private final Executor executor;
    private final Activity activity;
    private Camera camera;
    private final PreviewView cameraPreview;
    private static final long INTERVAL = 1000; // Update interval in milliseconds

    private final Handler handler;
    private boolean isTimerRunning;
    private CameraChangesListener cameraChangesListener;

    private long elapsedTime;
    private boolean tapToFocus;
    private boolean pinchToZoom;
    private VideoCapture<Recorder> videoCapture;
    private ImageCapture imageCapture;

    private boolean isPaused = false;
    private Recording recording = null;

    CameraService(Activity activity, PreviewView cameraPreview) {
        this.activity = activity;
        this.cameraPreview = cameraPreview;
        this.handler = new Handler();
        this.isTimerRunning = false;
        this.elapsedTime = 0;
        executor = ContextCompat.getMainExecutor(activity);
        handleAdditionalFunctions();
    }

    CameraService(Activity activity, PreviewView cameraPreview, boolean tapToFocus, boolean pinchToZoom, CameraChangesListener cameraChangesListener) {
        this.activity = activity;
        this.cameraPreview = cameraPreview;
        this.handler = new Handler();
        this.isTimerRunning = false;
        this.elapsedTime = 0;
        this.tapToFocus = tapToFocus;
        this.pinchToZoom = pinchToZoom;
        this.cameraChangesListener = cameraChangesListener;
        executor = ContextCompat.getMainExecutor(activity);
        handleAdditionalFunctions();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void handleAdditionalFunctions() {
        try {
            ScaleGestureDetector.OnScaleGestureListener listener = new ScaleGestureDetector.OnScaleGestureListener() {
                private float initialZoom = 1.0f;

                @Override
                public boolean onScale(@NonNull ScaleGestureDetector scaleGestureDetector) {
                    float scaleFactor = scaleGestureDetector.getScaleFactor();

                    if (Float.isNaN(scaleFactor) || Float.isInfinite(scaleFactor)) {
                        return false;
                    }

                    initialZoom *= scaleFactor;

                    // Assuming you have a max and min zoom ratio, adjust as needed
                    float minZoom = 1.0f;
                    float maxZoom = (currentCameraInfo.getZoomState().getValue() != null) ? currentCameraInfo.getZoomState().getValue().getMaxZoomRatio() : 1;

                    // Clamp the zoom level within the specified range
                    initialZoom = Math.max(minZoom, Math.min(maxZoom, initialZoom));

                    Log.d("Zoom", String.valueOf(initialZoom));
                    currentCameraControl.setZoomRatio(initialZoom);

                    return true;
                }

                @Override
                public boolean onScaleBegin(@NonNull ScaleGestureDetector scaleGestureDetector) {
                    initialZoom = (currentCameraInfo.getZoomState().getValue() != null) ? currentCameraInfo.getZoomState().getValue().getZoomRatio() : 1;
                    return true;
                }

                @Override
                public void onScaleEnd(@NonNull ScaleGestureDetector scaleGestureDetector) {
                }
            };

            ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(activity.getApplicationContext(), listener);

            cameraPreview.setOnTouchListener((view, motionEvent) -> {
                if (tapToFocus) {
                    handleTapToFocus(motionEvent);
                }

                if (pinchToZoom) {
                    scaleGestureDetector.onTouchEvent(motionEvent);
                }
                return true;
            });
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private int getAspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - 4.0 / 3.0) <= Math.abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }

    void startCamera() {
        try {
            int aspectRatio = getAspectRatio(cameraPreview.getWidth(), cameraPreview.getHeight());
            ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(activity);

            listenableFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = listenableFuture.get();

                    Preview preview = new Preview.Builder().setResolutionSelector(new ResolutionSelector.Builder().setAspectRatioStrategy(new AspectRatioStrategy(aspectRatio, AspectRatioStrategy.FALLBACK_RULE_AUTO)).build()).build();

                    imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setTargetRotation(activity.getWindowManager().getDefaultDisplay().getRotation()).build();

                    Recorder recorder = new Recorder.Builder().setExecutor(executor).setQualitySelector(QualitySelector.from(Quality.UHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))).build();

                    videoCapture = VideoCapture.withOutput(recorder);

                    CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraPosition).build();

                    cameraProvider.unbindAll();

                    camera = cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector, preview, imageCapture, videoCapture);

                    preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                    try {
                        OrientationEventListener orientationEventListener = new OrientationEventListener(activity) {
                            @Override
                            public void onOrientationChanged(int orientation) {
                                int rotation;

                                // Monitors orientation values to determine the target rotation value
                                if (orientation >= 45 && orientation < 135) {
                                    rotation = Surface.ROTATION_270;
                                } else if (orientation >= 135 && orientation < 225) {
                                    rotation = Surface.ROTATION_180;
                                } else if (orientation >= 225 && orientation < 315) {
                                    rotation = Surface.ROTATION_90;
                                } else {
                                    rotation = Surface.ROTATION_0;
                                }

                                imageCapture.setTargetRotation(rotation);
                                videoCapture.setTargetRotation(rotation);
                            }
                        };

                        orientationEventListener.enable();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    currentCameraControl = camera.getCameraControl();
                    currentCameraInfo = camera.getCameraInfo();


                    cameraChangesListener.onCameraReady();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, executor);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setCameraChangesListener(CameraChangesListener listener) {
        this.cameraChangesListener = listener;
    }

    public void startTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true;
            elapsedTime = 0;
            handler.postDelayed(timerRunnable, INTERVAL);
            if (cameraChangesListener != null) {
                cameraChangesListener.onTimerStart();
            }
        }
    }

    public void pauseTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;
            handler.removeCallbacks(timerRunnable);
            if (cameraChangesListener != null) {
                cameraChangesListener.onTimerPause();
            }
        }
    }

    public void resumeTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true;
            handler.postDelayed(timerRunnable, INTERVAL);
            if (cameraChangesListener != null) {
                cameraChangesListener.onTimerResume();
            }
        }
    }

    public void stopTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;
            handler.removeCallbacks(timerRunnable);
            elapsedTime = 0;
            if (cameraChangesListener != null) {
                cameraChangesListener.onTimerDone();
                cameraChangesListener.onTimerStop();
            }
        }
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedTime += INTERVAL;
            notifyTimerUpdate(elapsedTime);
            handler.postDelayed(this, INTERVAL);
        }
    };

    private void notifyTimerUpdate(long millisElapsed) {
        if (cameraChangesListener != null) {
            cameraChangesListener.onTimerUpdate(millisElapsed);
        }
    }

    private void handleTapToFocus(MotionEvent event) {
        try {
            MeteringPointFactory factory = cameraPreview.getMeteringPointFactory();
            MeteringPoint point = factory.createPoint(event.getX(), event.getY());
            FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
            currentCameraControl.startFocusAndMetering(action);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String generateUniqueName(boolean isVideo) {
        long currentTimeMillis = System.currentTimeMillis();
        long nanoseconds = System.nanoTime() % 1_000_000;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        String timestamp = dateFormat.format(new Date(currentTimeMillis));
        return String.format(Locale.getDefault(), "%s_%s_%d.%s", (isVideo) ? "video" : "image", timestamp, nanoseconds, (isVideo) ? "mp4" : "jpg");
    }

    private void recordVideo() {
        try {
            isPaused = false;
            if (cameraChangesListener != null) {
                cameraChangesListener.onRecordingInit();
            }

            Recording previousRecording = recording;
            if (previousRecording != null) {
                previousRecording.stop();
                recording = null;
                return;
            }

            String fileName = generateUniqueName(true);
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/CameraFeatures/");

            MediaStoreOutputOptions videoOptions = new MediaStoreOutputOptions.Builder(activity.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(contentValues)
                    .build();


            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            recording = videoCapture.getOutput()
                    .prepareRecording(activity, videoOptions)
                    .withAudioEnabled()
                    .start(executor, videoRecordEvent -> {
                        if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                            startTimer();
                            if (cameraChangesListener != null) {
                                cameraChangesListener.onRecordingStart();
                            }
                        } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                            if (!((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                                if (cameraChangesListener != null) {
                                    cameraChangesListener.onErrorWhileRecording(((VideoRecordEvent.Finalize) videoRecordEvent).getError());
                                }
                            } else {
                                if (recording != null) {
                                    recording.stop();
                                    recording.close();
                                    recording = null;
                                }
                            }
                            stopTimer();
                            if (cameraChangesListener != null) {
                                cameraChangesListener.onRecordingComplete();
                            }
                        }
                        if (recording != null && getFreeSpaceInGB() < 1) {
                            Toast.makeText(activity, "Your storage space is less than 1GB. Please free up some space to continue.", Toast.LENGTH_SHORT).show();
                            stopRecording();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void takePicture() {
        try {
            File picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File subDirectory = new File(picturesDirectory, "CameraFeatures");
            if (!subDirectory.exists()) {
                if (subDirectory.mkdir()) {
                    System.out.println("Directory Created!");
                }
            }

            // Create a file in the subdirectory

            File file = new File(subDirectory, generateUniqueName(false));
            ImageCapture.OutputFileOptions captureOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
            imageCapture.takePicture(captureOptions, executor, new ImageCapture.OnImageSavedCallback() {

                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    if (cameraChangesListener != null) {
                        cameraChangesListener.onImageSaved(outputFileResults);
                    }
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    if (cameraChangesListener != null) {
                        cameraChangesListener.onErrorWhileSavingImage(exception);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void turnOffFlash() {
        try {
            if (isFlashAvailable()) {
                camera.getCameraControl().enableTorch(false);
                isFlashLightOn = false;
            } else {
                Toast.makeText(activity, "Unable to turn off the flashlight on your device.", Toast.LENGTH_SHORT).show();
            }
            cameraChangesListener.onFlashStateChange(isFlashLightOn);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public void stopRecording() {
        try {
            Recording previousRecording = recording;
            previousRecording.stop();
            recording = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isFlashAvailable() {
        try {
            return activity.getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            return false;
        }
    }

    public boolean isVideoCurrentlyRecording() {
        return recording != null;
    }

    public void turnOnFlash() {
        try {
            if (isFlashAvailable()) {
                camera.getCameraControl().enableTorch(true);
                isFlashLightOn = true;
            } else {
                Toast.makeText(activity, "Unable to turn on the flashlight on your device.", Toast.LENGTH_SHORT).show();
            }
            cameraChangesListener.onFlashStateChange(isFlashLightOn);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public boolean isVideoPaused() {
        return isPaused;
    }

    public boolean isFlashLightTurnedOn() {
        return isFlashLightOn;
    }

    public void captureImage() {
        try {
            if (recording == null) {
                if (getFreeSpaceInGB() < .5) {
                    Toast.makeText(activity, "Sorry, there's not enough storage space to take a picture!", Toast.LENGTH_SHORT).show();
                    return;
                }
                takePicture();
            } else {
                Toast.makeText(activity, "Video recording is in progress, so you can't take a picture right now.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public void captureVideo() {
        try {
            if (getFreeSpaceInGB() < 1) {
                Toast.makeText(activity, "Not Enough Storage Space to Capture Video!", Toast.LENGTH_SHORT).show();
                return;
            }

            recordVideo();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public void setTapToFocus(boolean tapToFocus) {
        this.tapToFocus = tapToFocus;
    }

    float getFreeSpaceInGB() {
        try {
            File externalStorage = Environment.getExternalStorageDirectory();
            long freeSpaceInBytes = externalStorage.getFreeSpace();
            float freeSpaceInGB = (float) freeSpaceInBytes / (1024 * 1024 * 1024);
            return (float) (Math.round(freeSpaceInGB * 10.0) / 10.0); // Round to one decimal point
        } catch (Exception e) {
            e.printStackTrace();
            return -1.0f;
        }
    }

    public void setPinchToZoom(boolean pinchToZoom) {
        this.pinchToZoom = pinchToZoom;
    }

    public int getCameraFacing() {
        return cameraPosition;
    }

    public void pauseRecording() {
        try {
            isPaused = !isPaused;
            if (isPaused) {
                recording.pause();
                pauseTimer();
            } else {
                recording.resume();
                resumeTimer();
            }
            if (cameraChangesListener != null) {
                cameraChangesListener.onVideoStateChanges(isPaused);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public void switchCamera() {
        try {
            cameraPosition = cameraPosition == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            if (cameraChangesListener != null) {
                cameraChangesListener.onLensSwitch(cameraPosition);
            }
            startCamera();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        return (millis == 0) ? "00:00:00" : String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static interface CameraChangesListener {
        void onLensSwitch(int lensId);

        void onTimerUpdate(long millisElapsed);

        void onTimerDone();

        void onTimerPause();

        void onTimerResume();

        void onTimerStart();

        void onTimerStop();

        void onVideoStateChanges(boolean isPaused);

        void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults);

        void onErrorWhileSavingImage(@NonNull ImageCaptureException exception);

        void onRecordingInit();

        void onRecordingStart();

        void onErrorWhileRecording(int error);

        void onRecordingComplete();

        void onFlashStateChange(boolean isFlashOn);

        void onCameraReady();
    }

}
