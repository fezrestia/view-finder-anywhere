package com.fezrestia.android.viewfinderanywhere.control;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;

import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.device.CameraDeviceHandler;
import com.fezrestia.android.viewfinderanywhere.service.OverlayViewFinderService;
import com.fezrestia.android.viewfinderanywhere.util.Log;
import com.fezrestia.android.viewfinderanywhere.view.OverlayViewFinderRootView;

public class OverlayViewFinderController {
    // Log tag.
    private static final String TAG = OverlayViewFinderController.class.getSimpleName();

    // Master context.
    private  Context mContext;

    // UI thread worker.
    private Handler mUiWorker = new Handler();

    // Singleton instance
    private static final OverlayViewFinderController INSTANCE = new OverlayViewFinderController();

    // Overlay view.
    private OverlayViewFinderRootView mRootView = null;

    // Camera handler.
    private CameraDeviceHandler mCameraDeviceHandler = null;
    // Target camera ID.
    private static final int TARGET_CAMERA_ID = 0;

    // Current state.
    private State mCurrentState = new State();

    // View finder aspect.
    private float mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_16_9;

    // Receiver.
    private ScreenOffReceiver mScreenOffReceiver = null;

    // Trigger failed delay.
    private static final int TRIGGER_FAILED_FEEDBACK_DELAY_MILLIS = 1000;

    // Storage selector.
    private StartStorageSelectorTask mStartStorageSelectorTask = null;
    private StopStorageSelectorTask mStopStorageSelectorTask = null;

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
        // NOP.
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

        // State.
        changeStateTo(new StateInitialized());

        // Create overlay view.
        mRootView = (OverlayViewFinderRootView)
                LayoutInflater.from(context).inflate(
                R.layout.overlay_view_finder_root, null);
        mRootView.initialize(mViewFinderAspectWH);

        // Add to window.
        mRootView.addToOverlayWindow();

        // Camera.
        mCameraDeviceHandler = new CameraDeviceHandler(mContext, mUiWorker, TARGET_CAMERA_ID);
        mCameraDeviceHandler.openAsync(mViewFinderAspectWH);

        // Storage selector.
        mStartStorageSelectorTask = new StartStorageSelectorTask(mContext);
        mStopStorageSelectorTask = new StopStorageSelectorTask(mContext);

        // Receiver.
        mScreenOffReceiver = new ScreenOffReceiver();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X");
    }

    private void loadPreferences() {
        // Aspect.
        String aspect = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getString(ViewFinderAnywhereConstants.KEY_VIEW_FINDER_ASPECT, null);
        if (aspect == null) {
            // Unexpected or not initialized yet. Use default.
            mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_16_9;
        } else if (ViewFinderAnywhereConstants.VAL_VIEW_FINDER_ASPECT_16_9.equals(aspect)) {
            mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_16_9;
        } else if (ViewFinderAnywhereConstants.VAL_VIEW_FINDER_ASPECT_4_3.equals(aspect)) {
            mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_4_3;
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
        changeStateTo(new StateInitialized());

        // Camera.
        mCameraDeviceHandler.openAsync(mViewFinderAspectWH);

        // Receiver.
        mScreenOffReceiver.enable(mContext);

        // Storage selector.
        if (ViewFinderAnywhereApplication.isStorageSelectorEnabled()) {
            if (mRootView.isAttachedToWindow()) {
                mUiWorker.postDelayed(mStartStorageSelectorTask, 200);//TODO:Timing
            } else {
                mUiWorker.postDelayed(mStartStorageSelectorTask, 1000);//TODO:Timing
            }
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
        changeStateTo(new State());

        // Camera.
        mCameraDeviceHandler.releaseAsync();

        // Receiver.
        mScreenOffReceiver.disable(mContext);

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

        // State.
        changeStateTo(new State());

        // Release camera.
        if (mCameraDeviceHandler != null) {
            mCameraDeviceHandler.releaseAsync();
            mCameraDeviceHandler.destructor();
            mCameraDeviceHandler = null;
        }

        // Release receiver.
        if (mScreenOffReceiver != null) {
            mScreenOffReceiver.disable(mContext);
            mScreenOffReceiver = null;
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

    private interface FromExternalEnvironment {
        void requestForceStop();
    }

    private interface FromViewInterface {
        void onPreOpenRequested();
        void onPreOpenCanceled();
        void onSurfaceReady();
        void onFinishRequested();
        void requestScan();
        void requestCancelScan();
        void requestStillCapture();
    }

    private interface FromDeviceInterface {
        void onCameraReady();
        void onCameraBusy();
        void onScanDone(boolean isSuccess);
        void onShutterDone();
        void onStillCaptureDone(byte[] jpegBuffer);
    }

    private interface FromStorageInterface {
        void onPhotoStoreDone(boolean isSuccess, Uri uri);
    }

    public class State
            implements
                    StateInternalInterface,
                    FromExternalEnvironment,
                    FromViewInterface,
                    FromDeviceInterface,
                    FromStorageInterface {
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
        public void requestForceStop() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestForceStop()");

            // Force stop overlay.
            mRootView.forceStop();
        }

        @Override
        public void onPreOpenRequested() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenRequested()");

            mCameraDeviceHandler.openAsync(mViewFinderAspectWH);
        }

        @Override
        public void onPreOpenCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPreOpenCanceled()");

            mCameraDeviceHandler.releaseAsync();
        }

        @Override
        public void onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady() : NOP");
        }

        @Override
        public void onFinishRequested() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onFinishRequested()");

            // Finish Overlay.
            OverlayViewFinderController.LifeCycleTrigger.getInstance().requestStop(mContext);
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
        public void onShutterDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutterDone() : NOP");
        }

        @Override
        public void onStillCaptureDone(byte[] jpegBuffer) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStillCaptureDone() : NOP");
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

    private class StateInitialized extends State {
        private final String TAG = StateInitialized.class.getSimpleName();

        private boolean mIsCameraAlreadyReady = false;
        private boolean mIsSurfaceAlreadyReady = false;

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

                mCameraDeviceHandler.setSurfaceTextureAsync(mRootView.getViewFinderSurface());
                changeStateTo(new StateIdle());
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Not camera and surface are ready yet.");
            }
        }
    }

    private class StateIdle extends State {
        @Override
        public void entry() {
            mRootView.getHandler().post(mClear);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady()");

            mCameraDeviceHandler.setSurfaceTextureAsync(mRootView.getViewFinderSurface());
        }

        @Override
        public void requestScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestScan()");

            mCameraDeviceHandler.requestScanAsync();

            changeStateTo(new StateScanning());
        }
    }

    private class StateScanning extends State {
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
        public void onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady()");

            mCameraDeviceHandler.setSurfaceTextureAsync(mRootView.getViewFinderSurface());
        }

        @Override
        public void requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan()");

            mCameraDeviceHandler.requestCancelScanAsync();
            changeStateTo(new StateIdle());
        }

        @Override
        public void onScanDone(boolean isSuccess) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onScanDone()");

            if (mIsStillCaptureAlreadyRequested) {
                changeStateTo(new StateScanDone(isSuccess));
                changeStateTo(new StateStillCapturing());
                mCameraDeviceHandler.requestStillCaptureAsync();
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

    private class StateScanDone extends State {
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
        public void onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady()");

            mCameraDeviceHandler.setSurfaceTextureAsync(mRootView.getViewFinderSurface());
        }

        @Override
        public void requestCancelScan() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScan()");

            mCameraDeviceHandler.requestCancelScanAsync();
            changeStateTo(new StateIdle());
        }

        @Override
        public void requestStillCapture() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCapture()");

            mCameraDeviceHandler.requestStillCaptureAsync();

            changeStateTo(new StateStillCapturing());
        }
    }

    private class StateStillCapturing extends State {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void onSurfaceReady() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceReady()");

            mCameraDeviceHandler.setSurfaceTextureAsync(mRootView.getViewFinderSurface());
        }

        @Override
        public void onShutterDone() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutterDone()");
            // NOP.
        }

        @Override
        public void onStillCaptureDone(byte[] jpegBuffer) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStillCaptureDone()");
            // NOP.
        }

        @Override
        public void onPhotoStoreDone(boolean isSuccess, Uri uri) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPhotoStoreDone()");

            changeStateTo(new StateIdle());
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
}
