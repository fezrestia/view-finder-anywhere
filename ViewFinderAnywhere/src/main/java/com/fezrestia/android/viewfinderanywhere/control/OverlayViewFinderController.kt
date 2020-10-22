@file:Suppress("PrivatePropertyName", "ConstantConditionIf", "unused", "PropertyName")

package com.fezrestia.android.viewfinderanywhere.control

import android.content.Context
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.View

import com.fezrestia.android.lib.util.currentDisplayRot
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.R
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
     * Start overlay view finder.
     */
    fun ready() {
        if (IS_DEBUG) logD(TAG, "ready() : E")

        handler.post(ReadyTask())

        resume()

        if (IS_DEBUG) logD(TAG, "ready() : X")
    }

    private inner class ReadyTask : Runnable {
        private val TAG = "ReadyTask"
        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            currentState.onConstructed()

            // Config.
            configManager = ConfigManager()

            // View.
            cameraView = View.inflate(context, R.layout.overlay_view_finder_root, null) as OverlayViewFinderRootView
            cameraView.setCoreInstances(this@OverlayViewFinderController, configManager)
            cameraView.initialize()
            storageView = View.inflate(context, R.layout.storage_selector_root, null) as StorageSelectorRootView
            storageView.initialize()

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

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    /**
     * Resume overlay view finder.
     */
    fun resume() {
        if (IS_DEBUG) logD(TAG, "resume() : E")

        handler.post(ResumeTask())

        if (IS_DEBUG) logD(TAG, "resume() : X")
    }

    private inner class ResumeTask : Runnable {
        private val TAG = "ResumeTask"
        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            currentState.onResume()

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    /**
     * Pause overlay view finder.
     */
    fun pause() {
        if (IS_DEBUG) logD(TAG, "pause() : E")

        handler.post(PauseTask())

        if (IS_DEBUG) logD(TAG, "pause() : X")
    }

    private inner class PauseTask : Runnable {
        private val TAG = "PauseTask"
        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            currentState.onPause()

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    /**
     * Stop overlay view finder and release all references.
     */
    fun release() {
        if (IS_DEBUG) logD(TAG, "release() : E")

        pause()

        handler.post(ReleaseTask())


        if (IS_DEBUG) logD(TAG, "release() : X")
    }

    private inner class ReleaseTask : Runnable {
        private val TAG = "ReleaseTask"
        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            // Wait for state converged to not active (StateReady).
            while (currentState.isActive) {
                if (IS_DEBUG) logD(TAG, "Waiting for StateReady ...")
                Thread.sleep(100)
            }
            if (IS_DEBUG) logD(TAG, "Waiting for StateReady ... DONE")

            currentState.onDestructed()

            // Wait for state converged to NONE (StateNone).
            while (currentState != NO_STATE) {
                if (IS_DEBUG) logD(TAG, "Waiting for StateNone ...")
                Thread.sleep(100)
            }
            if (IS_DEBUG) logD(TAG, "Waiting for StateNone ... DONE")

            // All state return to static zero.

            nativeOnDestroyed()

            // Storage.
            storageController.release()

            // Release camera.
            camera.release()

            // View.
            cameraView.release()
            storageView.release()

            // Config.
            configManager.release()

            if (IS_DEBUG) logD(TAG, "run() : X")
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
                if (IS_DEBUG) logD(TAG, "isActive() : NOP")
                return true
            }

        override fun entry() {
            if (IS_DEBUG) logD(TAG, "entry() : NOP")
        }

        override fun exit() {
            if (IS_DEBUG) logD(TAG, "exit() : NOP")
        }

        override fun onConstructed() {
            if (IS_DEBUG) logD(TAG, "onConstructed() : NOP")
        }

        override fun onResume() {
            if (IS_DEBUG) logD(TAG, "onResume() : NOP")
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause() : NOP")
        }

        override fun onDestructed() {
            if (IS_DEBUG) logD(TAG, "onDestructed() : NOP")
        }

        override fun onToggleShowHideRequired() {
            if (IS_DEBUG) logD(TAG, "onToggleShowHideRequired() : NOP")
        }

        override fun onPreOpenRequested() {
            if (IS_DEBUG) logD(TAG, "onPreOpenRequested() : NOP")
        }

        override fun onPreOpenCanceled() {
            if (IS_DEBUG) logD(TAG, "onPreOpenCanceled() : NOP")
        }

        override fun onSurfaceCreated() {
            if (IS_DEBUG) logD(TAG, "onSurfaceCreated() : NOP")
        }

        override fun onSurfaceReady() {
            if (IS_DEBUG) logD(TAG, "onSurfaceReady() : NOP")
        }

        override fun onSurfaceReleased() {
            if (IS_DEBUG) logD(TAG, "onSurfaceReleased() : NOP")
        }

        override fun onCameraOpened(isSuccess: Boolean) {
            if (IS_DEBUG) logD(TAG, "onCameraOpened() : NOP")
        }

        override fun onCameraClosed() {
            if (IS_DEBUG) logD(TAG, "onCameraClosed() : NOP")
        }

        override fun onSurfaceBound() {
            if (IS_DEBUG) logD(TAG, "onSurfaceBound() : NOP")
        }

        override fun requestScan() {
            if (IS_DEBUG) logD(TAG, "requestScan() : NOP")
        }

        override fun requestCancelScan() {
            if (IS_DEBUG) logD(TAG, "requestCancelScan() : NOP")
        }

        override fun requestStillCapture() {
            if (IS_DEBUG) logD(TAG, "requestStillCapture() : NOP")
        }

        override fun requestStartRec() {
            if (IS_DEBUG) logD(TAG, "requestStartRec() : NOP")
        }

        override fun requestStopRec() {
            if (IS_DEBUG) logD(TAG, "requestStopRec() : NOP")
        }

        override fun onScanDone(isSuccess: Boolean) {
            if (IS_DEBUG) logD(TAG, "onScanDone() : NOP")
        }

        override fun onCancelScanDone() {
            if (IS_DEBUG) logD(TAG, "onCancelScanDone() : NOP")
        }

        override fun onShutterDone() {
            if (IS_DEBUG) logD(TAG, "onShutterDone() : NOP")
        }

        override fun onStillCaptureDone() {
            if (IS_DEBUG) logD(TAG, "onStillCaptureDone() : NOP")
        }

        override fun onPhotoStoreReady(data: ByteArray) {
            if (IS_DEBUG) logD(TAG, "onPhotoStoreReady() : NOP")
        }

        override fun onPhotoStoreDone(isSuccess: Boolean, uri: Uri?) {
            if (IS_DEBUG) logD(TAG, "onPhotoStoreDone() : NOP")
        }

        override fun onVideoStreamStarted() {
            if (IS_DEBUG) logD(TAG, "onVideoStreamStarted() : NOP")
        }

        override fun onVideoStreamStopped() {
            if (IS_DEBUG) logD(TAG, "onVideoStreamStopped() : NOP")
        }

        override fun onRecStarted() {
            if (IS_DEBUG) logD(TAG, "onRecStarted() : NOP")
        }

        override fun onRecStopped() {
            if (IS_DEBUG) logD(TAG, "onRecStopped() : NOP")
        }
    }

    @Synchronized
    private fun changeStateTo(next: State) {
        if (IS_DEBUG) logD(TAG,"changeStateTo() : NEXT=${next::class.simpleName}")

        currentState.exit()
        currentState = next
        currentState.entry()
    }

    private inner class StateNone : State() {
        private val TAG = "StateNone"

        override fun onConstructed() {
            if (IS_DEBUG) logD(TAG, "onConstructed()")

            changeStateTo(StateReady(isSurfaceReady = false, isCameraReady = false))
        }
    }

    private inner class StateReady(
            var isSurfaceReady: Boolean,
            var isCameraReady: Boolean) : State() {
        private val TAG = "StateReady"

        override val isActive = false

        override fun entry() {
            if (IS_DEBUG) logD(TAG, "entry() : surface=$isSurfaceReady, camera=$isCameraReady")
            // NOP.
        }

        override fun exit() {
            if (IS_DEBUG) logD(TAG, "exit()")
            // NOP.
        }

        override fun onResume() {
            if (IS_DEBUG) logD(TAG, "onResume()")

            changeStateTo(StateStarting(isSurfaceReady, isCameraReady))
        }

        override fun onPreOpenRequested() {
            if (IS_DEBUG) logD(TAG, "onPreOpenRequested()")

            camera.openAsync(configManager.evfAspect.ratioWH, OpenCallbackImpl())
        }

        override fun onPreOpenCanceled() {
            if (IS_DEBUG) logD(TAG, "onPreOpenCanceled()")

            camera.closeAsync(CloseCallbackImpl())
        }

        override fun onCameraOpened(isSuccess: Boolean) {
            if (IS_DEBUG) logD(TAG, "onCameraOpened()")

            if (isSuccess) {
                if (IS_DEBUG) logD(TAG, "Success to open camera.")

                isCameraReady = true
            } else {
                if (IS_DEBUG) logD(TAG, "Failed to open camera.")
                // NOP.
            }
        }

        override fun onCameraClosed() {
            if (IS_DEBUG) logD(TAG, "onCameraClosed()")

            isCameraReady = false
        }

        override fun onToggleShowHideRequired() {
            if (IS_DEBUG) logD(TAG, "onToggleShowHideRequired()")

            if (currentState.isActive) {
                if (cameraView.isVisible()) {
                    cameraView.invisible()
                } else {
                    cameraView.close()
                }
            }
        }

        override fun onDestructed() {
            if (IS_DEBUG) logD(TAG, "onDestructed()")

            App.ui.post { cameraView.removeFromOverlayWindow() }
        }

        override fun onSurfaceReleased() {
            if (IS_DEBUG) logD(TAG, "onSurfaceReleased()")

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
            if (IS_DEBUG) logD(TAG, "entry() : surface=$isSurfaceReady, camera=$isCameraReady")

            if (!isCameraReady) {
                camera.openAsync(configManager.evfAspect.ratioWH, OpenCallbackImpl())
            }

            if (!isSurfaceReady) {
                App.ui.post { cameraView.addToOverlayWindow() }
            }

            checkCameraAndSurfaceAndStartViewFinder()
        }

        override fun exit() {
            if (IS_DEBUG) logD(TAG, "exit()")
            // NOP.
        }

        override fun onResume() {
            if (IS_DEBUG) logD(TAG, "onResume()")

            isPauseRequested = false
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause()")

            isPauseRequested = true
        }

        override fun onCameraOpened(isSuccess: Boolean) {
            if (IS_DEBUG) logD(TAG, "onCameraOpened()")

            if (isSuccess) {
                if (IS_DEBUG) logD(TAG, "Success to open camera")

                isCameraReady = true

                checkCameraAndSurfaceAndStartViewFinder()
            } else {
                if (IS_DEBUG) logD(TAG, "Failed to open camera")

                // TODO: Handle camera not open error.

            }
        }

        override fun onSurfaceCreated() {
            if (IS_DEBUG) logD(TAG, "onSurfaceCreated()")

            // Start initialization of native GL/EGL related.
            val uiSurface = Surface(cameraView.getViewFinderSurface().surfaceTexture)

            nativeOnUiSurfaceInitialized(uiSurface)
        }

        override fun onSurfaceReady() {
            if (IS_DEBUG) logD(TAG, "onSurfaceReady()")

            isSurfaceReady = true

            checkCameraAndSurfaceAndStartViewFinder()
        }

        private fun checkCameraAndSurfaceAndStartViewFinder() {
            if (IS_DEBUG) logD(TAG, "checkCameraAndSurfaceAndStartViewFinder()")

            if (isCameraReady && isSurfaceReady) {
                if (IS_DEBUG) logD(TAG, "Camera and surface are ready.")

                // Camera stream surface.
                run {
                    prepareCameraStreamTexture(
                            camera.getPreviewStreamSize(),
                            camera.getSensorOrientation())

                    cameraSurfaceTexture?.let {
                        camera.bindPreviewSurfaceTextureAsync(
                                it,
                                cameraView.getViewFinderSurface().width,
                                cameraView.getViewFinderSurface().height,
                                BindSurfaceCallbackImpl())
                    } ?: run {
                        logE(TAG, "cameraSurfaceTexture is null")
                    }
                }

                // Video.
                run {
                    val videoSize = calcVideoSize(
                            camera.getPreviewStreamSize(),
                            configManager.evfAspect.ratioWH)
                    if (IS_DEBUG) logD(TAG, "## Video Size = $videoSize")

                    mpegRecorder = MpegRecorder(
                            context,
                            videoSize,
                            MpegRecorderCallbackImpl(),
                            App.ui)
                }

            } else {
                if (IS_DEBUG) logD(TAG, "Not camera and surface are ready yet.")
            }
        }

        override fun onSurfaceBound() {
            if (IS_DEBUG) logD(TAG, "onSurfaceBound()")

            if (isPauseRequested) {
                changeStateTo(StateStopping())
            } else {
                changeStateTo(StateIdle())

                if (App.isStorageSelectorEnabled) {
                    App.ui.post {
                        storageView.addToOverlayWindow()
                    }
                }
            }
        }
    }

    private inner class StateStopping : State() {
        private val TAG = "StateStopping"

        private var isResumeRequested = false

        override fun entry() {
            if (IS_DEBUG) logD(TAG, "entry()")

            cameraSurfaceTexture?.setOnFrameAvailableListener(null)

            camera.closeAsync(CloseCallbackImpl())

            App.ui.post {
                storageView.removeFromOverlayWindow()
            }
        }

        override fun onResume() {
            if (IS_DEBUG) logD(TAG, "onResume()")

            isResumeRequested = true
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause()")

            isResumeRequested = false
        }

        override fun onCameraClosed() {
            if (IS_DEBUG) logD(TAG, "onCameraClosed()")

            cameraSurfaceTexture?.release()
            cameraSurfaceTexture = null

            mpegRecorder?.release()
            mpegRecorder = null

            if (isResumeRequested) {
                changeStateTo(StateStarting(isSurfaceReady = true, isCameraReady = false))
            } else {
                changeStateTo(StateReady(isSurfaceReady = true, isCameraReady = false))
            }
        }
    }

    private inner class StateIdle : State() {
        private val TAG = "StateIdle"

        override fun entry() {
            if (IS_DEBUG) logD(TAG, "entry()")

            App.ui.post(VisualFeedbackClearTask())
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause()")

            changeStateTo(StateStopping())
        }

        override fun requestScan() {
            if (IS_DEBUG) logD(TAG, "requestScan()")

            camera.requestScanAsync(ScanCallbackImpl())

            changeStateTo(StateDoingScan())
        }

        override fun requestStartRec() {
            if (IS_DEBUG) logD(TAG, "requestStartRec()")

            changeStateTo(StateRec())
        }
    }

    private inner class StateDoingScan : State() {
        private val TAG = "StateDoingScan"

        private var isStillCaptureAlreadyRequested = false
        private var isPauseRequested = false

        override fun entry() {
            if (IS_DEBUG) logD(TAG, "entry()")

            App.ui.post(VisualFeedbackOnScanStartedTask())
        }

        override fun onResume() {
            if (IS_DEBUG) logD(TAG, "onResume()")

            isPauseRequested = false
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause()")

            isPauseRequested = true
        }

        override fun requestCancelScan() {
            if (IS_DEBUG) logD(TAG, "requestCancelScan()")

            if (!isPauseRequested) {
                camera.requestCancelScanAsync(CancelScanCallbackImpl())

                changeStateTo(StateCancellingScan())
            }
        }

        override fun onScanDone(isSuccess: Boolean) {
            if (IS_DEBUG) logD(TAG, "onScanDone()")

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
            if (IS_DEBUG) logD(TAG, "requestStillCapture()")

            isStillCaptureAlreadyRequested = true
        }
    }

    private inner class StateCancellingScan : State() {
        private val TAG = "StateCancellingScan"

        private var isPauseRequested = false

        override fun onResume() {
            if (IS_DEBUG) logD(TAG, "onResume()")

            isPauseRequested = false
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause()")

            isPauseRequested = true
        }

        override fun onCancelScanDone() {
            if (IS_DEBUG) logD(TAG, "onCancelScanDone()")

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
            if (IS_DEBUG) logD(TAG, "entry()")

            App.ui.post(VisualFeedbackOnScanDoneTask(isSuccess))
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause()")

            changeStateTo(StateStopping())
        }

        override fun requestCancelScan() {
            if (IS_DEBUG) logD(TAG, "requestCancelScan()")

            camera.requestCancelScanAsync(CancelScanCallbackImpl())

            changeStateTo(StateCancellingScan())
        }

        override fun requestStillCapture() {
            if (IS_DEBUG) logD(TAG, "requestStillCapture()")

            camera.requestStillCaptureAsync(StillCaptureCallbackImpl())

            changeStateTo(StateStillCapturing())
        }
    }

    private inner class StateStillCapturing : State() {
        private val TAG = "StateStillCapturing"

        private var isPauseRequested = false

        override fun onResume() {
            if (IS_DEBUG) logD(TAG, "onResume()")

            isPauseRequested = false
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause()")

            isPauseRequested = true
        }

        override fun onShutterDone() {
            if (IS_DEBUG) logD(TAG, "onShutterDone()")

            App.ui.post(VisualFeedbackClearTask())
            App.ui.post(VisualFeedbackOnShutterDoneTask())
        }

        override fun onPhotoStoreReady(data: ByteArray) {
            if (IS_DEBUG) logD(TAG, "onPhotoStoreReady()")

            // Request store.
            storageController.storePicture(data)

            if (isPauseRequested) {
                changeStateTo(StateStopping())
            } else {
                changeStateTo(StateIdle())
            }
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
            if (IS_DEBUG) logD(TAG, "entry()")

            mpegRecorder?.let {
                it.setup(mpegFileFullPath)
                nativeSetEncoderSurface(it.getVideoInputSurface())
            }
            camera.requestStartVideoStreamAsync(VideoCallbackImpl())
        }

        override fun exit() {
            if (IS_DEBUG) logD(TAG, "exit()")
            // NOP.
        }

        override fun onResume() {
            if (IS_DEBUG) logD(TAG, "onResume()")

            // If pause/resume action is done in Rec state,
            // recording will be stopped and return to Idle state.
            isPauseRequested = false
        }

        override fun onPause() {
            if (IS_DEBUG) logD(TAG, "onPause()")

            // If pause action is done in Rec state,
            // stop recording immediately and return to Finalized state.
            isPauseRequested = true

            if (IS_DEBUG) logD(TAG, "Auto requestStopRec()")
            requestStopRec()
        }

        override fun onVideoStreamStarted() {
            if (IS_DEBUG) logD(TAG, "onVideoStreamStarted()")

            cameraView.getVisualFeedbackTrigger().onRecStarted()

            nativeStartVideoEncode()

            mpegRecorder?.start()
        }

        override fun onVideoStreamStopped() {
            if (IS_DEBUG) logD(TAG, "onVideoStreamStopped()")

            mpegRecorder?.stop()
        }

        override fun onRecStarted() {
            if (IS_DEBUG) logD(TAG, "onRecStarted()")

            if (isWaitingForStopRec) {
                if (IS_DEBUG) logD(TAG, "onRecStarted() stop immediately")

                App.ui.postDelayed(
                        { doStopRec() },
                        MIN_REC_DURATION_MILLIS)
            } else {
                if (IS_DEBUG) logD(TAG, "onRecStarted() cache start timestamp")

                startRecUptimeMillis = SystemClock.uptimeMillis()
            }
        }

        override fun onRecStopped() {
            if (IS_DEBUG) logD(TAG, "onRecStopped()")

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
            if (IS_DEBUG) logD(TAG, "requestStopRec()")

            if (isWaitingForStopRec) {
                if (IS_DEBUG) logD(TAG, "Already waiting for stop recording")
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
                    if (IS_DEBUG) logD(TAG, "recTimeMillis=$recTimeMillis is too short.")

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
            if (IS_DEBUG) logD(TAG, "doStopRec()")

            nativeStopVideoEncode()
            camera.requestStopVideoStreamAsync()
        }
    }

    /**
     * Close overlay window without user intneraction.
     */
    fun forceClose() {
        cameraView.close()
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
        if (IS_DEBUG) {
            logD(TAG, "## frameSize = $frameSize")
            logD(TAG, "## frameOrientation = $frameOrientation")
        }

        cameraSurfaceTexture?.let {
            if (IS_DEBUG) logD(TAG, "cameraSurfaceTexture exists")
            // NOP.
        } ?: run {
            if (IS_DEBUG) logD(TAG, "cameraSurfaceTexture N/A")

            val cameraAspect = frameSize.width.toFloat() / frameSize.height.toFloat()
            nativeSetCameraStreamAspectWH(cameraAspect)

            nativePrepareCameraStreamTexture()
            val texId = nativeGetCameraStreamTextureId()
            if (IS_DEBUG) logD(TAG, "Camera Stream Tex ID = $texId")

            cameraSurfaceTexture = SurfaceTexture(texId)
            cameraSurfaceTexture?.setOnFrameAvailableListener(CameraSurfaceTextureCallback())

            val displayRot = getDisplayRotDeg()
            if (IS_DEBUG) logD(TAG, "displayRot = $displayRot")

            nativeSetCameraStreamRotDeg(displayRot)
        }
    }

    private fun getDisplayRotDeg(): Int {
        return when (currentDisplayRot(context, cameraView.windowManager)) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    // previewSize is based on landscape/portrait, requestAspectWH is always landscape.
    private fun calcVideoSize(previewSize: Size, requestAspectWH: Float): Size {
        val previewAspectWH = previewSize.width.toFloat() / previewSize.height.toFloat()

        if (1.0 < previewAspectWH) {
            // Landscape.
            if (previewAspectWH < requestAspectWH) {
                // Crop top/bottom.
                val w = previewSize.width
                val h = (w.toFloat() / requestAspectWH).toInt()
                return Size(w, h)
            } else {
                // Crop left/right.
                val h = previewSize.height
                val w = (h.toFloat() * requestAspectWH).toInt()
                return Size(w, h)
            }
        } else {
            // Portrait.
            val revisedRequestAspectWH = 1.0f / requestAspectWH
            if (previewAspectWH < revisedRequestAspectWH) {
                // Crop top/bottom.
                val w = previewSize.width
                val h = (w.toFloat() / revisedRequestAspectWH).toInt()
                return Size(w, h)
            } else {
                // Crop left/right.
                val h = previewSize.height
                val w = (h.toFloat() * revisedRequestAspectWH).toInt()
                return Size(w, h)
            }
        }
    }

    private inner class CameraSurfaceTextureCallback : SurfaceTexture.OnFrameAvailableListener {
        val TAG = "CameraSurfaceTextureCallback"
        override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
            if (IS_DEBUG) logD(TAG, "onFrameAvailable() : E")

            if (surfaceTexture.isReleased) {
                logE(TAG, "SurfaceTexture is already released.")
                return
            }

            nativeBindAppEglContext()

            surfaceTexture.updateTexImage()

            val matrix = FloatArray(16)
            surfaceTexture.getTransformMatrix(matrix)

            nativeSetCameraStreamTransformMatrix(matrix)

            nativeOnCameraStreamUpdated()

            nativeUnbindAppEglContext()

            if (IS_DEBUG) logD(TAG, "onFrameAvailable() : X")
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
