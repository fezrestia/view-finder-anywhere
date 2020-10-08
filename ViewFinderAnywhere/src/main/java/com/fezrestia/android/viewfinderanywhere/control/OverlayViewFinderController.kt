@file:Suppress("PrivatePropertyName", "ConstantConditionIf", "unused", "PropertyName")

package com.fezrestia.android.viewfinderanywhere.control

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
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
import com.fezrestia.android.viewfinderanywhere.view.StorageSelectorRootView

/**
 * Main controller class.
 *
 * @constructor
 * @param context Context Master context
 */
class OverlayViewFinderController(private val context: Context) {
    // Core instances.
    private lateinit var cameraView: OverlayViewFinderRootView
    private lateinit var storageView: StorageSelectorRootView
    private lateinit var configManager: ConfigManager

    private lateinit var camera: CameraPlatformInterface
    private var cameraSurfaceTexture: SurfaceTexture? = null

    private val NO_STATE = StateNone()

    var currentState: State = NO_STATE
            private set

    private lateinit var storageController: StorageController

    private var mpegRecorder: MpegRecorder? = null

    /**
     * Set core instance dependency.
     *
     * @param cameraView Camera overlay view
     * @param storageView Storage selector overlay view
     * @param configManager ConfigManager
     */
    fun setCoreInstances(
            cameraView: OverlayViewFinderRootView,
            storageView: StorageSelectorRootView,
            configManager: ConfigManager) {
        this.cameraView = cameraView
        this.storageView = storageView
        this.configManager = configManager
    }

    /**
     * Start overlay view finder.
     */
    fun ready() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "ready() : E")

        handler.post(ReadyTask())

        resume()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "ready() : X")
    }

    private inner class ReadyTask : Runnable {
        private val TAG = "ReadyTask"
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            currentState.onConstructed()

            // View.
            cameraView.initialize()

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

            nativeOnCreated()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    /**
     * Resume overlay view finder.
     */
    fun resume() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : E")

        handler.post(ResumeTask())

        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : X")
    }

    private inner class ResumeTask : Runnable {
        private val TAG = "ResumeTask"
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            currentState.onResume()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    /**
     * Pause overlay view finder.
     */
    fun pause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : E")

        handler.post(PauseTask())

        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : X")
    }

    private inner class PauseTask : Runnable {
        private val TAG = "PauseTask"
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            currentState.onPause()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    /**
     * Stop overlay view finder and release all references.
     */
    fun release() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : E")

        pause()

        handler.post(ReleaseTask())


        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : X")
    }

    private inner class ReleaseTask : Runnable {
        private val TAG = "ReleaseTask"
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            // Wait for state converged to not active (StateReady).
            while (currentState.isActive) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Waiting for StateReady ...")
                Thread.sleep(100)
            }
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Waiting for StateReady ... DONE")

            currentState.onDestructed()

            nativeOnDestroyed()

            // Storage.
            storageController.release()

            // Release camera.
            camera.release()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    private interface StateInternalInterface {
        val isActive: Boolean
        fun entry()
        fun exit()
    }

    private interface LifeCycleInterface {
        fun onConstructed()
        fun onResume()
        fun onPause()
        fun onDestructed()
        fun onToggleShowHideRequired()
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
        fun onSurfaceBound()
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
            FromViewInterface,
            FromDeviceInterface,
            FromStorageInterface,
            FromMpegRecorderInterface {
        private val TAG = "State"

        override val isActive: Boolean
            get() {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "isActive() : NOP")
                return true
            }

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry() : NOP")
        }

        override fun exit() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "exit() : NOP")
        }

        override fun onConstructed() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConstructed() : NOP")
        }

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume() : NOP")
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause() : NOP")
        }

        override fun onDestructed() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestructed() : NOP")
        }

        override fun onToggleShowHideRequired() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onToggleShowHideRequired() : NOP")
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

        override fun onSurfaceBound() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceBound() : NOP")
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

    private inner class StateNone : State() {
        private val TAG = "StateNone"

        override fun onConstructed() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConstructed()")

            changeStateTo(StateReady(false, false))
        }
    }

    private inner class StateReady(
            var isSurfaceReady: Boolean,
            var isCameraReady: Boolean) : State() {
        private val TAG = "StateReady"

        override val isActive = false

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry() : surface=$isSurfaceReady, camera=$isCameraReady")
            // NOP.
        }

        override fun exit() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "exit()")
            // NOP.
        }

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            changeStateTo(StateStarting(isSurfaceReady, isCameraReady))
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

                isCameraReady = true
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Failed to open camera.")
                // NOP.
            }
        }

        override fun onCameraClosed() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraClosed()")

            isCameraReady = false
        }

        override fun onToggleShowHideRequired() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onToggleShowHideRequired()")

            if (cameraView.isOverlayShown()) {
                cameraView.hide()
            } else {
                cameraView.show()
            }
        }

        override fun onDestructed() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestructed()")

            App.ui.post { cameraView.removeFromOverlayWindow() }
        }

        override fun onSurfaceReleased() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReleased()")

            isSurfaceReady = false

            nativeOnUiSurfaceFinalized()

            changeStateTo(NO_STATE)
        }
    }

    private inner class StateStarting(
            var isSurfaceReady: Boolean,
            var isCameraReady: Boolean) : State() {
        private val TAG = "StateStarting"

        private var isPauseRequested = false

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry() : surface=$isSurfaceReady, camera=$isCameraReady")

            if (!isCameraReady) {
                camera.openAsync(configManager.evfAspect.ratioWH, OpenCallbackImpl())
            }

            if (!isSurfaceReady) {
                App.ui.post { cameraView.addToOverlayWindow() }
            }

            checkCameraAndSurfaceAndStartViewFinder()
        }

        override fun exit() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "exit()")
            // NOP.
        }

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            isPauseRequested = false
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            isPauseRequested = true
        }

        override fun onCameraOpened(isSuccess: Boolean) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraOpened()")

            if (isSuccess) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Success to open camera")

                isCameraReady = true

                checkCameraAndSurfaceAndStartViewFinder()
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Failed to open camera")

                // TODO: Handle camera not open error.

            }
        }

        override fun onSurfaceCreated() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceCreated()")

            // Start initialization of native GL/EGL related.
            val uiSurface = Surface(cameraView.getViewFinderSurface().surfaceTexture)

            nativeOnUiSurfaceInitialized(uiSurface)
        }

        override fun onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady()")

            isSurfaceReady = true

            checkCameraAndSurfaceAndStartViewFinder()
        }

        private fun checkCameraAndSurfaceAndStartViewFinder() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "checkCameraAndSurfaceAndStartViewFinder()")

            if (isCameraReady && isSurfaceReady) {
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

            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Not camera and surface are ready yet.")
            }
        }

        override fun onSurfaceBound() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceBound()")

            if (isPauseRequested) {
                changeStateTo(StateStopping())
            } else {
                changeStateTo(StateIdle())

                if (App.isStorageSelectorEnabled) {
                    App.ui.post {
                        storageView.initialize()
                        storageView.addToOverlayWindow()
                    }
                }
            }
        }
    }

    private inner class StateStopping : State() {
        private val TAG = "StateStopping"

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()")

            cameraSurfaceTexture?.setOnFrameAvailableListener(null)

            camera.closeAsync(CloseCallbackImpl())

            App.ui.post {
                storageView.removeFromOverlayWindow()
            }
        }

        override fun onCameraClosed() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraClosed()")

            cameraSurfaceTexture?.release()
            cameraSurfaceTexture = null

            mpegRecorder?.release()
            mpegRecorder = null

            changeStateTo(StateReady(true, false))
        }
    }

    private inner class StateIdle : State() {
        private val TAG = "StateIdle"

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()")

            App.ui.post(VisualFeedbackClearTask())
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            changeStateTo(StateStopping())
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

    private inner class StateDoingScan : State() {
        private val TAG = "StateDoingScan"

        private var isStillCaptureAlreadyRequested = false
        private var isPauseRequested = false

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()")

            App.ui.post(VisualFeedbackOnScanStartedTask())
        }

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            isPauseRequested = false
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            isPauseRequested = true
        }

        override fun requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan()")

            if (!isPauseRequested) {
                camera.requestCancelScanAsync(CancelScanCallbackImpl())

                changeStateTo(StateCancellingScan())
            }
        }

        override fun onScanDone(isSuccess: Boolean) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanDone()")

            if (isPauseRequested) {
                changeStateTo(StateStopping())
            } else {
                if (isStillCaptureAlreadyRequested) {
                    App.ui.post(VisualFeedbackOnScanDoneTask(isSuccess))

                    changeStateTo(StateStillCapturing())

                    camera.requestStillCaptureAsync(StillCaptureCallbackImpl())
                } else {
                    changeStateTo(StateScanDone(isSuccess))
                }
            }
        }

        override fun requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture()")

            isStillCaptureAlreadyRequested = true
        }
    }

    private inner class StateCancellingScan : State() {
        private val TAG = "StateCancellingScan"

        private var isPauseRequested = false

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            isPauseRequested = false
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            isPauseRequested = true
        }

        override fun onCancelScanDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCancelScanDone()")

            if (isPauseRequested) {
                changeStateTo(StateStopping())
            } else {
                changeStateTo(StateIdle())
            }
        }
    }

    private inner class StateScanDone(private val isSuccess: Boolean) : State() {
        private val TAG = "StateScanDone"

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()")

            App.ui.post(VisualFeedbackOnScanDoneTask(isSuccess))
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            changeStateTo(StateStopping())
        }

        override fun requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan()")

            camera.requestCancelScanAsync(CancelScanCallbackImpl())

            changeStateTo(StateCancellingScan())
        }

        override fun requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture()")

            camera.requestStillCaptureAsync(StillCaptureCallbackImpl())

            changeStateTo(StateStillCapturing())
        }
    }

    private inner class StateStillCapturing : State() {
        private val TAG = "StateStillCapturing"

        private var isPauseRequested = false

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            isPauseRequested = false
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            isPauseRequested = true
        }

        override fun onShutterDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutterDone()")

            App.ui.post(VisualFeedbackClearTask())
            App.ui.post(VisualFeedbackOnShutterDoneTask())
        }

        override fun onStillCaptureDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStillCaptureDone()")

            if (isPauseRequested) {
                // NOP, Wait for photo store done, after then start stopping.
            } else {
                // Normal sequence.
                changeStateTo(StateIdle())
            }
        }

        override fun onPhotoStoreDone(isSuccess: Boolean, uri: Uri?) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPhotoStoreDone()")

            changeStateTo(StateStopping())
        }
    }

    private inner class StateRec : State() {
        private val TAG = "StateRec"

        val mpegFileFullPath = storageController.getVideoFileFullPath()
        var isPauseRequested = false

        val MIN_REC_DURATION_MILLIS = 1000L
        var startRecUptimeMillis = 0L

        var isWaitingForStopRec = false

        override fun entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()")

            mpegRecorder?.let {
                it.setup(mpegFileFullPath)
                nativeSetEncoderSurface(it.getVideoInputSurface())
            }
            camera.requestStartVideoStreamAsync(VideoCallbackImpl())
        }

        override fun exit() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "exit()")
            // NOP.
        }

        override fun onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")

            // If pause/resume action is done in Rec state,
            // recording will be stopped and return to Idle state.
            isPauseRequested = false
        }

        override fun onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")

            // If pause action is done in Rec state,
            // stop recording immediately and return to Finalized state.
            isPauseRequested = true

            if (Log.IS_DEBUG) Log.logDebug(TAG, "Auto requestStopRec()")
            requestStopRec()
        }

        override fun onVideoStreamStarted() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onVideoStreamStarted()")

            cameraView.getVisualFeedbackTrigger().onRecStarted()

            nativeStartVideoEncode()

            mpegRecorder?.start()
        }

        override fun onVideoStreamStopped() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onVideoStreamStopped()")

            mpegRecorder?.stop()
        }

        override fun onRecStarted() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onRecStarted()")

            if (isWaitingForStopRec) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onRecStarted() stop immediately")

                App.ui.postDelayed(
                        { doStopRec() },
                        MIN_REC_DURATION_MILLIS)
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onRecStarted() cache start timestamp")

                startRecUptimeMillis = SystemClock.uptimeMillis()
            }
        }

        override fun onRecStopped() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onRecStopped()")

            storageController.notifyToMediaScanner(mpegFileFullPath)

            nativeReleaseEncoderSurface()

            mpegRecorder?.reset()

            cameraView.getVisualFeedbackTrigger().onRecStopped()

            if (isPauseRequested) {
                changeStateTo(StateStopping())
            } else {
                changeStateTo(StateIdle())
            }
        }

        override fun requestStopRec() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStopRec()")

            if (isWaitingForStopRec) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already waiting for stop recording")
                return
            }

            isWaitingForStopRec = true

            if (startRecUptimeMillis == 0L) {
                // Recording is requested to stop immediately, but rec is not started yet.
                // NOP here but stop recording immediately after recording started.
            } else {
                // Recording is already started.

                val recTimeMillis = SystemClock.uptimeMillis() - startRecUptimeMillis
                if (recTimeMillis < MIN_REC_DURATION_MILLIS) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "recTimeMillis=$recTimeMillis is too short.")

                    // If rec time is less than min rec duration,
                    // delay stop recording still min duration.
                    App.ui.postDelayed(
                            { doStopRec() },
                            MIN_REC_DURATION_MILLIS - recTimeMillis)

                } else {
                    doStopRec()
                }
            }
        }

        private fun doStopRec() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "doStopRec()")

            nativeStopVideoEncode()
            camera.requestStopVideoStreamAsync()
        }
    }

    private inner class VisualFeedbackOnScanStartedTask : Runnable {
        override fun run() {
            cameraView.getVisualFeedbackTrigger().onScanStarted()
        }
    }

    private inner class VisualFeedbackOnScanDoneTask(private val isSuccess: Boolean) : Runnable {
        override fun run() {
            cameraView.getVisualFeedbackTrigger().onScanDone(isSuccess)
        }
    }

    private inner class VisualFeedbackOnShutterDoneTask : Runnable {
        override fun run() {
            cameraView.getVisualFeedbackTrigger().onShutterDone()
        }
    }

    private inner class VisualFeedbackClearTask : Runnable {
        override fun run() {
            cameraView.getVisualFeedbackTrigger().clear()
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
            currentState.onSurfaceBound()
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
        return when (cameraView.windowManager.defaultDisplay.rotation) {
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

    private inner class CameraSurfaceTextureCallback : SurfaceTexture.OnFrameAvailableListener {
        val TAG = "CameraSurfaceTextureCallback"
        override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onFrameAvailable() : E")

            if (surfaceTexture.isReleased) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "SurfaceTexture is already released.")
                return
            }

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

        private val handlerThread = HandlerThread("state-machine", Thread.NORM_PRIORITY)
        private val handler: Handler

        init {
            System.loadLibrary("viewfinderanywhere")

            handlerThread.start()
            handler = Handler(handlerThread.looper)
        }
    }
}
