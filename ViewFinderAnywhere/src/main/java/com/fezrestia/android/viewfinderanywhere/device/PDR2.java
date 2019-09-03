package com.fezrestia.android.viewfinderanywhere.device;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import com.fezrestia.android.lib.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.config.ViewFinderAspect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Platform Dependency Resolver based on Camera API 2.0.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class PDR2 {
    // Log tag.
    public static final String TAG = "PDR2";

    /**
     * Get back facing camera ID.
     *
     * @param camMng CameraManager instance.
     * @return Back camera ID.
     * @throws CameraAccessException Camera can not be accessed.
     */
    @SuppressWarnings("ConstantConditions")
    static String getBackCameraId(CameraManager camMng) throws CameraAccessException {
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

    static void logOutputImageFormat(StreamConfigurationMap streamConfigMap) {
        android.util.Log.e("TraceLog", "###### SUPPORTED OUTPUT FORMATS");
        logImageFormats(streamConfigMap, streamConfigMap.getOutputFormats());
    }

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

    static void logSurfaceTextureOutputSizes(StreamConfigurationMap configMap) {
        Size[] supportedSizes = configMap.getOutputSizes(SurfaceTexture.class);

        android.util.Log.e("TraceLog", "###### SurfaceTexture OUTPUT SIZES");

        for (Size eachSize : supportedSizes) {
            android.util.Log.e("TraceLog", "### SIZE = " + eachSize.toString());
        }
    }

    static void logAfTrigger(Integer afTrigger) {
        if (afTrigger == null) {
            android.util.Log.e("TraceLog", "### AF Trigger == NULL");
            return;
        }

        switch (afTrigger) {
            case CaptureResult.CONTROL_AF_TRIGGER_CANCEL:
                android.util.Log.e("TraceLog", "### AF Trigger == CANCEL");
                break;
            case CaptureResult.CONTROL_AF_TRIGGER_IDLE:
                android.util.Log.e("TraceLog", "### AF Trigger == IDLE");
                break;
            case CaptureResult.CONTROL_AF_TRIGGER_START:
                android.util.Log.e("TraceLog", "### AF Trigger == START");
                break;
            default:
                android.util.Log.e("TraceLog", "### AF Trigger == default");
                break;
        }
    }

    static void logAfState(Integer afState) {
        if (afState == null) {
            android.util.Log.e("TraceLog", "### AF State == NULL");
            return;
        }

        switch (afState) {
            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                android.util.Log.e("TraceLog", "### AF State == ACTIVE_SCAN");
                break;
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                android.util.Log.e("TraceLog", "### AF State == FOCUSED_LOCKED");
                break;
            case CaptureResult.CONTROL_AF_STATE_INACTIVE:
                android.util.Log.e("TraceLog", "### AF State == INACTIVE");
                break;
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                android.util.Log.e("TraceLog", "### AF State == NOT_FOCUSED_LOCKED");
                break;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                android.util.Log.e("TraceLog", "### AF State == PASSIVE_FOCUSED");
                break;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                android.util.Log.e("TraceLog", "### AF State == PASSIVE_SCAN");
                break;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                android.util.Log.e("TraceLog", "### AF State == PASSIVE_UNFOCUSED");
                break;
            default:
                android.util.Log.e("TraceLog", "### AF State == default");
                break;
        }
    }

    static void logAeTrigger(Integer aeTrigger) {
        if (aeTrigger == null) {
            android.util.Log.e("TraceLog", "### AE Trigger == NULL");
            return;
        }

        switch (aeTrigger) {
            case CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL:
                android.util.Log.e("TraceLog", "### AE Trigger == CANCEL");
                break;
            case CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE:
                android.util.Log.e("TraceLog", "### AE Trigger == IDLE");
                break;
            case CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_START:
                android.util.Log.e("TraceLog", "### AE Trigger == START");
                break;
            default:
                android.util.Log.e("TraceLog", "### AE Trigger == default");
                break;
        }
    }

    static void logAeState(Integer aeState) {
        if (aeState == null) {
            android.util.Log.e("TraceLog", "### AE State == NULL");
            return;
        }

        switch (aeState) {
            case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                android.util.Log.e("TraceLog", "### AE State == CONVERGED");
                break;
            case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                android.util.Log.e("TraceLog", "### AE State == FLASH_REQUIRED");
                break;
            case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                android.util.Log.e("TraceLog", "### AE State == INACTIVE");
                break;
            case CaptureResult.CONTROL_AE_STATE_LOCKED:
                android.util.Log.e("TraceLog", "### AE State == LOCKED");
                break;
            case CaptureResult.CONTROL_AE_STATE_PRECAPTURE:
                android.util.Log.e("TraceLog", "### AE State == PRECAPTURE");
                break;
            case CaptureResult.CONTROL_AE_STATE_SEARCHING:
                android.util.Log.e("TraceLog", "### AE State == SEARCHING");
                break;
            default:
                android.util.Log.e("TraceLog", "### AE State == default");
                break;
        }
    }



    /**
     * Get frame orientation.
     *
     * @param camCharacteristics Camera characteristics.
     * @param deviceOrientation Camera orientation degree.
     * @return Frame orientation degree.
     */
    @SuppressWarnings("ConstantConditions")
    static int getFrameOrientation(
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
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    /**
     * Get transform matrix for TextureView preview stream.
     *
     * @param context Master context.
     * @param bufferWidth Frame buffer width.
     * @param bufferHeight Frame buffer height.
     * @param finderWidth Finder width.
     * @param finderHeight Finder height.
     * @return Transform matrix.
     */
    static Matrix getTextureViewTransformMatrix(
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
     * @param c Camera characteristics.
     * @return Preview stream frame buffer size.
     */
    // TODO: Consider display size ?
    @SuppressWarnings("ConstantConditions")
    static Size getPreviewStreamFrameSize(CameraCharacteristics c) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### PreviewFrameSize");

        // Sensor aspect.
        Size fullSize = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        float fullAspectWH = ((float) fullSize.getWidth()) / ((float) fullSize.getHeight());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### PixelArraySize = " + fullSize.toString());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Full Size Aspect = " + fullAspectWH);

        // Estimate full aspect.
        final float diffTo43 = Math.abs(
                ViewFinderAspect.WH_4_3.getRatioWH() - fullAspectWH);
        final float diffTo169 = Math.abs(
                ViewFinderAspect.WH_16_9.getRatioWH() - fullAspectWH);
        if (diffTo43 < diffTo169) {
            // Near to 4:3.
            fullAspectWH = ViewFinderAspect.WH_4_3.getRatioWH();
        } else {
            // Near to 16:9.
            fullAspectWH = ViewFinderAspect.WH_16_9.getRatioWH();
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
     * Get maximum JPEG frame size.
     *
     * @param map Stream configuration map.
     * @param devicePreviewSize Camera device preview size.
     * @return Optimal JPEG frame size.
     */
    static Size getOptimalJpegFrameSize(StreamConfigurationMap map, Size devicePreviewSize) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### getOptimalJpegFrameSize");

        Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);

        int previewAspectRatioWHx100 = (int)
                ((float) devicePreviewSize.getWidth() / (float) devicePreviewSize.getHeight()
                * 100.0f);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "######   PreviewAspect = " + previewAspectRatioWHx100);

        List<Size> capableSizes = new ArrayList<>();
        for (Size eachSize : jpegSizes) {
            int aspectWHx100 = (int)
                    ((float) eachSize.getWidth() / (float) eachSize.getHeight() * 100.0f);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "######   EachAspect = " + aspectWHx100);

            if (previewAspectRatioWHx100 == aspectWHx100) {
                // Aspect matched.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "######   Capable = " + eachSize.toString());
                capableSizes.add(eachSize);
            }
        }

        // Check xperia recommended.
        Size maxSize = new Size(0, 0);
        for (Size eachSize : capableSizes) {
            if (eachSize.getWidth() == 3840 && eachSize.getHeight() == 2160) {
                // 8MP 16:9
                if (Log.IS_DEBUG) Log.logDebug(TAG, "######   Recommended 8MP 16:9");
                maxSize = eachSize;
                break;
            }
            if (eachSize.getWidth() == 3264 && eachSize.getHeight() == 2448) {
                // 8MP 4:3
                if (Log.IS_DEBUG) Log.logDebug(TAG, "######   Recommended 8MP 4:3");
                maxSize = eachSize;
                break;
            }

            // Larger is better.
            if (maxSize.getWidth() < eachSize.getWidth()) {
                maxSize = eachSize;
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "######   JPEG Size = " + maxSize.toString());
        return maxSize;
    }

    /**
     * Get crop size based on active array size.
     *
     * @param c Camera characteristics.
     * @param aspectWH Aspect ratio.
     * @return Aspect considered scaler crop region.
     */
    @SuppressWarnings("ConstantConditions")
    static Rect getAspectConsideredScalerCropRegion(
            CameraCharacteristics c,
            float aspectWH) {
        // Full resolution size.
        Rect fullRect = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        float fullAspectWH = ((float) fullRect.width()) / ((float) fullRect.height());
        // Max zoom.
        float maxZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

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

    /**
     * AF function is available or not.
     *
     * @param result Capture result.
     * @return AF is available or not.
     */
    static boolean isAfAvailable (CaptureResult result) {
        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
        return afState != null;
    }

    /**
     * AF is locked or not.
     *
     * @param result Capture result.
     * @return AF locked or not.
     */
    @SuppressWarnings("ConstantConditions")
    static boolean isAfLocked(CaptureResult result) {
        int afState = result.get(CaptureResult.CONTROL_AF_STATE);

        return (afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED)
                || (afState == CaptureRequest.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
    }

    /**
     * AF is successfully focused or not.
     *
     * @param result Capture result.
     * @return AF is success or not.
     */
    @SuppressWarnings("ConstantConditions")
    static boolean isAfSucceeded(CaptureResult result) {
        int afState = result.get(CaptureResult.CONTROL_AF_STATE);
        return (afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED);
    }

    /**
     * AE function is available or not.
     *
     * @param result Capture result.
     * @return AE is available or not.
     */
    static boolean isAeAvailable (CaptureResult result) {
        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
        return aeState != null;
    }

    /**
     * AE is locked or not.
     *
     * @param result Capture result.
     * @return AE is locked or not.
     */
    @SuppressWarnings("ConstantConditions")
    static boolean isAeLocked(CaptureResult result) {
        int aeState = result.get(CaptureResult.CONTROL_AE_STATE);
        return aeState == CaptureRequest.CONTROL_AE_STATE_LOCKED;
    }

    /**
     * Crop and rotate JPEG frame.
     *
     * @param srcJpeg JPEG frame buffer.
     * @param rotation Get from getFrameOrientation()
     * @param cropAspectWH Must be greater than 1.0f.
     * @param jpegQuality ResultJPEG quality
     * @return Result JPEG frame buffer.
     */
    static byte[] doCropRotJpeg(
            byte[] srcJpeg,
            int rotation,
            float cropAspectWH,
            int jpegQuality) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "doCropRotJpeg() : E");

        // Create source bitmap.
        Bitmap srcBmp = BitmapFactory.decodeByteArray(srcJpeg, 0, srcJpeg.length);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "    Decode Src Bitmap : DONE");

        // Create rotated source bitmap.
        Matrix rotator = new Matrix();
        rotator.postRotate(rotation);
        Bitmap rotSrcBmp = Bitmap.createBitmap(
                srcBmp,
                0,
                0,
                srcBmp.getWidth(),
                srcBmp.getHeight(),
                rotator,
                true);
        if (srcBmp.hashCode() != rotSrcBmp.hashCode()) {
            srcBmp.recycle();
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "    Create Rot Src Bitmap : DONE");

        // Create aspect considered bitmap.
        float w = (float) rotSrcBmp.getWidth();
        float h = (float) rotSrcBmp.getHeight();
        float srcAspectWH = w / h;
        float dstAspectWH;
        if (1.0f < srcAspectWH) {
            // Horizontal.
            dstAspectWH = cropAspectWH;
        } else {
            // Vertical.
            dstAspectWH = 1.0f / cropAspectWH;
        }

        Rect dstRect;
        if (((int) (srcAspectWH * 100)) == ((int) (dstAspectWH * 100))) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "    Src/Dst aspect is same.");
            // Already same aspect.

            dstRect = new Rect(0, 0, (int) w, (int) h);
        } else {
            if (srcAspectWH < dstAspectWH) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "    Cut off top and bottom.");
                // Cut off top and bottom.

                float dstH = w / dstAspectWH;
                dstRect = new Rect(
                        0,
                        (int) ((h - dstH) / 2.0f),
                        (int) w,
                        (int) ((h - dstH) / 2.0f + dstH));
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "    Cut off left and right.");
                // Cut off left and right.

                float dstW = h * dstAspectWH;
                dstRect = new Rect(
                        (int) ((w - dstW) / 2.0f),
                        0,
                        (int) (((w - dstW) / 2.0f) + dstW),
                        (int) h);
            }
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "    Calculate DstRect : DONE");
        if (Log.IS_DEBUG) Log.logDebug(TAG, "    DstRect = " + dstRect.toShortString());

        // Create destination bitmap.
        Bitmap dstBmp = Bitmap.createBitmap(
                rotSrcBmp,
                dstRect.left,
                dstRect.top,
                dstRect.width(),
                dstRect.height());
        if (rotSrcBmp.hashCode() != dstBmp.hashCode()) {
            rotSrcBmp.recycle();
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "    Create Dst Bitmap : DONE");

        // JPEG Encode.
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        dstBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, os);
        byte[] resultJpeg = os.toByteArray();
        if (Log.IS_DEBUG) Log.logDebug(TAG, "    JPEG Encode : DONE");

        // Release.
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!srcBmp.isRecycled()) {
            srcBmp.recycle();
        }
        if (!rotSrcBmp.isRecycled()) {
            rotSrcBmp.recycle();
        }
        if (!dstBmp.isRecycled()) {
            dstBmp.recycle();
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "doCropRotJpeg() : X");
        return resultJpeg;
    }
}
