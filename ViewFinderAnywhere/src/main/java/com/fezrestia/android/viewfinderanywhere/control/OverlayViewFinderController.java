package com.fezrestia.android.viewfinderanywhere.control;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;

import com.fezrestia.android.util.log.Log;
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

    // Receiver.
    private ScreenOffReceiver mScreenOffReceiver = null;

    // Trigger failed delay.
    private static final int TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS = 1000;

    // Storage.
    private StorageController mStorageController = null;

    // Storage selector.
    private StartStorageSelectorTask mStartStorageSelectorTask = null;
    private StopStorageSelectorTask mStopStorageSelectorTask = null;

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
    @SuppressLint("InflateParams")
    public void start() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E");

        // Initial state.
        mCurrentState = new StateFinalized();

        // Camera.
        switch (mConfigManager.getCamApiLv()) {
            case CAMERA_API_1:
                mCamera = new Camera1Device(mContext);
                break;

            case CAMERA_API_2:
                mCamera = new Camera2Device(mContext, mUiWorker);
                break;

            default:
                throw new IllegalArgumentException("Unknown API level.");
        }

        // Storage selector.
        mStartStorageSelectorTask = new StartStorageSelectorTask(mContext);
        mStopStorageSelectorTask = new StopStorageSelectorTask(mContext);

        // Receiver.
        mScreenOffReceiver = new ScreenOffReceiver();

        // Storage.
        mStorageController = new StorageController(mContext, mUiWorker, this);

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
            int delayedMillis;
            if (mRootView.isAttachedToWindow()) {
                delayedMillis = ViewFinderAnywhereConstants.STORAGE_SELECTOR_TRIGGER_DELAY_MILLIS;
            } else {
                delayedMillis = ViewFinderAnywhereConstants.STORAGE_SELECTOR_TRIGGER_DELAY_MILLIS
                        * 2;
            }
            mUiWorker.postDelayed(mStartStorageSelectorTask, delayedMillis);
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : X");
    }

    private class StartStorageSelectorTask implements Runnable {
        private final Context mContext;

        /**
         * CONSTRUCTOR.
         *
         * @param context Master context.
         */
        StartStorageSelectorTask(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            // Start storage selector.
            OnOffTrigger.openStorageSelector(mContext);
        }
    }

    /**
     * Overlay UI is active or not.
     *
     * @return Overlay view is active or not.
     */
    public boolean isOverlayActive() {
        return (mRootView != null);
    }

    /**
     * Pause overlay view finder.
     */
    public void pause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : E");

        // State.
        mCurrentState.onPause();

        // Storage selector.
        mUiWorker.removeCallbacks(mStartStorageSelectorTask);
        mUiWorker.post(mStopStorageSelectorTask);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : X");
    }

    private class StopStorageSelectorTask implements Runnable {
        private final Context mContext;

        /**
         * CONSTRUCTOR.
         *
         * @param context Master context.
         */
        StopStorageSelectorTask(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            // Start storage selector.
            OnOffTrigger.closeStorageSelector(mContext);
        }
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

        // Release receiver.
        if (mScreenOffReceiver != null) {
            mScreenOffReceiver.disable(mContext);
            mScreenOffReceiver = null;
        }

        // Storage.
        if (mStorageController != null) {
            mStorageController.release();
            mStorageController = null;
        }

        // Storage selector.
        mStartStorageSelectorTask = null;
        mStopStorageSelectorTask = null;

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X");
    }

    @SuppressWarnings("unused") // False positive.
    private interface StateInternalInterface {
        void entry();
        void exit();
        boolean isActive();
    }

    @SuppressWarnings("unused") // False positive.
    private interface LifeCycleInterface {
        void onResume();
        void onPause();
        void onToggleShowHideRequired();
    }

    @SuppressWarnings("unused") // False positive.
    private interface FromExternalEnvironment {
        void requestForceStop();
    }

    @SuppressWarnings("unused") // False positive.
    private interface FromViewInterface {
        void onPreOpenRequested();
        void onPreOpenCanceled();
        void onSurfaceReady();
        void requestScan();
        void requestCancelScan();
        void requestStillCapture();
    }

    @SuppressWarnings("unused") // False positive.
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

    @SuppressWarnings("WeakerAccess") // False positive.
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

        private boolean mIsPreOpenCanceled = true;

        @Override
        public void entry() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "entry()");

            // Camera.
            mCamera.closeAsync(new CloseCallbackImpl());

            // Receiver.
            mScreenOffReceiver.disable(mContext);
        }

        @Override
        public void onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()");

            changeStateTo(new StateInitialized());
        }

        @Override
        public void onPreOpenRequested() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenRequested()");

            mCamera.openAsync(mConfigManager.getEvfAspectWH(), new OpenCallbackImpl());

            mIsPreOpenCanceled = false;
        }

        @Override
        public void onPreOpenCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenCanceled()");

            mIsPreOpenCanceled = true;

            mCamera.closeAsync(new CloseCallbackImpl());
        }

        @Override
        public void onCameraReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraReady()");

            if (!mIsPreOpenCanceled) {
                // Bind surface in advance.
                if (mRootView.getViewFinderSurface() != null) {
                    mCamera.bindPreviewSurfaceAsync(
                            mRootView.getViewFinderSurface(),
                            new BindSurfaceCallbackImpl());
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

            // Camera.
            mCamera.openAsync(mConfigManager.getEvfAspectWH(), new OpenCallbackImpl());

            // Receiver.
            mScreenOffReceiver.enable(mContext);
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

            changeStateTo(new StateScanning());
        }
    }

    private class StateScanning extends StateAllFallback {
        public final String TAG = "StateScanning";

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
        public void onCancelScanDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCancelScanDone()");

            changeStateTo(new StateIdle());
        }

        @Override
        public void requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture()");

            mIsStillCaptureAlreadyRequested = true;
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
        private boolean mIsResumeRequired = false;

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void onResume() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()");

            // Resume state without camera release after still capture is done.

            mIsResumeRequired = true;
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

            if (mIsResumeRequired) {
                changeStateTo(new StateInitialized());
            } else if (mIsPauseRequired) {
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

    private class ScreenOffReceiver extends BroadcastReceiver {
        // Log tag.
        private final String TAG = "ScreenOffReceiver";

        // Screen OFF receiver filter.
        private IntentFilter mScreenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);

        // This receiver is enabled or not.
        private boolean mIsEnabled = false;

        /**
         * Enable screen off receiver.
         *
         * @param context Master context.
         */
        public void enable(Context context) {
            if (!mIsEnabled) {
                mIsEnabled = true;
                context.registerReceiver(this, mScreenOffFilter);
            }
        }

        /**
         * Disable screen off receiver.
         *
         * @param context Master context.
         */
        public void disable(Context context) {
            if (mIsEnabled) {
               context.unregisterReceiver(this);
               mIsEnabled = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceive() : ACTION = " + intent.getAction());

            getCurrentState().requestForceStop();
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
}
