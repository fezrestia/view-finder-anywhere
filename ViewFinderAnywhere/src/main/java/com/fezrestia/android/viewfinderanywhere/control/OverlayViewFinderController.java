package com.fezrestia.android.viewfinderanywhere.control;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants.CameraApiLevel;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.device.Camera1Device;
import com.fezrestia.android.viewfinderanywhere.device.Camera2Device;
import com.fezrestia.android.viewfinderanywhere.device.CameraPlatformInterface;
import com.fezrestia.android.viewfinderanywhere.service.OverlayViewFinderService;
import com.fezrestia.android.viewfinderanywhere.storage.StorageController;
import com.fezrestia.android.viewfinderanywhere.view.OverlayViewFinderRootView;

public class OverlayViewFinderController {
    // Log tag.
    private static final String TAG = OverlayViewFinderController.class.getSimpleName();

    // Master context.
    private  Context mContext = null;

    // UI thread handler.
    private Handler mUiWorker = null;

    // Singleton instance
    private static final OverlayViewFinderController INSTANCE = new OverlayViewFinderController();

    // Overlay view.
    private OverlayViewFinderRootView mRootView = null;

    // Camera platform interface.
    private CameraPlatformInterface mCamera = null;

    // Current state.
    private State mCurrentState = new StateFinalized(); // Default.

    // View finder aspect.
    private float mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_1_1;

    // Receiver.
    private ScreenOffReceiver mScreenOffReceiver = null;

    // Trigger failed delay.
    private static final int TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS = 1000;

    // Storage.
    private StorageController mStorageController = null;

    // Storage selector.
    private StartStorageSelectorTask mStartStorageSelectorTask = null;
    private StopStorageSelectorTask mStopStorageSelectorTask = null;

    // API level.
    private CameraApiLevel mCamApiLv = CameraApiLevel.CAMERA_API_1;

    /**
     * Life cycle trigger interface.
     */
    public static class LifeCycleTrigger {
        private static final String TAG = LifeCycleTrigger.class.getSimpleName();
        private static final LifeCycleTrigger INSTANCE = new LifeCycleTrigger();

        // CONSTRUCTOR.
        private LifeCycleTrigger() {
            // NOP.
        }

        /**
         * Get accessor.
         *
         * @return
         */
        public static LifeCycleTrigger getInstance() {
            return INSTANCE;
        }

        /**
         * Start.
         *
         * @param context
         */
        public void requestStart(Context context) {
            Intent service = new Intent(context, OverlayViewFinderService.class);
            ComponentName component = context.startService(service);

            if (Log.IS_DEBUG) {
                if (component != null) {
                    Log.logDebug(TAG, "requestStart() : Component = " + component.toString());
                } else {
                    Log.logDebug(TAG, "requestStart() : Component = NULL");
                }
            }
        }

        /**
         * Stop.
         *
         * @param context
         */
        public void requestStop(Context context) {
            Intent service = new Intent(context, OverlayViewFinderService.class);
            boolean isSuccess = context.stopService(service);

            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStop() : isSuccess = " + isSuccess);
        }
    }

    /**
     * CONSTRUCTOR.
     */
    private OverlayViewFinderController() {
        mUiWorker = ViewFinderAnywhereApplication.getUiThreadHandler();
    }

    /**
     * Get singleton controller instance.
     *
     * @return
     */
    public static synchronized OverlayViewFinderController getInstance() {
        return INSTANCE;
    }

    /**
     * Start overlay view finder.
     *
     * @param context
     */
    public void start(Context context) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E");

        if (mRootView != null) {
            // NOP. Already started.
            Log.logError(TAG, "Error. Already started.");
            return;
        }

        // Cache master context.
        mContext = context;

        // Load preferences.
        loadPreferences();

        // Initial state.
        mCurrentState = new StateFinalized();

        // Create overlay view.
        mRootView = (OverlayViewFinderRootView)
                LayoutInflater.from(context).inflate(
                        R.layout.overlay_view_finder_root,
                        null);
        mRootView.initialize(mViewFinderAspectWH);

        // Add to window.
        mRootView.addToOverlayWindow();

        // Camera.
        switch (mCamApiLv) {
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
        mStorageController = new StorageController(mContext, mUiWorker);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X");
    }

    private void loadPreferences() {
        // Level.
        String apiLevel = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getString(ViewFinderAnywhereConstants.KEY_CAMERA_FUNCTION_API_LEVEL, null);
        if (apiLevel == null) {
            // Use default.
            mCamApiLv = CameraApiLevel.CAMERA_API_1;
        } else if (CameraApiLevel.CAMERA_API_1.name().equals(apiLevel)) {
            mCamApiLv = CameraApiLevel.CAMERA_API_1;
        } else if (CameraApiLevel.CAMERA_API_2.name().equals(apiLevel)) {
            mCamApiLv = CameraApiLevel.CAMERA_API_2;
        } else {
            // NOP. Unexpected.
            throw new IllegalArgumentException("Unexpected API level.");
        }

        // Aspect.
        String aspect = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getString(ViewFinderAnywhereConstants.KEY_VIEW_FINDER_ASPECT, null);
        if (aspect == null) {
            // Unexpected or not initialized yet. Use default.
            mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_1_1;
        } else if (ViewFinderAnywhereConstants.VAL_VIEW_FINDER_ASPECT_16_9.equals(aspect)) {
            mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_16_9;
        } else if (ViewFinderAnywhereConstants.VAL_VIEW_FINDER_ASPECT_4_3.equals(aspect)) {
            mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_4_3;
        } else if (ViewFinderAnywhereConstants.VAL_VIEW_FINDER_ASPECT_1_1.equals(aspect)) {
            mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_1_1;
        } else {
            // NOP. Unexpected.
            throw new IllegalArgumentException("Unexpected Aspect.");
        }
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
         * @param context
         */
        public StartStorageSelectorTask(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            // Start storage selector.
            StorageSelectorController.LifeCycleTrigger.getInstance().requestStart(mContext);
        }
    }

    /**
     * Overlay UI is active or not.
     *
     * @return
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
         * @param context
         */
        public StopStorageSelectorTask(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            // Start storage selector.
            StorageSelectorController.LifeCycleTrigger.getInstance().requestStop(mContext);
        }
    }

    /**
     * Stop overlay view finder.
     */
    public void stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E");

        if (mRootView == null) {
            // NOP. Already stopped.
            Log.logError(TAG, "Error. Already stopped.");
            return;
        }

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

        // Release references.
        mContext = null;
        if (mRootView != null) {
            mRootView.release();
            mRootView.removeFromOverlayWindow();
            mRootView = null;
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
     * @return
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

            mCamera.openAsync(mViewFinderAspectWH, new OpenCallbackImpl());
        }

        @Override
        public void onPreOpenCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenCanceled()");

            mCamera.closeAsync(new CloseCallbackImpl());
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
            mCamera.openAsync(mViewFinderAspectWH, new OpenCallbackImpl());

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
         * @param isSuccess
         */
        public StateScanDone(boolean isSuccess) {
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
         * @param isSuccess
         * @return
         */
        public Runnable setScanDoneState(boolean isSuccess) {
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

    private static class ScreenOffReceiver extends BroadcastReceiver {
        // Log tag.
        private static final String TAG = ScreenOffReceiver.class.getSimpleName();

        // Screen OFF receiver filter.
        private IntentFilter mScreenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);

        // This receiver is enabled or not.
        private boolean mIsEnabled = false;

        /**
         * Enable screen off receiver.
         *
         * @param context
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
         * @param context
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

            OverlayViewFinderController.getInstance().getCurrentState().requestForceStop();
        }
    }



    //// CAMERA PLATFORM INTERFACE CALLBACK ///////////////////////////////////////////////////////

    private class OpenCallbackImpl implements CameraPlatformInterface.OpenCallback {
        @Override
        public void onOpened(boolean isSuccess) {
            if (isSuccess) {
                OverlayViewFinderController.getInstance().getCurrentState().onCameraReady();
            } else {
                OverlayViewFinderController.getInstance().getCurrentState().onCameraBusy();
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
            OverlayViewFinderController.getInstance().getCurrentState().onScanDone(isSuccess);
        }
    }

    private class CancelScanCallbackImpl implements CameraPlatformInterface.CancelScanCallback {
        @Override
        public void onCancelScanDone() {
            OverlayViewFinderController.getInstance().getCurrentState().onCancelScanDone();
        }
    }

    private class StillCaptureCallbackImpl
            implements CameraPlatformInterface.StillCaptureCallback {
        @Override
        public void onShutterDone(int requestId) {
            OverlayViewFinderController.getInstance().getCurrentState().onShutterDone();

            // Firebase analytics.
            ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                    .createNewLogRequest()
                    .setEvent(ViewFinderAnywhereConstants.FIREBASE_EVENT_ON_SHUTTER_DONE)
                    .done();
        }

        @Override
        public void onCaptureDone(int requestId) {
            OverlayViewFinderController.getInstance().getCurrentState().onStillCaptureDone();
        }

        @Override
        public void onPhotoStoreReady(int requestId, byte[] data) {
            // Request store.
            mStorageController.storePicture(data);

            OverlayViewFinderController.getInstance().getCurrentState().onPhotoStoreReady(data);
        }
    }
}
