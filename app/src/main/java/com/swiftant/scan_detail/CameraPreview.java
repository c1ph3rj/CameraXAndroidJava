package com.swiftant.scan_detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Arrays;

public class CameraPreview extends AppCompatActivity implements PermissionManager.PermissionResultListener, CameraService.CameraChangesListener {
    CardView captureBtn;
    CardView flashBtn;
    ImageView flashIcon;
    CardView flipCamBtn;
    ImageView flipCamIcon;
    PreviewView cameraPreview;
    ImageView captureIcon;
    PermissionManager permissionManager;
    CardView viewGalleryBtn;
    ImageView videoGalleryIcon;
    CardView videoGalleryPlayIcon;
    CardView timerTextView;
    TextView timerText;
    CameraService cameraService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);
        permissionManager = new PermissionManager(this);
        permissionManager.setPermissionResultListener(this);
        if (permissionManager.hasPermissions(permissionManager.cameraAndStoragePermissionArray())) {
            init();
        } else {
            permissionManager.requestPermissions(permissionManager.cameraAndStoragePermissionArray());
        }
    }

    void setIcon(ImageView imageView, int drawableId) {
        try {
            imageView.setImageDrawable(AppCompatResources.getDrawable(this, drawableId));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void init() {
        try {
            captureBtn = findViewById(R.id.captureBtn);
            flipCamBtn = findViewById(R.id.flipCamBtn);
            flashBtn = findViewById(R.id.flashBtn);
            flashIcon = findViewById(R.id.flashIcon);
            cameraPreview = findViewById(R.id.cameraPreview);
            captureIcon = findViewById(R.id.captureIcon);
            flipCamIcon = findViewById(R.id.flipCamIcon);
            viewGalleryBtn = findViewById(R.id.viewGalleryBtn);
            videoGalleryIcon = findViewById(R.id.previewImageView);
            videoGalleryPlayIcon = findViewById(R.id.videoBtn);
            timerTextView = findViewById(R.id.timerTextView);
            timerText = findViewById(R.id.timerText);

            cameraService = new CameraService(this, cameraPreview, true, true, this);

            cameraService.startCamera();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private void initFlashLight() {
        try {
            if (cameraService.isFlashAvailable()) {
                flashBtn.setOnClickListener(onClickFlash -> {
                    try {
                        if (cameraService.isFlashLightTurnedOn()) {
                            cameraService.turnOffFlash();
                        } else {
                            cameraService.turnOnFlash();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } else {
                flashBtn.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            flashBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPermissionGranted() {
        init();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.onRequestPermissionsResult(permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        permissionManager.handleSettingsActivityResult(permissionManager.cameraAndStoragePermissionArray(), requestCode, resultCode);
    }

    @Override
    public void onPermissionDenied() {
        permissionManager.showPermissionExplanationDialog();
    }

    void updateRecentPreview() {
        try {
            File rootDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/CameraFeatures");
            File[] capturedItems = rootDir.listFiles();

            if (capturedItems == null || capturedItems.length == 0) {
                viewGalleryBtn.setVisibility(View.GONE);
            } else {
                viewGalleryBtn.setVisibility(View.VISIBLE);
                Arrays.sort(capturedItems, (file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
                String recentFilePath = capturedItems[0].getPath();
                System.out.println(recentFilePath);
                Glide.with(this)
                        .load(recentFilePath)
                        .error(R.drawable.ic_launcher_foreground)
                        .centerCrop()
                        .into(videoGalleryIcon);
                videoGalleryPlayIcon.setVisibility((recentFilePath.endsWith(".mp4") ? View.VISIBLE : View.GONE));
            }

        } catch (Exception e) {
            e.printStackTrace();
            viewGalleryBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLensSwitch(int lensId) {
        try {
            if (lensId == CameraSelector.LENS_FACING_BACK) {
                flashBtn.setVisibility(View.VISIBLE);
            } else {
                flashBtn.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTimerUpdate(long millisUntilFinished) {
        if (timerText != null) {
            timerText.setText(cameraService.formatTime(millisUntilFinished));
        }
    }

    @Override
    public void onTimerDone() {
        if (timerText != null) {
            timerText.setText(R.string.reset_timer);
        }
    }

    @Override
    public void onTimerPause() {
        timerTextView.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.white));
        timerText.setTextColor(getColor(R.color.black));
    }

    @Override
    public void onTimerResume() {
        timerTextView.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.red));
        timerText.setTextColor(getColor(R.color.white));
    }

    @Override
    public void onTimerStart() {
        timerTextView.setVisibility(View.VISIBLE);
        timerText.setText(R.string.reset_timer);
    }

    @Override
    public void onTimerStop() {
        timerTextView.setVisibility(View.GONE);
    }

    @Override
    public void onVideoStateChanges(boolean isPaused) {
        try {
            if (isPaused) {
                cameraService.pauseTimer();
                setIcon(flipCamIcon, R.drawable.resume_ic);
            } else {
                cameraService.resumeTimer();
                setIcon(flipCamIcon, R.drawable.pause_ic);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
        updateRecentPreview();
    }

    @Override
    public void onErrorWhileSavingImage(@NonNull ImageCaptureException exception) {
        exception.printStackTrace();
    }

    @Override
    public void onRecordingInit() {
        setIcon(flipCamIcon, R.drawable.pause_ic);
    }

    @Override
    public void onRecordingStart() {
        setIcon(captureIcon, R.drawable.stop_recording_ic);
    }

    @Override
    public void onErrorWhileRecording(int error) {

    }

    @Override
    public void onRecordingComplete() {
        setIcon(flipCamIcon, R.drawable.flip_cam_ic);
        setIcon(captureIcon, R.drawable.camera_ic);
        updateRecentPreview();
    }

    @Override
    public void onFlashStateChange(boolean isFlashOn) {
        setIcon(flashIcon, (isFlashOn) ? R.drawable.flash_on_ic : R.drawable.flash_off_ic);
    }

    @Override
    public void onCameraReady() {
        captureBtn.setOnClickListener(onClickCapture -> {
            if (cameraService.isVideoCurrentlyRecording()) {
                cameraService.stopRecording();
            } else {
                if (getFreeSpaceInGB() < .5) {
                    Toast.makeText(this, "Not Enough Storage Space to Capture Image!", Toast.LENGTH_SHORT).show();
                    return;
                }
                cameraService.captureImage();
            }
        });

        captureBtn.setOnLongClickListener(onLongClickCapture -> {
            if (getFreeSpaceInGB() < 1) {
                Toast.makeText(this, "Not Enough Storage Space to Capture Video!", Toast.LENGTH_SHORT).show();
                return false;
            }
            cameraService.captureVideo();
            return true;
        });

        flipCamBtn.setOnClickListener(onClickFlipCam -> {
            try {
                if (cameraService.isVideoCurrentlyRecording()) {
                    cameraService.pauseRecording();
                } else {
                    cameraService.switchCamera();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        initFlashLight();
        updateRecentPreview();
    }
}