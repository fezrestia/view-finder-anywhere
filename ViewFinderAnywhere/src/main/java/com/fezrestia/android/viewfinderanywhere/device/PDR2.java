package com.fezrestia.android.viewfinderanywhere.device;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;

import java.util.List;

/**
 * Platform Dependency Resolver based on Camera API 2.0.
 */
public class PDR2 {
    // Log tag.
    public static final String TAG = "PDR2";

    /**
     * Get back facing camera ID.
     *
     * @param camMng
     * @return
     * @throws CameraAccessException
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static String getBackCameraId(CameraManager camMng) throws CameraAccessException {
        String[] ids = camMng.getCameraIdList();

        // Scan ID.
        for (String eachId : ids) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ID = " + eachId);

            CameraCharacteristics eachCharacteristics = camMng.getCameraCharacteristics(eachId);

            if (Log.IS_DEBUG) {
                List<CameraCharacteristics.Key<?>> keys = eachCharacteristics.getKeys();
                android.util.Log.e("TraceLog", "###### AVAILABLE KEYS");
                for (CameraCharacteristics.Key<?> eachKey : keys) {
                    android.util.Log.e("TraceLog", "### KEY = " + eachKey.toString());
                }
            }

            // Check facing.
            if (eachCharacteristics.get(CameraCharacteristics.LENS_FACING)
                    == CameraCharacteristics.LENS_FACING_BACK) {
                // Valid.
                return eachId;
            }
        }

        // Not available.
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void logOutputImageFormat(StreamConfigurationMap streamConfigMap) {
        android.util.Log.e("TraceLog", "###### SUPPORTED OUTPUT FORMATS");
        logImageFormats(streamConfigMap, streamConfigMap.getOutputFormats());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static void logImageFormats(StreamConfigurationMap streamConfigMap, int[] formats) {
        for (int eachFormat : formats) {
            switch (eachFormat) {
                case ImageFormat.DEPTH16:
                    android.util.Log.e("TraceLog", "### FORMAT = DEPTH16");
                    break;
                case ImageFormat.DEPTH_POINT_CLOUD:
                    android.util.Log.e("TraceLog", "### FORMAT = DEPTH_POINT_CLOUD");
                    break;
                case ImageFormat.FLEX_RGBA_8888:
                    android.util.Log.e("TraceLog", "### FORMAT = FLEX_RGBA_8888");
                    break;
                case ImageFormat.FLEX_RGB_888:
                    android.util.Log.e("TraceLog", "### FORMAT = FLEX_RGB_888");
                    break;
                case ImageFormat.JPEG:
                    android.util.Log.e("TraceLog", "### FORMAT = JPEG");
                    break;
                case ImageFormat.NV16:
                    android.util.Log.e("TraceLog", "### FORMAT = NV16");
                    break;
                case ImageFormat.NV21:
                    android.util.Log.e("TraceLog", "### FORMAT = NV21");
                    break;
                case ImageFormat.PRIVATE:
                    android.util.Log.e("TraceLog", "### FORMAT = PRIVATE");
                    break;
                case ImageFormat.RAW10:
                    android.util.Log.e("TraceLog", "### FORMAT = RAW10");
                    break;
                case ImageFormat.RAW12:
                    android.util.Log.e("TraceLog", "### FORMAT = RAW12");
                    break;
                case ImageFormat.RAW_SENSOR:
                    android.util.Log.e("TraceLog", "### FORMAT = RAW_SENSOR");
                    break;
                case ImageFormat.RGB_565:
                    android.util.Log.e("TraceLog", "### FORMAT = RGB_565");
                    break;
                case ImageFormat.UNKNOWN:
                    android.util.Log.e("TraceLog", "### FORMAT = UNKNOWN");
                    break;
                case ImageFormat.YUV_420_888:
                    android.util.Log.e("TraceLog", "### FORMAT = YUV_420_888");
                    break;
                case ImageFormat.YUV_422_888:
                    android.util.Log.e("TraceLog", "### FORMAT = YUV_422_888");
                    break;
                case ImageFormat.YUV_444_888:
                    android.util.Log.e("TraceLog", "### FORMAT = YUV_444_888");
                    break;
                case ImageFormat.YUY2:
                    android.util.Log.e("TraceLog", "### FORMAT = YUY2");
                    break;
                case ImageFormat.YV12:
                    android.util.Log.e("TraceLog", "### FORMAT = YV12");
                    break;
                default:
                    android.util.Log.e("TraceLog", "### FORMAT = default : " + eachFormat);
                    break;
            }

            Size[] outputSizes = streamConfigMap.getOutputSizes(eachFormat);
            for (Size eachSize : outputSizes) {
                android.util.Log.e("TraceLog", "###   SIZE = " + eachSize.toString());
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void logSurfaceTextureOutputSizes(StreamConfigurationMap configMap) {
        Size[] supportedSizes = configMap.getOutputSizes(SurfaceTexture.class);

        android.util.Log.e("TraceLog", "###### SurfaceTexture OUTPUT SIZES");

        for (Size eachSize : supportedSizes) {
            android.util.Log.e("TraceLog", "### SIZE = " + eachSize.toString());
        }
    }

    /**
     * Get frame orientation.
     *
     * @param camCharacteristics
     * @param deviceOrientation
     * @return
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static int getFrameOrientation(
            CameraCharacteristics camCharacteristics,
            int deviceOrientation) {
        // Check.
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0;
        }

        // Round.
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Consider front facing.
        boolean isFacingFront = (camCharacteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraCharacteristics.LENS_FACING_FRONT);
        if (isFacingFront) {
            deviceOrientation = -1 * deviceOrientation;
        }

        int sensorOrientation = camCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int frameOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return frameOrientation;
    }

    /**
     * Get transform matrix for TextureView preview stream.
     *
     * @param context
     * @param bufferWidth
     * @param bufferHeight
     * @param finderWidth
     * @param finderHeight
     * @return
     */
    public static Matrix getTextureViewTransformMatrix(
            Context context,
            int bufferWidth,
            int bufferHeight,
            int finderWidth,
            int finderHeight) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### getTextureViewTransformMatrix() : E");

        // Display rotation.
        WindowManager winMng = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = winMng.getDefaultDisplay().getRotation();

        Matrix matrix = new Matrix();
        matrix.reset();
        RectF bufferRect = new RectF(0, 0, bufferWidth, bufferHeight);
        RectF finderRect = new RectF(0, 0, finderWidth, finderHeight);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### BufferRect = " + bufferRect.toShortString());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### FinderRect = " + finderRect.toShortString());
        float centerX = finderRect.centerX();
        float centerY = finderRect.centerY();

        // Aspect consideration.
        final float bufferAspect;
        if (bufferRect.height() < bufferRect.width()) {
            bufferAspect = bufferRect.width() / bufferRect.height();
        } else {
            bufferAspect = bufferRect.height() / bufferRect.width();
        }
        final float finderAspect;
        if (finderRect.height() < finderRect.width()) {
            finderAspect = finderRect.width() / finderRect.height();
        } else {
            finderAspect = finderRect.height() / finderRect.width();
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### BufferAspect = " + bufferAspect);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### FinderAspect = " + finderAspect);

        switch (rotation) {
            case Surface.ROTATION_180:
                if (Log.IS_DEBUG) Log.logDebug(TAG, "###### ROTATION_180");

                matrix.postRotate(180, centerX, centerY);

                // Fall through.
            case Surface.ROTATION_0:
                if (Log.IS_DEBUG) Log.logDebug(TAG, "###### ROTATION_0");

                // Align aspect.
                if (bufferAspect < finderAspect) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Scale vertical.");

                    matrix.postScale(
                            1.0f,
                            bufferAspect / finderAspect,
                            centerX,
                            centerY);

                } else {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Scale horizontal.");

                    matrix.postScale(
                            finderAspect / bufferAspect,
                            1.0f,
                            centerX,
                            centerY);
                }
                break;

            case Surface.ROTATION_90:
                // Fall through.
            case Surface.ROTATION_270:
                if (Log.IS_DEBUG) Log.logDebug(TAG, "###### ROTATION_90,270");

                // Convert landscape/portrait.
                RectF finderLand = new RectF(0, 0, finderRect.width(), finderRect.height());
                RectF finderPort = new RectF(0, 0, finderRect.height(), finderRect.width());
                finderPort.offset(centerX - finderPort.centerX(), centerY - finderPort.centerY());
                matrix.setRectToRect(finderLand, finderPort, Matrix.ScaleToFit.FILL);

                // Align aspect.
                if (bufferAspect < finderAspect) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Scale vertical.");

                    matrix.postScale(
                            1.0f,
                            bufferAspect / finderAspect,
                            centerX,
                            centerY);

                } else {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Scale portrait.");

                    matrix.postScale(
                            finderAspect / bufferAspect,
                            1.0f,
                            centerX,
                            centerY);
                }

                // Correct rotation.
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
                break;

            default:
                // Unexpected rotation.
                throw new IllegalStateException("Rotation is not valid.");
        }

        // Check aspect.
        if (((int) (bufferAspect * 100)) != ((int) (finderAspect * 100))) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Aspect is not matched");
            // Not matched.

            if (bufferAspect < finderAspect) {
                // Black area is available on right and left based on buffer coordinates.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Fit buffer right and left to finder.");

                matrix.postScale(
                        finderAspect / bufferAspect,
                        finderAspect / bufferAspect,
                        centerX,
                        centerY);
            } else {
                // Black area is available on top and bottom based on buffer coordinates.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Fit buffer top and bottom to finder");

                matrix.postScale(
                        bufferAspect / finderAspect,
                        bufferAspect / finderAspect,
                        centerX,
                        centerY);
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### getTextureViewTransformMatrix() : X");
        return matrix;
    }

    /**
     * Get optimal preview stream frame size.
     *
     * @param c
     * @return
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    // TODO: Consider display size ?
    public static Size getPreviewStreamFrameSize(CameraCharacteristics c) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### PreviewFrameSize");

        // Sensor aspect.
        Size fullSize = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        float fullAspectWH = ((float) fullSize.getWidth()) / ((float) fullSize.getHeight());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### PixelArraySize = " + fullSize.toString());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Full Size Aspect = " + fullAspectWH);

        // Estimate full aspect.
        final float diffTo43 = Math.abs(
                ViewFinderAnywhereConstants.ASPECT_RATIO_4_3 - fullAspectWH);
        final float diffTo169 = Math.abs(
                ViewFinderAnywhereConstants.ASPECT_RATIO_16_9 - fullAspectWH);
        if (diffTo43 < diffTo169) {
            // Near to 4:3.
            fullAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_4_3;
        } else {
            // Near to 16:9.
            fullAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_16_9;
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Estimated full Size Aspect = " + fullAspectWH);

        // Supported size.
        StreamConfigurationMap configMap = c.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = configMap.getOutputSizes(SurfaceTexture.class);

        // Match aspect.
        Size optimalSize = new Size(0, 0);
        for (Size eachSize : sizes) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "###### EachSize = " + eachSize.toString());

            //TODO: Consider display size ?
            if (1920 < eachSize.getWidth()) {
                continue;
            }

            // Target aspect.
            float eachAspectWH = ((float) eachSize.getWidth()) / ((float) eachSize.getHeight());
            if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Each Aspect = " + eachAspectWH);

            if (((int) (fullAspectWH * 100)) == ((int) (eachAspectWH * 100))) {
                if (optimalSize.getWidth() < eachSize.getWidth()) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Update optimal size");
                    optimalSize = eachSize;
                }
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### PreviewFrameSize = " + optimalSize.toString());

        return optimalSize;
    }

    /**
     * Get crop size based on active array size.
     *
     * @param c
     * @param aspectWH
     * @return
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Rect getAspectConsideredScalerCropRegion(
            CameraCharacteristics c,
            float aspectWH) {
        // Full resolution size.
        Rect fullRect = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        float fullAspectWH = ((float) fullRect.width()) / ((float) fullRect.height());
        // Max zoom.
        float maxZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                .floatValue();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "### ActiveArraySize = " + fullRect.toShortString());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "### MAX Zoom = " + maxZoom);

        // Crop rect.
        int cropWidth;
        int cropHeight;

        if (aspectWH < fullAspectWH) {
            // Full resolution is wider than requested aspect.

            cropHeight = fullRect.height();
            cropWidth = (int) (cropHeight * aspectWH);
        } else {
            // Full resolution is thinner than request aspect.

            cropWidth = fullRect.width();
            cropHeight = (int) (cropWidth / aspectWH);
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "### CropSize = " + cropWidth + "x" + cropHeight);

        Rect cropRect = new Rect(
                (fullRect.width() - cropWidth) / 2,
                (fullRect.height() - cropHeight) / 2,
                (fullRect.width() - cropWidth) / 2 + cropWidth,
                (fullRect.height() - cropHeight) / 2 + cropHeight);
        cropRect.offset(
                fullRect.centerX() - cropRect.centerX(),
                fullRect.centerY() - cropRect.centerY());

        if (Log.IS_DEBUG) Log.logDebug(TAG, "### CropRect = " + cropRect.toShortString());

        return cropRect;
    }



}
