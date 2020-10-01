@file:Suppress("PrivatePropertyName")

package com.fezrestia.android.viewfinderanywhere.device.codec

import android.content.Context
import android.hardware.SensorManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import com.fezrestia.android.lib.util.log.Log
import java.nio.ByteBuffer

class MpegRecorder(
        private val context: Context,
        videoSize: Size,
        private val callback: Callback,
        private val callbackHandler: Handler) {
    private val TAG = "MpegRecorder"

    private var recHandlerThread = HandlerThread("rec", Thread.NORM_PRIORITY)
    private var recHandler: Handler

    // Video.
    private var videoMediaFormat: MediaFormat
    private var videoEncoderName: String
    private var videoInputSurface: Surface? = null
    private var videoMediaCodec: MediaCodec? = null

    // Mux.
    private var mpegMuxer: MediaMuxer? = null

    // File.
    private var mpegFileFullPath: String = ""

    // Orientation.
    private var rotDeg = OrientationEventListener.ORIENTATION_UNKNOWN
    private val rotRate = SensorManager.SENSOR_DELAY_NORMAL
    private val rotDegCallback = RotDegCallback()
    inner class RotDegCallback : OrientationEventListener(context, rotRate) {
        override fun onOrientationChanged(orientation: Int) {
            rotDeg = orientation
        }
    }

    fun <T : Any> ensure(nullable: T?): T = nullable ?: throw Exception("Ensure FAIL")

    interface Callback {
        fun onRecStarted()
        fun onRecStopped(recFileFullPath: String)
    }

    // CONSTRUCTOR.
    init {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR()")

        recHandlerThread.start()
        recHandler = Handler(recHandlerThread.looper)
        videoMediaFormat = MediaCodecPDR.getVideoMediaFormat(videoSize.width, videoSize.height)
        videoEncoderName = MediaCodecPDR.getVideoEncoderName(videoMediaFormat)

        rotDegCallback.enable()
    }

    fun release() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "release()")

        rotDegCallback.disable()

        recHandlerThread.quitSafely()
        try {
            recHandlerThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Setup MPEG recorder for single recording.
     * setup() -> start() -> stop() -> reset()
     *
     * @param mpegFileFullPath
     */
    fun setup(mpegFileFullPath: String) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "setup()")

        videoMediaCodec = MediaCodec.createByCodecName(videoEncoderName).apply {
            setCallback(VideoMediaCodecCallbackImpl(), recHandler)
            configure(
                    videoMediaFormat,
                    null, // output surface.
                    null, // media crypto.
                    MediaCodec.CONFIGURE_FLAG_ENCODE)

            videoInputSurface = createInputSurface()
        }

        mpegMuxer = MediaMuxer(
                mpegFileFullPath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        this.mpegFileFullPath = mpegFileFullPath
    }

    /**
     * Start recording.
     * setup() -> start() -> stop() -> reset()
     */
    fun start() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start()")

        val videoEnc = ensure(videoMediaCodec)
        videoEnc.start()

        val muxer = ensure(mpegMuxer)
        val videoRotHint = ((rotDeg + 45) / 90 * 90) % 360 // Round to 0, 90, 180, 270
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## rotDeg=$rotDeg, videoRotHint=$videoRotHint")
        muxer.setOrientationHint(videoRotHint)
    }

    /**
     * Stop recording.
     * setup() -> start() -> stop() -> reset()
     */
    fun stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop()")

        val videoCodec = ensure(videoMediaCodec)
        videoCodec.signalEndOfInputStream()
    }

    /**
     * Reset recorder setting.
     * setup() -> start() -> stop() -> reset()
     */
    fun reset() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "reset()")

        videoMediaCodec?.release()
        videoMediaCodec = null

        videoInputSurface?.release()
        videoInputSurface = null

        mpegMuxer?.release()
        mpegMuxer = null
    }

    /**
     * Video input surface.
     */
    fun getVideoInputSurface(): Surface {
        return ensure(videoInputSurface)
    }

    private inner class VideoMediaCodecCallbackImpl : MediaCodec.Callback() {
        private val TAG = "VideoMediaCodecCallbackImpl"

        private var videoTrackIndex: Int = 0

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onInputBufferAvailable() : index=$index")
            // NOP.
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputBufferAvailable() : index=$index, info=$info")

            val outBuf: ByteBuffer = ensure(codec.getOutputBuffer(index))
            val muxer: MediaMuxer = ensure(mpegMuxer)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "  outBuf.remaining = ${outBuf.remaining()}")

            muxer.writeSampleData(videoTrackIndex, outBuf, info)

            codec.releaseOutputBuffer(index, false)

            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "FLAG = BUFFER_FLAG_END_OF_STREAM")

                codec.stop()
                codec.release()
                videoMediaCodec = null

                muxer.stop()
                muxer.release()
                mpegMuxer = null

                callbackHandler.post { callback.onRecStopped(mpegFileFullPath) }
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.logError(TAG, "onError() : e=$e")

            // TODO: Handle error.

        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputFormatChanged() : format=$format")

            // After first frame encoded, output format is changed to valid.
            // Add video track here with valid format.

            val muxer = ensure(mpegMuxer)

            videoTrackIndex = muxer.addTrack(format)
            muxer.start()

            callbackHandler.post { callback.onRecStarted() }
        }
    }
}
