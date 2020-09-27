@file:Suppress("DEPRECATION", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.device.api1

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.SensorManager
import android.os.Handler
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager

import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.lib.util.media.ImageProc
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.device.CameraPlatformInterface

import java.io.IOException
import java.util.HashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Camera functions based on Camera API 1.0.
 *
 * @constructor
 * @param context Master context.
 */
class Camera1Device(private val context: Context) : CameraPlatformInterface {

    // UI thread handler.
    private var uiWorker: Handler? = null

    // Target device ID.
    private val cameraId = Camera.CameraInfo.CAMERA_FACING_BACK

    // Camera API 1.0 related.
    private var camera: Camera? = null
    private var evfAspectWH = 1.0f
    private var scanTask: ScanTask? = null
    private lateinit var previewSize: PlatformDependencyResolver.Size

    // Snapshot request ID.
    private var requestId = 0

    // Back worker.
    private var backWorker: ExecutorService? = null

    // Orientation.
    private var orientationDegree = OrientationEventListener.ORIENTATION_UNKNOWN
    private var orientationEventListenerImpl: OrientationEventListenerImpl? = null

    private class BackWorkerThreadFactoryImpl : ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            val thread = Thread(r, TAG)
            thread.priority = Thread.MAX_PRIORITY
            return thread
        }
    }

    private inner class OrientationEventListenerImpl(context: Context, rate: Int)
            : OrientationEventListener(context, rate) {
        override fun onOrientationChanged(orientation: Int) {
            orientationDegree = orientation
        }
    }

    private fun generateBackWorker() {
        backWorker = Executors.newSingleThreadExecutor(backWorkerThreadFactoryImpl)
    }

    private fun shutdownBackWorker() {
        backWorker?.let { back ->
            back.shutdown()
            try {
                back.awaitTermination(3000, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            backWorker = null
        }
    }

    override fun prepare() {
        uiWorker = App.ui

        generateBackWorker()
    }

    override fun release() {
        shutdownBackWorker()

        uiWorker = null
    }

    override fun openAsync(evfAspectWH: Float, openCallback: CameraPlatformInterface.OpenCallback) {
        this.evfAspectWH = evfAspectWH
        val task = OpenTask(openCallback)
        backWorker?.execute(task)
    }

    private inner class OpenTask(private val openCallback: CameraPlatformInterface.OpenCallback) : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "OpenTask.run() : E")

            if (camera == null) {
                // Open.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.open() : E")
                camera = try {
                    Camera.open(cameraId)
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                    null
                }

                if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.open() : X")

                if (camera == null) {
                    // Failed to open.
                    // TODO:Re-Try ?

                    // Notify.
                    openCallback.onOpened(false)
                } else {
                    // Parameters.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.getParameters() : E")
                    val params = camera?.parameters
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.getParameters() : X")

                    val supportedSizes = HashSet<PlatformDependencyResolver.Size>()

                    // Preview size.
                    supportedSizes.clear()
                    params?.let { p ->
                        for (eachSize in p.supportedPreviewSizes) {
                            val supported = PlatformDependencyResolver.Size(
                                    eachSize.width,
                                    eachSize.height)
                            supportedSizes.add(supported)
                        }
                    }
                    previewSize = PlatformDependencyResolver.getOptimalPreviewSizeForStill(
                            evfAspectWH,
                            supportedSizes)

                    // Picture size.
                    supportedSizes.clear()
                    params?.let { p ->
                        for (eachSize in p.supportedPictureSizes) {
                            val supported = PlatformDependencyResolver.Size(
                                    eachSize.width,
                                    eachSize.height)
                            supportedSizes.add(supported)
                        }
                    }
                    val pictureSize = PlatformDependencyResolver.getOptimalPictureSize(
                            evfAspectWH,
                            supportedSizes)

                    // Parameters.
                    params?.let { p ->
                        p.setPreviewSize(previewSize.width, previewSize.height)
                        p.setPictureSize(pictureSize.width, pictureSize.height)
                        for (eachFocusMode in p.supportedFocusModes) {
                            if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE == eachFocusMode) {
                                p.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                                break
                            }
                        }
                    }

                    // Set.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.setParameters() : E")
                    doSetParameters(camera, params)
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.setParameters() : X")

                    // Start preview.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.startPreview() : E")
                    camera?.startPreview()
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.startPreview() : X")

                    // Request ID.
                    requestId = 0

                    // Orientation.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Create OrientationListenerImpl : E")
                    orientationEventListenerImpl = OrientationEventListenerImpl(
                            context,
                            SensorManager.SENSOR_DELAY_NORMAL)
                    orientationEventListenerImpl?.enable()
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Create OrientationListenerImpl : X")

                    // Notify.
                    openCallback.onOpened(true)
                }
            } else {
                // Notify. Already opened.
                openCallback.onOpened(true)
            }
            if (Log.IS_DEBUG) Log.logDebug(TAG, "OpenTask.run() : X")
        }
    }

    private fun doSetParameters(camera: Camera?, params: Camera.Parameters?) {
        if (camera == null || params == null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "camera or params is null")
            return
        }

        if (Log.IS_DEBUG) {
            val splitParams = params.flatten().split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            android.util.Log.e("TraceLog", "############ CameraParameters DEBUG")
            for (str in splitParams) {
                android.util.Log.e("TraceLog", "###### CameraParameters : $str")
            }
            android.util.Log.e("TraceLog", "############")
        }

        try {
            camera.parameters = params
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }

    }

    override fun closeAsync(closeCallback: CameraPlatformInterface.CloseCallback) {
//        Runnable removeSurfaceTask = new SetSurfaceTask(null);
//        backWorker.execute(removeSurfaceTask);
        val closeTask = CloseTask(closeCallback)
        backWorker?.execute(closeTask)
    }

    private inner class CloseTask(private val closeCallback: CameraPlatformInterface.CloseCallback) : Runnable {

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CloseTask.run() : E")

            // Camera.
            camera?.let { c ->
                c.stopPreview()
                c.release()
                camera = null
            }

            // Orientation.
            orientationEventListenerImpl?.let { o ->
                o.disable()
                orientationEventListenerImpl = null
            }

            // Notify.
            closeCallback.onClosed(true)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "CloseTask.run() : X")
        }
    }

    override fun bindPreviewSurfaceAsync(
            textureView: TextureView,
            bindSurfaceCallback: CameraPlatformInterface.BindSurfaceCallback) {

        val previewWidth = previewSize.width
        val previewHeight = previewSize.height
        val finderWidth = textureView.width
        val finderHeight = textureView.height

        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "  Preview Frame Size = $previewWidth x $previewHeight")
            Log.logDebug(TAG, "  Finder Size = $finderWidth x $finderHeight")
        }

        // Transform matrix.
        val matrix = PlatformDependencyResolver.getTextureViewTransformMatrix(
                context,
                previewWidth,
                previewHeight,
                finderWidth,
                finderHeight)

        val uiTask = SetTextureViewTransformTask(textureView, matrix)
        uiWorker?.post(uiTask)

        val task = BindSurfaceTask(textureView.surfaceTexture, bindSurfaceCallback)
        backWorker?.execute(task)
    }

    private inner class SetTextureViewTransformTask(
            private val texView: TextureView,
            private val matrix: Matrix)
            : Runnable {
        override fun run() {
            texView.setTransform(matrix)
        }
    }

    private inner class BindSurfaceTask(
            private val surface: SurfaceTexture,
            private val bindSurfaceCallback: CameraPlatformInterface.BindSurfaceCallback)
            : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "SetSurfaceTask.run() : E")

            camera?.let { c ->
                // Orientation.
                val camInfo = Camera.CameraInfo()
                Camera.getCameraInfo(cameraId, camInfo)
                val winMng = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val rotation = winMng.defaultDisplay.rotation
                val degrees: Int
                degrees = when (rotation) {
                    Surface.ROTATION_0 -> 0
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> throw IllegalStateException("Unexpected rotation.")
                }
                var resultRotation: Int
                when (camInfo.facing) {
                    Camera.CameraInfo.CAMERA_FACING_BACK -> {
                        resultRotation = (camInfo.orientation - degrees + 360) % 360
                    }

                    Camera.CameraInfo.CAMERA_FACING_FRONT -> {
                        resultRotation = (camInfo.orientation + degrees) % 360
                        resultRotation = (360 - resultRotation) % 360
                    }

                    else -> throw IllegalStateException("Unexpected facing.")
                }
                c.setDisplayOrientation(resultRotation)

                try {
                    c.setPreviewTexture(surface)
                } catch (e: IOException) {
                    e.printStackTrace()

                    bindSurfaceCallback.onSurfaceBound(false)
                }

                bindSurfaceCallback.onSurfaceBound(true)
            } ?: run {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.")
                bindSurfaceCallback.onSurfaceBound(false)
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "SetSurfaceTask.run() : X")
        }
    }

    override fun requestScanAsync(scanCallback: CameraPlatformInterface.ScanCallback) {
        ScanTask(scanCallback).let { task ->
            backWorker?.execute(task)
            scanTask = task
        }
    }

    private inner class ScanTask(private val scanCallback: CameraPlatformInterface.ScanCallback) : Runnable {
        private val latch = CountDownLatch(1)
        private var isCanceled = false

        fun cancel() {
            isCanceled = true
            latch.countDown()
        }

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ScanTask.run() : E")

            camera?.let { c ->
                isCanceled = false
                val focusCallbackImpl = FocusCallbackImpl(latch)

                // Do scan.
                c.autoFocus(focusCallbackImpl)

                try {
                    latch.await()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    throw UnsupportedOperationException("Why thread is interrupted ?")
                }

                if (!isCanceled) {
                    // Lock AE and AWB.
                    val params = c.parameters
                    if (params.isAutoExposureLockSupported) {
                        params.autoExposureLock = true
                    }
                    if (params.isAutoWhiteBalanceLockSupported) {
                        params.autoWhiteBalanceLock = true
                    }
                    c.parameters = params

                    scanCallback.onScanDone(focusCallbackImpl.isSuccess)
                }
            } ?: run {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.")
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "ScanTask.run() : X")
        }
    }

    private inner class FocusCallbackImpl(private val latch: CountDownLatch) : Camera.AutoFocusCallback {
        var isSuccess = false
            private set

        override fun onAutoFocus(isSuccess: Boolean, camera: Camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onAutoFocus() : [isSuccess=$isSuccess]")

            this.isSuccess = isSuccess
            latch.countDown()
        }
    }

    override fun requestCancelScanAsync(cancelScanCallback: CameraPlatformInterface.CancelScanCallback) {
        // Cancel scan task.
        scanTask?.cancel()

        backWorker?.execute(CancelScanTask(cancelScanCallback))
    }

    private inner class CancelScanTask(private val cancelScanCallback: CameraPlatformInterface.CancelScanCallback) : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : E")

            doCancelScan()

            cancelScanCallback.onCancelScanDone()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : X")
        }
    }

    private fun doCancelScan() {
        camera?.let { c ->
            c.cancelAutoFocus()

            // Unlock AE and AWB.
            val params = c.parameters
            if (params.isAutoExposureLockSupported) {
                params.autoExposureLock = false
            }
            if (params.isAutoWhiteBalanceLockSupported) {
                params.autoWhiteBalanceLock = false
            }
            c.parameters = params
        }
    }

    override fun requestStillCaptureAsync(stillCaptureCallback: CameraPlatformInterface.StillCaptureCallback): Int {
        ++requestId
        val stillCaptureTask = StillCaptureTask(requestId, stillCaptureCallback)
        backWorker?.execute(stillCaptureTask)
        return requestId
    }

    private inner class StillCaptureTask(
            private val requestId: Int,
            private val stillCaptureCallback: CameraPlatformInterface.StillCaptureCallback)
            : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : E")

            camera?.let { c ->
                // Parameters.
                val params = c.parameters
                // Orientation.
                val camInfo = Camera.CameraInfo()
                Camera.getCameraInfo(cameraId, camInfo)
                val orientation = (orientationDegree + 45) / 90 * 90
                val rotationDeg: Int
                rotationDeg = when (camInfo.facing) {
                    Camera.CameraInfo.CAMERA_FACING_BACK -> {
                        (camInfo.orientation + orientation) % 360
                    }

                    Camera.CameraInfo.CAMERA_FACING_FRONT -> {
                        (camInfo.orientation - orientation + 360) % 360
                    }

                    else -> throw IllegalArgumentException("Unexpected facing.")
                }
                params.setRotation(rotationDeg)
                c.parameters = params

                // Do capture.
                val latch = CountDownLatch(1)
                val pictCallback = PictureCallbackImpl(latch)
                c.takePicture(
                        null, // new ShutterCallbackImpl(),
                        null,
                        pictCallback)

                try {
                    latch.await()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    throw UnsupportedOperationException("Why thread is interrupted ?")
                }

                // Restart preview.
                c.startPreview()

                // Reset lock state.
                doCancelScan()

                // Process JPEG and notify.
                val task = HandleJpegTask(
                        pictCallback.jpegBuffer,
                        rotationDeg,
                        evfAspectWH,
                        JPEG_QUALITY)
                backWorker!!.execute(task)
            } ?: run {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.")
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : X")
        }

//        private inner class ShutterCallbackImpl : Camera.ShutterCallback {
//            override fun onShutter() {
//                if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutter()");
//
//                // Notify to controller.
//                stillCaptureCallback.onShutterDone(requestId);
//            }
//        }

        private inner class PictureCallbackImpl(private val latch: CountDownLatch) : Camera.PictureCallback {
            lateinit var jpegBuffer: ByteArray
                private set

            override fun onPictureTaken(jpegBuffer: ByteArray, camera: Camera) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onPictureTaken()")

                // Notify to controller.
                stillCaptureCallback.onShutterDone(requestId)

                this.jpegBuffer = jpegBuffer

                latch.countDown()

                // Notify to controller.
                stillCaptureCallback.onCaptureDone(requestId)
            }
        }

        private inner class HandleJpegTask(
                private val srcJpeg: ByteArray,
                private val rotationDeg: Int,
                private val cropAspectWH: Float,
                private val jpegQuality: Int) : Runnable {
            private lateinit var resultJpeg: ByteArray

            override fun run() {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "HandleJpegTask.run() : E")

                resultJpeg = ImageProc.doCropRotJpeg(
                        srcJpeg,
                        rotationDeg,
                        cropAspectWH,
                        jpegQuality)

                uiWorker?.post(NotifyResultJpegTask())

                if (Log.IS_DEBUG) Log.logDebug(TAG, "HandleJpegTask.run() : X")
            }

            private inner class NotifyResultJpegTask : Runnable {
                override fun run() {
                    // Notify to controller.
                    stillCaptureCallback.onPhotoStoreReady(requestId, resultJpeg)
                }
            }
        }
    }

    private var recFileFullPath: String = ""
    private var recCallback: CameraPlatformInterface.RecCallback? = null

    override fun requestStartRecAsync(
            recFileFullPath: String,
            recCallback: CameraPlatformInterface.RecCallback) {
        this.recFileFullPath = recFileFullPath
        this.recCallback = recCallback
        val startRecTask = StartRecTask(recCallback)
        backWorker?.execute(startRecTask)
    }

    private inner class StartRecTask(
            private val callback: CameraPlatformInterface.RecCallback) : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StartRecTask.run() : E")




            callback.onRecStarted()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StartRecTask.run() : X")
        }
    }

    override fun requestStopRecAsync() {
        recCallback?.let { callback ->
            val stopRecTask = StopRecTask(callback)
            backWorker?.execute(stopRecTask)
            recCallback = null
        }
    }

    private inner class StopRecTask(
            private val callback: CameraPlatformInterface.RecCallback) : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StopRecTask.run() : E")





            callback.onRecStopped(recFileFullPath)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StopRecTask.run() : X")
        }
    }

    companion object {
        // Log tag.
        private const val TAG = "Camera1Device"

        private const val JPEG_QUALITY = 95

        // Back worker thread factory.
        private val backWorkerThreadFactoryImpl = BackWorkerThreadFactoryImpl()
    }
}
