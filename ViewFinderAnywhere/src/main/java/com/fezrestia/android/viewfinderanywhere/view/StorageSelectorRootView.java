package com.fezrestia.android.viewfinderanywhere.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.fezrestia.android.lib.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.storage.DirFileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StorageSelectorRootView extends RelativeLayout {
    // Log tag.
    private static final String TAG = "StorageSelectorRootView";

    // UI.
    private RelativeLayout mTotalContainer = null;
    private LinearLayout mItemList = null;
    private View mDefaultStorageItem = null;

    // UI coordinates.
    private int mDisplayShortLineLength = 0;
    private int mWindowEdgePadding = 0;

    // Overlay window orientation.
    private int mOrientation = Configuration.ORIENTATION_UNDEFINED;

    // Window.
    private WindowManager mWindowManager = null;
    private WindowManager.LayoutParams mWindowLayoutParams = null;

    // Limit position.
    private Point mWindowEnabledPosit = new Point();
    private Point mWindowDisabledPosit = new Point();

    // Storage list.
    private List<String> mAvailableStorageList = new ArrayList<>();
    private Set<String> mTargetStorageList = new HashSet<>();

    // CONSTRUCTOR.
    public StorageSelectorRootView(final Context context) {
        this(context, null);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR");
        // NOP.
    }

    // CONSTRUCTOR.
    public StorageSelectorRootView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR");
        // NOP.
    }

    // CONSTRUCTOR.
    public StorageSelectorRootView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR");
        // NOP.
    }

    /**
     * Initialize all of configurations.
     */
    public void initialize() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : E");

        // Cache instance references.
        cacheInstances();

        // Load setting.
        loadPreferences();

        // Window related.
        createWindowParameters();

        // Create dynamic layout.
        createUiLayout();

        // Update UI.
        updateTotalUserInterface();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : X");
    }

    private void cacheInstances() {
        mTotalContainer = (RelativeLayout) findViewById(R.id.total_container);
        mItemList = (LinearLayout) findViewById(R.id.item_list);
    }

    private void loadPreferences() {
        mAvailableStorageList.clear();
        mTargetStorageList.clear();

        SharedPreferences sp = ViewFinderAnywhereApplication.getGlobalSharedPreferences();

        //TODO:Directory existence check.
        Set<String> totalSet = sp.getStringSet(
                ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                null);
        if (totalSet != null) {
            for (String eachDirName : totalSet) {

                String dirPath = DirFileUtil.getApplicationStorageRootPath() + "/"
                        + eachDirName;
                File dir = new File(dirPath);
                if (dir.isDirectory() && dir.exists()) {
                    mAvailableStorageList.add(eachDirName);
                } else {
                    Log.logError(TAG, "loadPreferences() : Directory is not existing.");
                }
            }
            Collections.sort(mAvailableStorageList);
        }
        Set<String> targetSet = sp.getStringSet(
                ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_STORE_TARGET_DIRECTORY,
                null);
        if (targetSet != null) {
            for (String eachDirName : targetSet) {
                if (mAvailableStorageList.contains(eachDirName)) {
                    mTargetStorageList.add(eachDirName);
                }
            }
        }
    }

    private void createWindowParameters() {
        mWindowManager = (WindowManager)
                getContext().getSystemService(Context.WINDOW_SERVICE);

        mWindowLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
    }

    @SuppressLint("InflateParams")
    private void createUiLayout() {
        // Clear.
        mItemList.removeAllViews();

        LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        // Item params.
        final int itemHeight = getResources().getDimensionPixelSize(
                R.dimen.storage_selector_item_height);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                itemHeight);

        // Set default storage.
        mDefaultStorageItem = layoutInflater.inflate(R.layout.storage_selector_list_item, null);
        mDefaultStorageItem.setOnTouchListener(mOnStorageItemTouchListenerImpl);
        mDefaultStorageItem.setTag(DirFileUtil.DEFAULT_STORAGE_DIR_NAME);
        if (mTargetStorageList.contains(DirFileUtil.DEFAULT_STORAGE_DIR_NAME)) {
            mDefaultStorageItem.setSelected(true);
        }
        TextView defaultLabel = (TextView)
                mDefaultStorageItem.findViewById(R.id.storage_selector_list_item_label);
        defaultLabel.setText(R.string.storage_selector_default_storage_label);
        mOnStorageItemTouchListenerImpl.updateStaticDrawable(mDefaultStorageItem);
        mItemList.addView(mDefaultStorageItem, params);
        // Set selected storage.
        for (String eachStorage : mAvailableStorageList) {
            RelativeLayout item = (RelativeLayout) layoutInflater.inflate(
                    R.layout.storage_selector_list_item,
                    null);

            if (DirFileUtil.DEFAULT_STORAGE_DIR_NAME.equals(eachStorage)) {
                // This is default storage. Already handled.
                continue;
            }

            TextView label = (TextView) item.findViewById(R.id.storage_selector_list_item_label);
            label.setText(eachStorage);
            label.setShadowLayer(10.0f, 0.0f, 0.0f, Color.BLACK);

            item.setTag(eachStorage);

            item.setOnTouchListener(mOnStorageItemTouchListenerImpl);

            if (mTargetStorageList.contains(eachStorage)) {
                item.setSelected(true);
            }

            // Update UI.
            mOnStorageItemTouchListenerImpl.updateStaticDrawable(item);

            mItemList.addView(item, params);
        }
        // Check selected state.
        mOnStorageItemTouchListenerImpl.checkSelectedStatus();

        // Load dimension.
        mWindowEdgePadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.storage_selector_top_padding);
    }

    private final OnStorageItemTouchListenerImpl mOnStorageItemTouchListenerImpl
            = new OnStorageItemTouchListenerImpl();
    private class OnStorageItemTouchListenerImpl implements OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setBackground(ViewFinderAnywhereApplication.getCustomResContainer()
                        .drawableStorageItemBgPressed);
                    break;

                case MotionEvent.ACTION_UP:
                    // Update state.
                    v.setSelected(!v.isSelected());

                    // Click sound.
                    v.playSoundEffect(SoundEffectConstants.CLICK);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

                    // Update target storage.
                    updateTargetStorage(v);

                    // fall-through.
                case MotionEvent.ACTION_CANCEL:
                    updateStaticDrawable(v);
                    break;

                default:
                    // NOP.
                    break;
            }

            return true;
        }

        private void updateStaticDrawable(View v) {
            if (v.isSelected()) {
                v.setBackground(ViewFinderAnywhereApplication.getCustomResContainer()
                        .drawableStorageItemBgSelected);
            } else {
                v.setBackground(ViewFinderAnywhereApplication.getCustomResContainer()
                        .drawableStorageItemBgNormal);
            }
        }

        private void updateTargetStorage(View v) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "updateTargetStorage() : E");

            String targetStorage = (String) v.getTag();

            if (v.isSelected()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Selected : " + targetStorage);
                mTargetStorageList.add(targetStorage);
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "De-Selected : " + targetStorage);
                mTargetStorageList.remove(targetStorage);
            }

            // If all item is unselected, force default item selected.
            checkSelectedStatus();

            // Store preferences.
            ViewFinderAnywhereApplication.getGlobalSharedPreferences().edit()
                    .putStringSet(
                    ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_STORE_TARGET_DIRECTORY,
                    mTargetStorageList)
                    .apply();

            if (Log.IS_DEBUG) Log.logDebug(TAG, "updateTargetStorage() : X");
        }

        private void checkSelectedStatus() {
            if (mTargetStorageList.isEmpty()) {
                // Select default.
                mDefaultStorageItem.setSelected(true);
                mOnStorageItemTouchListenerImpl.updateStaticDrawable(mDefaultStorageItem);
                mTargetStorageList.add((String) mDefaultStorageItem.getTag());
            }
        }
    }

    /**
     * Release all resources.
     */
    public void release() {
        mTotalContainer = null;
        mItemList = null;
        mDefaultStorageItem = null;

        mWindowManager = null;
        mWindowLayoutParams = null;
    }

    /**
     * Add this view to WindowManager layer.
     */
    public void addToOverlayWindow() {
        if (isAttachedToWindow()) {
            // Already attached.
            return;
        }

        // Window parameters.
        updateWindowParams(true);

        // Add to WindowManager.
        WindowManager winMng = (WindowManager)
                getContext().getSystemService(Context.WINDOW_SERVICE);
        winMng.addView(this, mWindowLayoutParams);
    }

    @SuppressLint("RtlHardcoded")
    private void updateWindowParams(boolean isInitialSetup) {
        switch (mOrientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
            {
                mWindowLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;

                // Window offset on enabled.
                mWindowLayoutParams.x = mWindowEdgePadding;
                mWindowLayoutParams.y = 0;

                // Position limit.
                mWindowDisabledPosit.set(
                        mWindowLayoutParams.x - mDisplayShortLineLength,
                        mWindowLayoutParams.y);
                mWindowEnabledPosit.set(
                        mWindowLayoutParams.x,
                        mWindowLayoutParams.y);
                break;
            }

            case Configuration.ORIENTATION_PORTRAIT:
            {
                mWindowLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;

                // Window offset on enabled.
                mWindowLayoutParams.x = 0;
                mWindowLayoutParams.y = mWindowEdgePadding;

                // Position limit.
                mWindowDisabledPosit.set(
                        mWindowLayoutParams.x,
                        mWindowLayoutParams.y - mDisplayShortLineLength);
                mWindowEnabledPosit.set(
                        mWindowLayoutParams.x,
                        mWindowLayoutParams.y);
                break;
            }

            default:
                // Unexpected orientation.
                throw new IllegalStateException("Unexpected orientation.");
        }

        // Check active.
        if (!isInitialSetup && !isAttachedToWindow()) {
            mWindowLayoutParams.x = mWindowDisabledPosit.x;
            mWindowLayoutParams.y = mWindowDisabledPosit.y;
        }

        if (isAttachedToWindow()) {
            mWindowManager.updateViewLayout(this, mWindowLayoutParams);
        }
    }

    /**
     * Remove this view from WindowManager layer.
     */
    public void removeFromOverlayWindow() {
        if (!isAttachedToWindow()) {
            // Already detached.
            return;
        }

        // Remove from to WindowManager.
        WindowManager winMng = (WindowManager)
                getContext().getSystemService(Context.WINDOW_SERVICE);
        winMng.removeView(this);
    }

    private void updateTotalUserInterface() {
        // Screen configuration.
        calculateScreenConfiguration();
        // Window layout.
        updateWindowParams(false);
        // Update layout.
        updateLayoutParams();
    }

    private void calculateScreenConfiguration() {
        // Get display size.
        Display display = mWindowManager.getDefaultDisplay();
        Point screenSize = new Point();
        display.getSize(screenSize);
        final int width = screenSize.x;
        final int height = screenSize.y;
        mDisplayShortLineLength = Math.min(width, height);

        // Get display orientation.
        if (height < width) {
            mOrientation = Configuration.ORIENTATION_LANDSCAPE;
        } else {
            mOrientation = Configuration.ORIENTATION_PORTRAIT;
        }
    }

    private void updateLayoutParams() {
        if (mTotalContainer != null) {
            ViewGroup.LayoutParams params = mTotalContainer.getLayoutParams();
            final int itemHeight = getResources().getDimensionPixelSize(
                    R.dimen.storage_selector_item_height);
            int maxItemCount;
            int totalWidth;
            int totalHeight;
            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE: {
                    int overlayVfLeft = ViewFinderAnywhereApplication.TotalWindowConfiguration
                            .OverlayViewFinderWindowConfig.enabledWindowX;
                    totalWidth = (int) ((overlayVfLeft - mWindowEdgePadding) * 0.8f);
                    maxItemCount = (int) ((mDisplayShortLineLength * 0.8f) / itemHeight);
                    if (mItemList.getChildCount() < maxItemCount) {
                        totalHeight = itemHeight * mItemList.getChildCount();
                    } else {
                        totalHeight = itemHeight * maxItemCount;
                    }
                    params.width = totalWidth;
                    params.height = totalHeight;

                    // Cache.
                    int winX = mWindowEdgePadding;
                    int winY = (mDisplayShortLineLength - totalHeight) / 2;
                    int winW = totalWidth;
                    int winH = totalHeight;
                    ViewFinderAnywhereApplication.TotalWindowConfiguration
                            .StorageSelectorWindowConfig.update(winX, winY, winW, winH);
                    if (Log.IS_DEBUG) Log.logDebug(TAG,
                            "updateLayoutParams() : [X=" + winX + "] [Y=" + winY + "] [W="
                             + winW + "] [H=" + winH +"]");
                    break;
                }

                case Configuration.ORIENTATION_PORTRAIT: {
                    totalWidth = (int) (mDisplayShortLineLength * 0.8f);
                    int overlayVfTop = ViewFinderAnywhereApplication.TotalWindowConfiguration
                            .OverlayViewFinderWindowConfig.enabledWindowY;
                    maxItemCount = (int) ((overlayVfTop - mWindowEdgePadding) * 0.8f / itemHeight);
                    if (mItemList.getChildCount() < maxItemCount) {
                        totalHeight = itemHeight * mItemList.getChildCount();
                    } else {
                        totalHeight = itemHeight * maxItemCount;
                    }
                    params.width = totalWidth;
                    params.height = totalHeight;

                    // Cache.
                    int winX = (mDisplayShortLineLength - totalWidth) / 2;
                    int winY = mWindowEdgePadding;
                    int winW = totalWidth;
                    int winH = totalHeight;
                    ViewFinderAnywhereApplication.TotalWindowConfiguration
                            .StorageSelectorWindowConfig.update(winX, winY, winW, winH);
                    if (Log.IS_DEBUG) Log.logDebug(TAG,
                            "updateLayoutParams() : [X=" + winX + "] [Y=" + winY + "] [W="
                             + winW + "] [H=" + winH +"]");
                    break;
                }

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
            mTotalContainer.setLayoutParams(params);
        }




    }

    @Override
    public void onFinishInflate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onFinishInflate()");
        super.onFinishInflate();
        // NOP.
    }

    @Override
    public void onAttachedToWindow() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onAttachedToWindow()");
        super.onAttachedToWindow();
        // NOP.
    }

    @Override
    public void onDetachedFromWindow() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDetachedFromWindow()");
        super.onDetachedFromWindow();
        // NOP.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (Log.IS_DEBUG) Log.logDebug(TAG,
                "onConfigurationChanged() : [Config=" + newConfig.toString());
        super.onConfigurationChanged(newConfig);

        // Update UI.
        updateTotalUserInterface();
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
//        if (Log.IS_DEBUG) Log.logDebug(TAG,
//                "onLayout() : [Changed=" + changed + "] [Rect="
//                 + left + ", " + top + ", " + right + ", " + bottom + "]");
        super.onLayout(changed, left, top, right, bottom);
        // NOP.
    }

    @Override
    public void onSizeChanged(int curW, int curH, int nxtW, int nxtH) {
//        if (Log.IS_DEBUG) Log.logDebug(TAG,
//                "onSizeChanged() : [CUR=" + curW + "x" + curH + "] [NXT=" +  nxtW + "x" + nxtH + "]");
        super.onSizeChanged(curW, curH, nxtW, nxtH);
        // NOP.
    }



}
