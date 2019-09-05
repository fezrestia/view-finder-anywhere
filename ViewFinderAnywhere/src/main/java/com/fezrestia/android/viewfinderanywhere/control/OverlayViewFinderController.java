package com.fezrestia.android.viewfinderanywhere.control;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.view.TextureView;

import com.fezrestia.android.lib.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager;
import com.fezrestia.android.viewfinderanywhere.device.Camera1Device;
import com.fezrestia.android.viewfinderanywhere.device.Camera2Device;
import com.fezrestia.android.viewfinderanywhere.device.CameraPlatformInterface;
import com.fezrestia.android.viewfinderanywhere.storage.StorageController;
import com.fezrestia.android.viewfinderanywhere.view.OverlayViewFinderRootView;

public class OverlayViewFinderController {
    // Log tag.
    private static final String TAG = "OverlayViewFinderController";

    // Master context.
    private Context mContext = null;

    // UI thread handler.
    private Handler mUiWorker = null;

    // Core instances.
    private OverlayViewFinderRootView mRootView = null;
    private ConfigManager mConfigManager = null;

    // Camera platform interface.
    private CameraPlatformInterface mCamera = null;

    // Current state.
    private State mCurrentState = new StateFinalized(); // Default.

    // Trigger failed delay.
    private static final int TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS = 1000;

    // Storage.
    private StorageController mStorageController = null;

    /**
     * CONSTRUCTOR.
     *
     * @param context Master context
     */
    public OverlayViewFinderController(Context context) {
        mContext = context;
        mUiWorker = ViewFinderAnywhereApplication.getUiThreadHandler();
    }

    /**
     * Set core instance dependency.
     *
     * @param rootView RootView
     * @param configManager ConfigManager
     */
    public void setCoreInstances(
            OverlayViewFinderRootView rootView,
            ConfigManager configManager) {
        mRootView = rootView;
        mConfigManager = configManager;
    }

    /**
     * Release all references.
     */
    public void release() {
        mContext = null;
        mUiWorker = null;
        mRootView = null;
        mConfigManager = null;
    }

    /**
     * Start overlay view finder.
     */
    public void start() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E");

        // Initial state.
        mCurrentState = new StateFinalized();

        // Camera.
        switch (mConfigManager.getCamApiLv()) {
            case API_1:
                mCamera = new Camera1Device(mContext);
                break;

            case API_2:
                mCamera = new Camera2Device(mContext, mUiWorker);
                break;

            default:
                throw new IllegalArgumentException("Unknown API level.");
        }
        mCamera.prepare();

        // Storage.
        mStorageController = new StorageController(
                mContext,
                mUiWorker,
                new StorageControllerCallback());

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X");
    }

    /**
     * Resume overlay view finder.
     */
    public void resume() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : E");

        // State.
        mCurrentState.onResume();

        // Storage selector.
        if (ViewFinderAnywhereApplication.isStorageSelectorEnabled()) {
            // Start storage selector.
            OnOffTrigger.openStorageSelector(mContext);
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : X");
    }

    /**
     * Pause overlay view finder.
     */
    public void pause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : E");

        // State.
        mCurrentState.onPause();

        // Close storage selector.
        OnOffTrigger.closeStorageSelector(mContext);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : X");
    }

    /**
     * Stop overlay view finder.
     */
    public void stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E");

        // Reset state.
        mCurrentState = new StateFinalized();

        // Release camera.
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }

        // Storage.
        if (mStorageController != null) {
            mStorageController.release();
            mStorageController = null;
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X");
    }

    private interface StateInternalInterface {
        void entry();
        void exit();
        boolean isActive();
    }

    private interface LifeCycleInterface {
        void onResume();
        void onPause();
        void onToggleShowHideRequired();
    }

    private interface FromExternalEnvironment {
        void requestForceStop();
    }

    private interface FromViewInterface {
        void onPreOpenRequested();
        void onPreOpenCanceled();
        void onSurfaceReady();
        void requestScan();
        void requestCancelScan();
        void requestStillCapture();
    }

    private interface FromDeviceInterface {
        void onCameraReady();
        void onCameraBusy();
        void onScanDone(boolean isSuccess);
        void onCancelScanDone();
        void onShutterDone();
        void onStillCaptureDone();
        void onPhotoStoreReady(byte[] data);
    }

    private interface FromStorageInterface {
        void onPhotoStoreDone(boolean isSuccess, Uri uri);
    }

    public abstract class State
            implements
                    StateInternalInterface,
                    LifeCycleInterface,
                    FromExternalEnvironment,
                    FromViewInterface,
                    FromDeviceInterface,
                    FromStorageInterface {
        public final String TAG = "State";

        @Override
        public void entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry() : NOP");
        }

        @Override
        public void exit() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "exit() : NOP");
        }

        @Override
        public boolean isActive() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "isActive() : NOP");
            return false;
        }

        @Override
        public void onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume() : NOP");
        }

        @Override
        public void onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause() : NOP");
        }

        @Override
        public void onToggleShowHideRequired() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onToggleShowHideRequired() : NOP");
        }

        @Override
        public void requestForceStop() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestForceStop() : NOP");
        }

        @Override
        public void onPreOpenRequested() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenRequested() : NOP");
        }

        @Override
        public void onPreOpenCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenCanceled() : NOP");
        }

        @Override
        public void onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady() : NOP");
        }

        @Override
        public void onCameraReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraReady() : NOP");
        }

        @Override
        public void onCameraBusy() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraBusy() : NOP");
        }

        @Override
        public void requestScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestScan() : NOP");
        }

        @Override
        public void requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan() : NOP");
        }

        @Override
        public void requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture() : NOP");
        }

        @Override
        public void onScanDone(boolean isSuccess) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanDone() : NOP");
        }

        @Override
        public void onCancelScanDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCancelScanDone() : NOP");
        }

        @Override
        public void onShutterDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutterDone() : NOP");
        }

        @Override
        public void onStillCaptureDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStillCaptureDone() : NOP");
        }

        @Override
        public void onPhotoStoreReady(byte[] data) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPhotoStoreReady() : NOP");
        }

        @Override
        public void onPhotoStoreDone(boolean isSuccess, Uri uri) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPhotoStoreDone() : NOP");
        }
    }

    private synchronized void changeStateTo(State nextState) {
        if (Log.IS_DEBUG) Log.logDebug(TAG,
                "changeStateTo() : [NEXT=" + nextState.getClass().getSimpleName() + "]");

        mCurrentState.exit();
        mCurrentState = nextState;
        mCurrentState.entry();
    }

    /**
     * Send event to state from all of the related component including ownself.
     *
     * @return Current state.
     */
    public synchronized State getCurrentState() {
        return mCurrentState;
    }

    private class StateAllFallback extends State {
        public final String TAG = "StateAllFallback";

        @Override
        public void requestForceStop() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestForceStop()");

            // Force stop overlay.
            mRootView.forceStop();

            changeStateTo(new StateFinalized());
        }
    }

    private class StateFinalized extends StateAllFallback {
        public final String TAG = "StateFinalized";

        private boolean mIsPreOpenRequested = false;

        @Override
        public void entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()");

            // Camera.
            mCamera.closeAsync(new CloseCallbackImpl());
        }

        @Override
        public void onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()");

            changeStateTo(new StateInitialized());
        }

        @Override
        public void onPreOpenRequested() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenRequested()");

            mCamera.openAsync(mConfigManager.getEvfAspect().getRatioWH(), new OpenCallbackImpl());
            mIsPreOpenRequested = true;
        }

        @Override
        public void onPreOpenCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenCanceled()");

            if (mIsPreOpenRequested) {
                mCamera.closeAsync(new CloseCallbackImpl());
                mIsPreOpenRequested = false;
            }
        }

        @Override
        public void onCameraReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraReady()");

            if (mIsPreOpenRequested) {
                // Bind surface in advance.
                TextureView surface = mRootView.getViewFinderSurface();
                if (surface != null) {
                    mCamera.bindPreviewSurfaceAsync(surface, new BindSurfaceCallbackImpl());
                }
            }
        }

        @Override
        public void onToggleShowHideRequired() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onToggleShowHideRequired() : NOP");

            if (mRootView.isOverlayShown()) {
                mRootView.hide();
            } else {
                mRootView.show();
            }
        }
    }

    private class StateInitialized extends StateAllFallback {
        public final String TAG = "StateInitialized";

        private boolean mIsCameraAlreadyReady = false;
        private boolean mIsSurfaceAlreadyReady = false;

        @Override
        public void entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()");

            mCamera.openAsync(mConfigManager.getEvfAspect().getRatioWH(), new OpenCallbackImpl());
        }

        @Override
        public void onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()");

            changeStateTo(new StateFinalized());
        }

        @Override
        public void onCameraReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraReady()");

            mIsCameraAlreadyReady = true;

            checkCameraAndSurfaceAndStartViewFinder();
        }

        @Override
        public void onCameraBusy() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraBusy()");

            // Camera can not be used.
            mRootView.getHandler().postDelayed(
                    mForceStopTask,
                    TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS);
        }

        @Override
        public void onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady()");

            mIsSurfaceAlreadyReady = true;

            checkCameraAndSurfaceAndStartViewFinder();
        }

        private void checkCameraAndSurfaceAndStartViewFinder() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "checkCameraAndSurfaceAndStartViewFinder()");

            if (mIsCameraAlreadyReady && mIsSurfaceAlreadyReady) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera and surface are ready.");

                mCamera.bindPreviewSurfaceAsync(
                        mRootView.getViewFinderSurface(),
                        new BindSurfaceCallbackImpl());

                changeStateTo(new StateIdle());
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Not camera and surface are ready yet.");
            }
        }
    }

    private class StateIdle extends StateAllFallback {
        public final String TAG = "StateIdle";

        @Override
        public void entry() {
            mRootView.getHandler().post(mClear);
        }

        @Override
        public void onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()");

            changeStateTo(new StateFinalized());
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void requestScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestScan()");

            mCamera.requestScanAsync(new ScanCallbackImpl());

            changeStateTo(new StateDoingScan());
        }
    }

    private class StateDoingScan extends StateAllFallback {
        public final String TAG = "StateDoingScan";

        private boolean mIsStillCaptureAlreadyRequested = false;

        @Override
        public void entry() {
            mRootView.getHandler().post(mOnScanStarted);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()");

            mCamera.requestCancelScanAsync(new CancelScanCallbackImpl());

            changeStateTo(new StateFinalized());
        }

        @Override
        public void requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan()");

            mCamera.requestCancelScanAsync(new CancelScanCallbackImpl());
            changeStateTo(new StateCancellingScan());
        }

        public void onScanDone(boolean isSuccess) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanDone()");

            if (mIsStillCaptureAlreadyRequested) {
                changeStateTo(new StateScanDone(isSuccess));
                changeStateTo(new StateStillCapturing());
                mCamera.requestStillCaptureAsync(new StillCaptureCallbackImpl());
            } else {
                changeStateTo(new StateScanDone(isSuccess));
            }
        }

        @Override
        public void requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture()");

            mIsStillCaptureAlreadyRequested = true;
        }
    }

    private class StateCancellingScan extends StateAllFallback {
        public final String TAG = "StateCancellingScan";

        @Override
        public void entry() {
            // NOP.
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()");

            changeStateTo(new StateFinalized());
        }

        @Override
        public void onCancelScanDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCancelScanDone()");

            changeStateTo(new StateIdle());
        }
    }

    private class StateScanDone extends StateAllFallback {
        public final String TAG = "StateScanDone";

        // Scan is done successfully or not.
        private final boolean mIsSuccess;

        /**
         * CONSTRUCTOR.
         *
         * @param isSuccess Scan is success or not.
         */
        StateScanDone(boolean isSuccess) {
            mIsSuccess = isSuccess;
        }

        @Override
        public void entry() {
            mRootView.getHandler().post(mOnScanDone.setScanDoneState(mIsSuccess));
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()");

            mCamera.requestCancelScanAsync(new CancelScanCallbackImpl());

            changeStateTo(new StateFinalized());
        }

        @Override
        public void requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan()");

            mCamera.requestCancelScanAsync(new CancelScanCallbackImpl());
        }

        @Override
        public void requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture()");

            mCamera.requestStillCaptureAsync(new StillCaptureCallbackImpl());

            changeStateTo(new StateStillCapturing());
        }

        @Override
        public void onCancelScanDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCancelScanDone()");

            changeStateTo(new StateIdle());
        }
    }

    private class StateStillCapturing extends StateAllFallback {
        public final String TAG = "StateStillCapturing";

        private boolean mIsPauseRequired = false;

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()");

            // Pause request is canceled.

            mIsPauseRequired = false;
        }

        @Override
        public void onPause() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()");

            // Do not pause here. Close device after still capture is assured.

            mIsPauseRequired = true;
        }

        @Override
        public void onShutterDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutterDone()");

            // Clear UI.
            mRootView.getHandler().post(mClear);
            mRootView.getHandler().post(mOnShutterDone);
        }

        @Override
        public void onStillCaptureDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStillCaptureDone()");

            if (mIsPauseRequired) {
                changeStateTo(new StateFinalized());
            } else {
                // Normal sequence.
                changeStateTo(new StateIdle());
            }
        }
    }

    private final ForceStopTask mForceStopTask = new ForceStopTask();
    private class ForceStopTask implements Runnable {
        @Override
        public void run() {
            mRootView.forceStop();
        }
    }

    private final VisualFeedbackOnScanStartedTask mOnScanStarted
            = new VisualFeedbackOnScanStartedTask();
    private class VisualFeedbackOnScanStartedTask implements Runnable {
        @Override
        public void run() {
            mRootView.getVisualFeedbackTrigger().onScanStarted();
        }
    }

    private final VisualFeedbackOnScanDoneTask mOnScanDone
            = new VisualFeedbackOnScanDoneTask();
    private class VisualFeedbackOnScanDoneTask implements Runnable {
        private boolean mIsSuccess = false;

        /**
         * Set scan done state.
         *
         * @param isSuccess Scan is success or not.
         * @return Self.
         */
        Runnable setScanDoneState(boolean isSuccess) {
            mIsSuccess = isSuccess;
            return this;
        }

        @Override
        public void run() {
            mRootView.getVisualFeedbackTrigger().onScanDone(mIsSuccess);
        }
    }

    private final VisualFeedbackOnShutterDoneTask mOnShutterDone
            = new VisualFeedbackOnShutterDoneTask();
    private class VisualFeedbackOnShutterDoneTask implements Runnable {
        @Override
        public void run() {
            mRootView.getVisualFeedbackTrigger().onShutterDone();
        }
    }

    private final VisualFeedbackClearTask mClear
            = new VisualFeedbackClearTask();
    private class VisualFeedbackClearTask implements Runnable {
        @Override
        public void run() {
            mRootView.getVisualFeedbackTrigger().clear();
        }
    }

    //// CAMERA PLATFORM INTERFACE CALLBACK ///////////////////////////////////////////////////////

    private class OpenCallbackImpl implements CameraPlatformInterface.OpenCallback {
        @Override
        public void onOpened(boolean isSuccess) {
            if (isSuccess) {
                getCurrentState().onCameraReady();
            } else {
                getCurrentState().onCameraBusy();
            }
        }
    }

    private class CloseCallbackImpl implements CameraPlatformInterface.CloseCallback {
        @Override
        public void onClosed(boolean isSuccess) {
            // NOP.
        }
    }

    private class BindSurfaceCallbackImpl implements CameraPlatformInterface.BindSurfaceCallback {
        @Override
        public void onSurfaceBound(boolean isSuccess) {
            // NOP.
        }
    }

    private class ScanCallbackImpl implements CameraPlatformInterface.ScanCallback {
        @Override
        public void onScanDone(boolean isSuccess) {
            getCurrentState().onScanDone(isSuccess);
        }
    }

    private class CancelScanCallbackImpl implements CameraPlatformInterface.CancelScanCallback {
        @Override
        public void onCancelScanDone() {
            getCurrentState().onCancelScanDone();
        }
    }

    private class StillCaptureCallbackImpl
            implements CameraPlatformInterface.StillCaptureCallback {
        @Override
        public void onShutterDone(int requestId) {
            getCurrentState().onShutterDone();

            // Firebase analytics.
            ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                    .createNewLogRequest()
                    .setEvent(ViewFinderAnywhereConstants.FIREBASE_EVENT_ON_SHUTTER_DONE)
                    .done();
        }

        @Override
        public void onCaptureDone(int requestId) {
            getCurrentState().onStillCaptureDone();
        }

        @Override
        public void onPhotoStoreReady(int requestId, byte[] data) {
            // Request store.
            mStorageController.storePicture(data);

            getCurrentState().onPhotoStoreReady(data);
        }
    }

    private class StorageControllerCallback implements StorageController.Callback {
        @Override
        public void onPhotoStoreDone(boolean isSuccess, Uri uri) {
            getCurrentState().onPhotoStoreDone(isSuccess, uri);
        }
    }
}
