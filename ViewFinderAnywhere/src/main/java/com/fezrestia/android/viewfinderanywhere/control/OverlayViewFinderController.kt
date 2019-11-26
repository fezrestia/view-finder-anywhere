package com.fezrestia.android.viewfinderanywhere.control

import android.content.Context
import android.net.Uri
import android.os.Handler

import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.config.options.CameraApiLevel
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager
import com.fezrestia.android.viewfinderanywhere.device.api1.Camera1Device
import com.fezrestia.android.viewfinderanywhere.device.api2.Camera2Device
import com.fezrestia.android.viewfinderanywhere.device.CameraPlatformInterface
import com.fezrestia.android.viewfinderanywhere.storage.StorageController
import com.fezrestia.android.viewfinderanywhere.view.OverlayViewFinderRootView

/**
 * Main controller class.
 *
 * @constructor
 * @param context Context Master context
 */
class OverlayViewFinderController(val context: Context) {
    private val TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS = 1000L

    // Core instances.
    private lateinit var rootView: OverlayViewFinderRootView
    private lateinit var configManager: ConfigManager
    private lateinit var camera: CameraPlatformInterface

    var currentState: State = StateFinalized()
            get
            private set

    private lateinit var storageController: StorageController

    private val forceStopTask = ForceStopTask()

    init {
        // NOP.
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
        // NOP.
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
        fun onSurfaceReady()
        fun requestScan()
        fun requestCancelScan()
        fun requestStillCapture()
    }

    private interface FromDeviceInterface {
        fun onCameraReady()
        fun onCameraBusy()
        fun onScanDone(isSuccess: Boolean)
        fun onCancelScanDone()
        fun onShutterDone()
        fun onStillCaptureDone()
        fun onPhotoStoreReady(data: ByteArray)
    }

    private interface FromStorageInterface {
        fun onPhotoStoreDone(isSuccess: Boolean, uri: Uri?)
    }

    abstract inner class State :
            StateInternalInterface,
            LifeCycleInterface,
            FromExternalEnvironment,
            FromViewInterface,
            FromDeviceInterface,
            FromStorageInterface {
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

        override fun onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady() : NOP")
        }

        override fun onCameraReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraReady() : NOP")
        }

        override fun onCameraBusy() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraBusy() : NOP")
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

        private var isPreOpenRequested = false

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
            isPreOpenRequested = true
        }

        override fun onPreOpenCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenCanceled()")

            if (isPreOpenRequested) {
                camera.closeAsync(CloseCallbackImpl())
                isPreOpenRequested = false
            }
        }

        override fun onCameraReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraReady()")

            if (isPreOpenRequested) {
                // Bind surface in advance.
                val surface = rootView.getViewFinderSurface()
                camera.bindPreviewSurfaceAsync(surface, BindSurfaceCallbackImpl())
            }
        }

        override fun onToggleShowHideRequired() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onToggleShowHideRequired()")

            if (rootView.isOverlayShown()) {
                rootView.hide()
            } else {
                rootView.show()
            }
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

        override fun onCameraReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraReady()")

            isCameraAlreadyReady = true

            checkCameraAndSurfaceAndStartViewFinder()
        }

        override fun onCameraBusy() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraBusy()")

            // Camera can not be used.
            App.ui.postDelayed(
                    forceStopTask,
                    TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS)
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

                camera.bindPreviewSurfaceAsync(
                        rootView.getViewFinderSurface(),
                        BindSurfaceCallbackImpl())

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
            if (isSuccess) {
                currentState.onCameraReady()
            } else {
                currentState.onCameraBusy()
            }
        }
    }

    private inner class CloseCallbackImpl : CameraPlatformInterface.CloseCallback {
        override fun onClosed(isSuccess: Boolean) {
            // NOP.
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

    companion object {
        private const val TAG = "OverlayViewFinderController"
    }
}
