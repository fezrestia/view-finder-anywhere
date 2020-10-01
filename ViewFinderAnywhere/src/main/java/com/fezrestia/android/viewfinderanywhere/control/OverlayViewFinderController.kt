@file:Suppress("PrivatePropertyName", "ConstantConditionIf", "unused", "PropertyName")

package com.fezrestia.android.viewfinderanywhere.control

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager
import com.fezrestia.android.viewfinderanywhere.config.options.CameraApiLevel
import com.fezrestia.android.viewfinderanywhere.device.CameraPlatformInterface
import com.fezrestia.android.viewfinderanywhere.device.api1.Camera1Device
import com.fezrestia.android.viewfinderanywhere.device.api2.Camera2Device
import com.fezrestia.android.viewfinderanywhere.device.codec.MpegRecorder
import com.fezrestia.android.viewfinderanywhere.storage.StorageController
import com.fezrestia.android.viewfinderanywhere.view.OverlayViewFinderRootView

/**
 * Main controller class.
 *
 * @constructor
 * @param context Context Master context
 */
class OverlayViewFinderController(private val context: Context) {
    private val TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS = 1000L

    // Core instances.
    private lateinit var rootView: OverlayViewFinderRootView
    private lateinit var configManager: ConfigManager
    private lateinit var camera: CameraPlatformInterface
    private var cameraSurfaceTexture: SurfaceTexture? = null

    var currentState: State = StateFinalized()
            private set

    private lateinit var storageController: StorageController

    private var mpegRecorder: MpegRecorder? = null

    private val forceStopTask = ForceStopTask()

    /**
     * Primary CONSTRUCTOR.
     */
    init {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR() : E")

        nativeOnCreated()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR() : X")
    }

    /**
     * Set core instance dependency.
     *
     * @param rootView RootView
     * @param configManager ConfigManager
     */
    fun setCoreInstances(
            rootView: OverlayViewFinderRootView,
            configManager: ConfigManager) {
        this.rootView = rootView
        this.configManager = configManager
    }

    /**
     * Release all references.
     */
    fun release() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : E")

        nativeOnDestroyed()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : X")
    }

    /**
     * Start overlay view finder.
     */
    fun start() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E")

        // Initial state.
        currentState = StateFinalized()

        // Camera.
        camera = when (configManager.camApiLv) {
            CameraApiLevel.API_1 -> Camera1Device(context)
            CameraApiLevel.API_2 -> Camera2Device(context, App.ui)
        }
        camera.prepare()

        // Storage.
        storageController = StorageController(
                context,
                App.ui,
                StorageControllerCallback())

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X")
    }

    /**
     * Resume overlay view finder.
     */
    fun resume() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : E")

        // State.
        currentState.onResume()

        // Storage selector.
        if (App.isStorageSelectorEnabled) {
            // Start storage selector.
            OnOffTrigger.openStorageSelector(context)
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : X")
    }

    /**
     * Pause overlay view finder.
     */
    fun pause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : E")

        // State.
        currentState.onPause()

        // Close storage selector.
        OnOffTrigger.closeStorageSelector(context)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : X")
    }

    /**
     * Stop overlay view finder.
     */
    fun stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E")

        // Reset state.
        currentState = StateFinalized()

        // Release camera.
        camera.release()

        // Storage.
        storageController.release()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X")
    }

    private interface StateInternalInterface {
        val isActive: Boolean
        fun entry()
        fun exit()
    }

    private interface LifeCycleInterface {
        fun onResume()
        fun onPause()
        fun onToggleShowHideRequired()
    }

    private interface FromExternalEnvironment {
        fun requestForceStop()
    }

    private interface FromViewInterface {
        fun onPreOpenRequested()
        fun onPreOpenCanceled()
        fun onSurfaceCreated()
        fun onSurfaceReady()
        fun onSurfaceReleased()
        fun requestScan()
        fun requestCancelScan()
        fun requestStillCapture()
        fun requestStartRec()
        fun requestStopRec()
    }

    private interface FromDeviceInterface {
        fun onCameraOpened(isSuccess: Boolean)
        fun onCameraClosed()
        fun onScanDone(isSuccess: Boolean)
        fun onCancelScanDone()
        fun onShutterDone()
        fun onStillCaptureDone()
        fun onPhotoStoreReady(data: ByteArray)
        fun onVideoStreamStarted()
        fun onVideoStreamStopped()
    }

    private interface FromStorageInterface {
        fun onPhotoStoreDone(isSuccess: Boolean, uri: Uri?)
    }

    private interface FromMpegRecorderInterface {
        fun onRecStarted()
        fun onRecStopped()
    }

    abstract inner class State :
            StateInternalInterface,
            LifeCycleInterface,
            FromExternalEnvironment,
            FromViewInterface,
            FromDeviceInterface,
            FromStorageInterface,
            FromMpegRecorderInterface {
        private val TAG = "State"

        override val isActive: Boolean
            get() {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "isActive() : NOP")
                return false
            }

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry() : NOP")
        }

        override fun exit() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "exit() : NOP")
        }

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume() : NOP")
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause() : NOP")
        }

        override fun onToggleShowHideRequired() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onToggleShowHideRequired() : NOP")
        }

        override fun requestForceStop() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestForceStop() : NOP")
        }

        override fun onPreOpenRequested() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenRequested() : NOP")
        }

        override fun onPreOpenCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenCanceled() : NOP")
        }

        override fun onSurfaceCreated() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceCreated() : NOP")
        }

        override fun onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady() : NOP")
        }

        override fun onSurfaceReleased() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReleased() : NOP")
        }

        override fun onCameraOpened(isSuccess: Boolean) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraOpened() : NOP")
        }

        override fun onCameraClosed() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraClosed() : NOP")
        }

        override fun requestScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestScan() : NOP")
        }

        override fun requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan() : NOP")
        }

        override fun requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture() : NOP")
        }

        override fun requestStartRec() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStartRec() : NOP")
        }

        override fun requestStopRec() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStopRec() : NOP")
        }

        override fun onScanDone(isSuccess: Boolean) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanDone() : NOP")
        }

        override fun onCancelScanDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCancelScanDone() : NOP")
        }

        override fun onShutterDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutterDone() : NOP")
        }

        override fun onStillCaptureDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStillCaptureDone() : NOP")
        }

        override fun onPhotoStoreReady(data: ByteArray) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPhotoStoreReady() : NOP")
        }

        override fun onPhotoStoreDone(isSuccess: Boolean, uri: Uri?) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPhotoStoreDone() : NOP")
        }

        override fun onVideoStreamStarted() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onVideoStreamStarted() : NOP")
        }

        override fun onVideoStreamStopped() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onVideoStreamStopped() : NOP")
        }

        override fun onRecStarted() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onRecStarted() : NOP")
        }

        override fun onRecStopped() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onRecStopped() : NOP")
        }
    }

    @Synchronized
    private fun changeStateTo(next: State) {
        if (Log.IS_DEBUG) Log.logDebug(TAG,"changeStateTo() : NEXT=${next::class.simpleName}")

        currentState.exit()
        currentState = next
        currentState.entry()
    }

    private open inner class StateAllFallback : State() {
        private val TAG = "StateAllFallback"

        override fun requestForceStop() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestForceStop()")

            // Force stop overlay.
            rootView.forceStop()

            changeStateTo(StateFinalized())
        }
    }

    private inner class StateFinalized : StateAllFallback() {
        private val TAG = "StateFinalized"

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()")

            camera.closeAsync(CloseCallbackImpl())
        }

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            changeStateTo(StateInitialized())
        }

        override fun onPreOpenRequested() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenRequested()")

            camera.openAsync(configManager.evfAspect.ratioWH, OpenCallbackImpl())
        }

        override fun onPreOpenCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenCanceled()")

            camera.closeAsync(CloseCallbackImpl())
        }

        override fun onCameraOpened(isSuccess: Boolean) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraOpened()")

            if (isSuccess) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Success to open camera.")
                // NOP.
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Failed to open camera.")
                // NOP.
            }
        }

        override fun onCameraClosed() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraClosed()")

            mpegRecorder?.release()
            mpegRecorder = null

            releaseCameraStreamTexture()
        }

        override fun onToggleShowHideRequired() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onToggleShowHideRequired()")

            if (rootView.isOverlayShown()) {
                rootView.hide()
            } else {
                rootView.show()
            }
        }

        override fun onSurfaceReleased() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReleased()")

            nativeOnUiSurfaceFinalized()
        }
    }

    private inner class StateInitialized : StateAllFallback() {
        private val TAG = "StateInitialized"

        private var isCameraAlreadyReady = false
        private var isSurfaceAlreadyReady = false

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()")

            camera.openAsync(configManager.evfAspect.ratioWH, OpenCallbackImpl())
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            changeStateTo(StateFinalized())
        }

        override fun onSurfaceCreated() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceCreated()")

            // Start initialization of native GL/EGL related.
            val uiSurface = Surface(rootView.getViewFinderSurface().surfaceTexture)
            nativeOnUiSurfaceInitialized(uiSurface)
        }

        override fun onCameraOpened(isSuccess: Boolean) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraOpened()")

            if (isSuccess) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Success to open camera")

                isCameraAlreadyReady = true
                checkCameraAndSurfaceAndStartViewFinder()
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Failed to open camera")

                // Camera can not be used.
                App.ui.postDelayed(
                        forceStopTask,
                        TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS)
            }
        }

        override fun onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady()")

            isSurfaceAlreadyReady = true

            checkCameraAndSurfaceAndStartViewFinder()
        }

        private fun checkCameraAndSurfaceAndStartViewFinder() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "checkCameraAndSurfaceAndStartViewFinder()")

            if (isCameraAlreadyReady && isSurfaceAlreadyReady) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera and surface are ready.")

                // Camera stream surface.
                run {
                    prepareCameraStreamTexture(
                            camera.getPreviewStreamSize(),
                            camera.getSensorOrientation())

                    val texView = TextureView(context)
                    texView.surfaceTexture = cameraSurfaceTexture

                    camera.bindPreviewSurfaceAsync(
                            texView,
                            BindSurfaceCallbackImpl())
                }

                // Video.
                run {
                    val videoSize = calcVideoSize(
                            camera.getPreviewStreamSize(),
                            configManager.evfAspect.ratioWH)
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## Video Size = $videoSize")

                    mpegRecorder = MpegRecorder(
                            context,
                            videoSize,
                            MpegRecorderCallbackImpl(),
                            App.ui)
                }

                changeStateTo(StateIdle())
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Not camera and surface are ready yet.")
            }
        }
    }

    private inner class StateIdle : StateAllFallback() {
        private val TAG = "StateIdle"

        override val isActive = true

        override fun entry() {
            App.ui.post(VisualFeedbackClearTask())
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            changeStateTo(StateFinalized())
        }

        override fun requestScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestScan()")

            camera.requestScanAsync(ScanCallbackImpl())

            changeStateTo(StateDoingScan())
        }

        override fun requestStartRec() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStartRec()")

            changeStateTo(StateRec())
        }
    }

    private inner class StateDoingScan : StateAllFallback() {
        private val TAG = "StateDoingScan"

        private var isStillCaptureAlreadyRequested = false

        override val isActive = true

        override fun entry() {
            App.ui.post(VisualFeedbackOnScanStartedTask())
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            camera.requestCancelScanAsync(CancelScanCallbackImpl())

            changeStateTo(StateFinalized())
        }

        override fun requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan()")

            camera.requestCancelScanAsync(CancelScanCallbackImpl())
            changeStateTo(StateCancellingScan())
        }

        override fun onScanDone(isSuccess: Boolean) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanDone()")

            if (isStillCaptureAlreadyRequested) {
                changeStateTo(StateScanDone(isSuccess))
                changeStateTo(StateStillCapturing())
                camera.requestStillCaptureAsync(StillCaptureCallbackImpl())
            } else {
                changeStateTo(StateScanDone(isSuccess))
            }
        }

        override fun requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture()")

            isStillCaptureAlreadyRequested = true
        }
    }

    private inner class StateCancellingScan : StateAllFallback() {
        private val TAG = "StateCancellingScan"

        override val isActive = true

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            changeStateTo(StateFinalized())
        }

        override fun onCancelScanDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCancelScanDone()")

            changeStateTo(StateIdle())
        }
    }

    private inner class StateScanDone(private val isSuccess: Boolean) : StateAllFallback() {
        private val TAG = "StateScanDone"

        override val isActive = true

        override fun entry() {
            App.ui.post(VisualFeedbackOnScanDoneTask(isSuccess))
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            camera.requestCancelScanAsync(CancelScanCallbackImpl())

            changeStateTo(StateFinalized())
        }

        override fun requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan()")

            camera.requestCancelScanAsync(CancelScanCallbackImpl())
        }

        override fun requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture()")

            camera.requestStillCaptureAsync(StillCaptureCallbackImpl())

            changeStateTo(StateStillCapturing())
        }

        override fun onCancelScanDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCancelScanDone()")

            changeStateTo(StateIdle())
        }
    }

    private inner class StateStillCapturing : StateAllFallback() {
        private val TAG = "StateStillCapturing"

        private var isPauseRequired = false

        override val isActive = true

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            // Pause request is canceled.

            isPauseRequired = false
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            // Do not pause here. Close device after still capture is assured.

            isPauseRequired = true
        }

        override fun onShutterDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutterDone()")

            App.ui.post(VisualFeedbackClearTask())
            App.ui.post(VisualFeedbackOnShutterDoneTask())
        }

        override fun onStillCaptureDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStillCaptureDone()")

            if (isPauseRequired) {
                changeStateTo(StateFinalized())
            } else {
                // Normal sequence.
                changeStateTo(StateIdle())
            }
        }
    }

    private inner class StateRec : StateAllFallback() {
        private val TAG = "StateRec"

        val mpegFileFullPath = storageController.getVideoFileFullPath()

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()")

            mpegRecorder?.let {
                it.setup(mpegFileFullPath)
                nativeSetEncoderSurface(it.getVideoInputSurface())
                it.start()
            }
            camera.requestStartVideoStreamAsync(VideoCallbackImpl())
        }

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            // TODO: Consider pause/resume during rec.

        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            // TODO: Consider pause/resume during rec.

        }

        override fun onVideoStreamStarted() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onVideoStreamStarted()")

            rootView.getVisualFeedbackTrigger().onRecStarted()

            nativeStartVideoEncode()
        }

        override fun onVideoStreamStopped() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onVideoStreamStopped()")

            mpegRecorder?.stop()
        }

        override fun onRecStarted() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onRecStarted()")
            // NOP.
        }

        override fun onRecStopped() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onRecStopped()")

            storageController.notifyToMediaScanner(mpegFileFullPath)

            nativeReleaseEncoderSurface()

            mpegRecorder?.reset()

            rootView.getVisualFeedbackTrigger().onRecStopped()

            changeStateTo(StateIdle())
        }

        override fun requestStopRec() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStopRec()")

            nativeStopVideoEncode()

            camera.requestStopVideoStreamAsync()
        }
    }

    private inner class ForceStopTask : Runnable {
        override fun run() {
            rootView.forceStop()
        }
    }

    private inner class VisualFeedbackOnScanStartedTask : Runnable {
        override fun run() {
            rootView.getVisualFeedbackTrigger().onScanStarted()
        }
    }

    private inner class VisualFeedbackOnScanDoneTask(private val isSuccess: Boolean) : Runnable {
        override fun run() {
            rootView.getVisualFeedbackTrigger().onScanDone(isSuccess)
        }
    }

    private inner class VisualFeedbackOnShutterDoneTask : Runnable {
        override fun run() {
            rootView.getVisualFeedbackTrigger().onShutterDone()
        }
    }

    private inner class VisualFeedbackClearTask : Runnable {
        override fun run() {
            rootView.getVisualFeedbackTrigger().clear()
        }
    }

    //// CAMERA PLATFORM INTERFACE CALLBACK ///////////////////////////////////////////////////////

    private inner class OpenCallbackImpl : CameraPlatformInterface.OpenCallback {
        override fun onOpened(isSuccess: Boolean) {
            currentState.onCameraOpened(isSuccess)
        }
    }

    private inner class CloseCallbackImpl : CameraPlatformInterface.CloseCallback {
        override fun onClosed(isSuccess: Boolean) {
            currentState.onCameraClosed()
        }
    }

    private inner class BindSurfaceCallbackImpl : CameraPlatformInterface.BindSurfaceCallback {
        override fun onSurfaceBound(isSuccess: Boolean) {
            if (!isSuccess) throw RuntimeException("Failed to bind surface.")
        }
    }

    private inner class ScanCallbackImpl : CameraPlatformInterface.ScanCallback {
        override fun onScanDone(isSuccess: Boolean) {
            currentState.onScanDone(isSuccess)
        }
    }

    private inner class CancelScanCallbackImpl : CameraPlatformInterface.CancelScanCallback {
        override fun onCancelScanDone() {
            currentState.onCancelScanDone()
        }
    }

    private inner class StillCaptureCallbackImpl : CameraPlatformInterface.StillCaptureCallback {
        override fun onShutterDone(requestId: Int) {
            currentState.onShutterDone()

            // Firebase analytics.
            App.firebase.createNewLogRequest()
                    .setEvent(Constants.FIREBASE_EVENT_ON_SHUTTER_DONE)
                    .done()
        }

        override fun onCaptureDone(requestId: Int) {
            currentState.onStillCaptureDone()
        }

        override fun onPhotoStoreReady(requestId: Int, data: ByteArray) {
            // Request store.
            storageController.storePicture(data)

            currentState.onPhotoStoreReady(data)
        }
    }

    private inner class StorageControllerCallback : StorageController.Callback {
        override fun onPhotoStoreDone(isSuccess: Boolean, uri: Uri?) {
            currentState.onPhotoStoreDone(isSuccess, uri)
        }
    }

    private inner class VideoCallbackImpl : CameraPlatformInterface.VideoCallback {
        override fun onVideoStreamStarted() {
            currentState.onVideoStreamStarted()
        }

        override fun onVideoStreamStopped() {
            currentState.onVideoStreamStopped()
        }
    }

    private inner class MpegRecorderCallbackImpl : MpegRecorder.Callback {
        override fun onRecStarted() {
            currentState.onRecStarted()
        }

        override fun onRecStopped(recFileFullPath: String) {
            currentState.onRecStopped()
        }
    }

    private fun prepareCameraStreamTexture(
            frameSize: Size,
            frameOrientation: Int) {
        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "## frameSize = $frameSize")
            Log.logDebug(TAG, "## frameOrientation = $frameOrientation")
        }

        cameraSurfaceTexture?.let {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "cameraSurfaceTexture exists")
            // NOP.
        } ?: run {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "cameraSurfaceTexture N/A")

            val cameraAspect = frameSize.width.toFloat() / frameSize.height.toFloat()
            nativeSetCameraStreamAspectWH(cameraAspect)

            nativePrepareCameraStreamTexture()
            val texId = nativeGetCameraStreamTextureId()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera Stream Tex ID = $texId")

            cameraSurfaceTexture = SurfaceTexture(texId)
            cameraSurfaceTexture?.setOnFrameAvailableListener(CameraSurfaceTextureCallback())

            val displayRot = getDisplayRotDeg()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "displayRot = $displayRot")

            nativeSetCameraStreamRotDeg(displayRot)
        }
    }

    private fun getDisplayRotDeg(): Int {
        return when (rootView.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun calcVideoSize(previewSize: Size, requestAspectWH: Float): Size {
        val previewAspectWH = previewSize.width.toFloat() / previewSize.height.toFloat()

        return if (previewAspectWH < requestAspectWH) {
            // Cut off top/bottom.
            val w = previewSize.width
            val h = (w.toFloat() / requestAspectWH).toInt()
            Size(w, h)
        } else {
            // Cut off left/right.
            val h = previewSize.height
            val w = (h.toFloat() * requestAspectWH).toInt()
            Size(w, h)
        }
    }

    private fun releaseCameraStreamTexture() {
        cameraSurfaceTexture?.let { it ->
            it.setOnFrameAvailableListener(null)
            it.release()
            cameraSurfaceTexture = null
        }
    }

    private inner class CameraSurfaceTextureCallback : SurfaceTexture.OnFrameAvailableListener {
        val TAG = "CameraSurfaceTextureCallback"
        override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onFrameAvailable() : E")

            nativeBindAppEglContext()

            surfaceTexture.updateTexImage()

            val matrix = FloatArray(16)
            surfaceTexture.getTransformMatrix(matrix)

            nativeSetCameraStreamTransformMatrix(matrix)

            nativeOnCameraStreamUpdated()

            nativeUnbindAppEglContext()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "onFrameAvailable() : X")
        }
    }

    private external fun nativeOnCreated(): Int
    private external fun nativeOnDestroyed(): Int

    private external fun nativeOnUiSurfaceInitialized(surface: Surface): Int
    private external fun nativeOnUiSurfaceFinalized(): Int

    private external fun nativeBindAppEglContext(): Int
    private external fun nativeUnbindAppEglContext(): Int

    private external fun nativePrepareCameraStreamTexture(): Int
    private external fun nativeReleaseCameraStreamTexture(): Int

    private external fun nativeSetCameraStreamAspectWH(aspectWH: Float): Int
    private external fun nativeGetCameraStreamTextureId(): Int
    private external fun nativeSetCameraStreamTransformMatrix(matrix: FloatArray): Int
    private external fun nativeSetCameraStreamRotDeg(frameOrientation: Int): Int
    private external fun nativeOnCameraStreamUpdated(): Int

    private external fun nativeSetEncoderSurface(surface: Surface): Int
    private external fun nativeReleaseEncoderSurface(): Int

    private external fun nativeStartVideoEncode(): Int
    private external fun nativeStopVideoEncode(): Int

    companion object {
        private const val TAG = "OverlayViewFinderController"

        init {
            System.loadLibrary("viewfinderanywhere")
        }
    }
}
