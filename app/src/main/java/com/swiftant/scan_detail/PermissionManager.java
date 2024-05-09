package com.swiftant.scan_detail;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionManager {
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private static final String PERMISSION_READ_VIDEO = Manifest.permission.READ_MEDIA_VIDEO;
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private static final String PERMISSION_READ_IMAGE = Manifest.permission.READ_MEDIA_IMAGES;
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private static final String PERMISSION_READ_AUDIO = Manifest.permission.READ_MEDIA_AUDIO;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_WRITE_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String PERMISSION_READ_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    private static final String PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
    private final Activity activity;
    private final int requestCode = 1016;
    private PermissionResultListener listener;

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    public String[] storagePermissionsArray() {
        return (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) ? new String[]{PERMISSION_WRITE_STORAGE, PERMISSION_READ_STORAGE} : new String[] {PERMISSION_READ_IMAGE, PERMISSION_READ_VIDEO, PERMISSION_READ_AUDIO};
    }

    public String[] cameraAndStoragePermissionArray() {
        return (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) ? new String[]{PERMISSION_CAMERA, PERMISSION_WRITE_STORAGE, PERMISSION_READ_STORAGE, PERMISSION_RECORD_AUDIO} : new String[] {PERMISSION_CAMERA , PERMISSION_READ_IMAGE, PERMISSION_READ_VIDEO, PERMISSION_READ_AUDIO, PERMISSION_RECORD_AUDIO};
    }

    public void requestPermission(String permission) {
        ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
    }

    public void requestPermissions(String[] permissions) {
        if (!(getMissingPermissions(permissions).length == 0)) {
            ActivityCompat.requestPermissions(activity, getMissingPermissions(permissions), requestCode);
        }
    }

    public String[] getMissingPermissions(String[] permissions) {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                missingPermissions.add(permission);
            }
        }
        return missingPermissions.toArray(new String[0]);
    }

    public void setPermissionResultListener(PermissionResultListener listener) {
        this.listener = listener;
    }

    public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        boolean allPermissionGranted = true;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                allPermissionGranted = false;
                break;
            }
        }

        if (allPermissionGranted && listener != null) {
            listener.onPermissionGranted();
        } else if (!allPermissionGranted && listener != null) {
            listener.onPermissionDenied();
        }
    }

    public void handleSettingsActivityResult(String[] permissions, int requestCode, int resultCode) {
        if (requestCode == this.requestCode) {
            // Check permission status after returning from settings
            if (hasPermissions(permissions) && listener != null) {
                listener.onPermissionGranted();
            } else if (listener != null) {
                listener.onPermissionDenied();
            }
        }
    }


    public void startAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivityForResult(intent, requestCode);
    }



    public void showPermissionExplanationDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
        dialog.setTitle("Permission Required!");
        dialog.setMessage("Please allow the necessary permissions to continue!");
        dialog.setPositiveButton("Go to Settings", (dialogInterface, i) -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            startAppSettings(); // Start settings activity for result
        });
        dialog.setNegativeButton("Cancel", (dialogInterface, i) -> {
            activity.finish();
            dialogInterface.dismiss();
        });
        dialog.setCancelable(false);
        AlertDialog alertDialog = dialog.create();
        alertDialog.show();
    }
    public void showPermissionExplanationDialogC() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
        dialog.setTitle("Permission Required!");
        dialog.setMessage("Please allow the necessary permissions to continue!");
        dialog.setPositiveButton("Go to Settings", (dialogInterface, i) -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            startAppSettings(); // Start settings activity for result
        });
        dialog.setNegativeButton("Cancel", (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });
        dialog.setCancelable(false);
        AlertDialog alertDialog = dialog.create();
        alertDialog.show();
    }

    public interface PermissionResultListener {
        void onPermissionGranted();

        void onPermissionDenied();
    }
}