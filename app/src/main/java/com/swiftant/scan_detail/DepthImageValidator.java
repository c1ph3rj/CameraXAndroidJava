package com.swiftant.scan_detail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

public class DepthImageValidator {

    private Session session;

    public DepthImageValidator(Context context) throws UnavailableArcoreNotInstalledException, UnavailableDeviceNotCompatibleException, UnavailableSdkTooOldException, UnavailableApkTooOldException {
        session = new Session(context);
        Config config = new Config(session);
        boolean isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
        if (isDepthSupported) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            throw new UnavailableDeviceNotCompatibleException();
        }
        session.configure(config);
    }

//    public Bitmap convertDepthToGrayscaleImage(Frame frame) {
//        if (!frame.hasDepthData()) return null;
//         depthData = frame.getDepthData();
//        int width = depthData.getWidth();
//        int height = depthData.getHeight();
//        float[] distances = depthData.getDistances();
//        Bitmap grayscaleImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//
//        float maxDepth = 10.0f; // Adjust according to your max depth range
//
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                float distance = distances[y * width + x];
//                int intensity = (int) (255 * (distance / maxDepth)); // Normalize to 255
//                int color = Color.rgb(intensity, intensity, intensity);
//                grayscaleImage.setPixel(x, y, color);
//            }
//        }
//        return grayscaleImage;
//    }

    public boolean isValidImage(Bitmap grayscaleImage) {
        int width = grayscaleImage.getWidth();
        int height = grayscaleImage.getHeight();
        int[] histogram = new int[256];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = grayscaleImage.getPixel(x, y);
                int intensity = Color.red(pixel);
                histogram[intensity]++;
            }
        }

        int threshold = width * height / 2;
        for (int count : histogram) {
            if (count > threshold) {
                return false;
            }
        }
        return true;
    }
}