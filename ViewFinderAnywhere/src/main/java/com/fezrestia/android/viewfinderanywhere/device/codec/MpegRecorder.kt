@file:Suppress("PrivatePropertyName", "LocalVariableName")

package com.fezrestia.android.viewfinderanywhere.device.codec

import android.content.Context
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import com.fezrestia.android.lib.util.ensure
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

class MpegRecorder(
        private val context: Context,
        videoSize: Size,
        private val callback: Callback,
        private val callbackHandler: Handler) {
    private val TAG = "MpegRecorder"

    private var videoHandlerThread = HandlerThread("video-rec", Thread.NORM_PRIORITY)
    private var videoHandler: Handler

    private var audioHandlerThread = HandlerThread("audio-rec", Thread.NORM_PRIORITY)
    private var audioHandler: Handler

    // Video.
    private var videoMediaFormat: MediaFormat
    private var videoEncoderName: String
    private var videoInputSurface: Surface? = null
    private var videoMediaCodec: MediaCodec? = null

    // Audio.
    private var audioMediaFormat: MediaFormat
    private var audioEncoderName: String
    private var audioMediaCodec: MediaCodec? = null
    private var audioFormat: AudioFormat
    private var audioRecord: AudioRecord? = null
    private val audioFrameSize: Int
    private val audioBufferSizeInBytes: Int

    // Mux.
    private var mpegMuxer: MediaMuxer? = null
    private var mpegMuxerStartLatch: CountDownLatch? = null
    private val INVALID_TRACK_INDEX = -1
    private var videoTrackIndex = INVALID_TRACK_INDEX
    private var audioTrackIndex = INVALID_TRACK_INDEX

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

    interface Callback {
        fun onRecStarted()
        fun onRecStopped(recFileFullPath: String)
    }

    /**
     * CONSTRUCTOR.
     */
    init {
        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR()")

        videoHandlerThread.start()
        videoHandler = Handler(videoHandlerThread.looper)

        videoMediaFormat = MediaCodecPDR.getVideoMediaFormat(videoSize.width, videoSize.height)
        videoEncoderName = MediaCodecPDR.getVideoEncoderName(videoMediaFormat)

        audioHandlerThread.start()
        audioHandler = Handler(audioHandlerThread.looper)

        audioMediaFormat = MediaCodecPDR.getAudioMediaFormat()
        audioEncoderName = MediaCodecPDR.getAudioEncoderName(audioMediaFormat)

        audioFormat = MediaCodecPDR.getAudioRecordAudioFormat()

        audioFrameSize = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            audioFormat.frameSizeInBytes
        } else {
            32 // 16 bits stereo
        }

        audioBufferSizeInBytes = MediaCodecPDR.getAudioRecordMinBufferSize() * 2

        rotDegCallback.enable()
    }

    /**
     * Release all resources.
     */
    fun release() {
        if (IS_DEBUG) logD(TAG, "release()")

        reset()

        rotDegCallback.disable()

        videoHandlerThread.quitSafely()
        try {
            videoHandlerThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        audioHandlerThread.quitSafely()
        try {
            audioHandlerThread.join()
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
        if (IS_DEBUG) logD(TAG, "setup()")

        videoMediaCodec = MediaCodec.createByCodecName(videoEncoderName).apply {
            setCallback(VideoMediaCodecCallbackImpl(), videoHandler)
            configure(
                    videoMediaFormat,
                    null, // output surface.
                    null, // media crypto.
                    MediaCodec.CONFIGURE_FLAG_ENCODE)

            videoInputSurface = createInputSurface()
        }

        audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(audioBufferSizeInBytes)
                .build()

        audioMediaCodec = MediaCodec.createByCodecName(audioEncoderName).apply {
            setCallback(AudioMediaCodecCallbackImpl(), audioHandler)
            configure(
                    audioMediaFormat,
                    null, // output surface.
                    null, // media crypto.
                    MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        mpegMuxer = MediaMuxer(
                mpegFileFullPath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        mpegMuxerStartLatch = CountDownLatch(2) // video and audio

        this.mpegFileFullPath = mpegFileFullPath
    }

    /**
     * Start recording.
     * setup() -> start() -> stop() -> reset()
     *
     * @location Current GPS/GNSS location information if available.
     */
    fun start(location: Location?) {
        if (IS_DEBUG) logD(TAG, "start()")

        val videoEnc = ensure(videoMediaCodec)
        videoEnc.start()

        val audioRec = ensure(audioRecord)
        audioRec.startRecording()

        // Flush audio record buffer.
        // If invalid audio buffer is available, it will be available as noise in contents.
        if (IS_DEBUG) logD(TAG, "## AudioRecord buffer flushing ...")
        val buf = ByteArray(audioBufferSizeInBytes)
        audioRec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
        if (IS_DEBUG) logD(TAG, "## AudioRecord buffer flushing ... DONE")

        val audioEnc = ensure(audioMediaCodec)
        audioEnc.start()

        val muxer = ensure(mpegMuxer)
        val videoRotHint = ((rotDeg + 45) / 90 * 90) % 360 // Round to 0, 90, 180, 270
        if (IS_DEBUG) logD(TAG, "## rotDeg=$rotDeg, videoRotHint=$videoRotHint")
        muxer.setOrientationHint(videoRotHint)
        location?.let {
            muxer.setLocation(location.latitude.toFloat(), location.longitude.toFloat())
        } ?: run {
            logE(TAG, "location is null for path=$mpegFileFullPath")
        }
    }

    /**
     * Stop recording.
     * setup() -> start() -> stop() -> reset()
     */
    fun stop() {
        if (IS_DEBUG) logD(TAG, "stop()")

        val videoEnc = ensure(videoMediaCodec)
        videoEnc.signalEndOfInputStream()

        val audioRec = ensure(audioRecord)
        audioRec.stop()
    }

    /**
     * Reset recorder setting.
     * setup() -> start() -> stop() -> reset()
     */
    fun reset() {
        if (IS_DEBUG) logD(TAG, "reset()")

        videoMediaCodec?.release()
        videoMediaCodec = null

        videoInputSurface?.release()
        videoInputSurface = null

        audioMediaCodec?.release()
        audioMediaCodec = null

        audioRecord?.release()
        audioRecord = null

        mpegMuxer?.release()
        mpegMuxer = null

        mpegMuxerStartLatch?.let {
            while (it.count != 0L) {
                it.countDown()
            }
        }
        mpegMuxerStartLatch = null

        videoTrackIndex = INVALID_TRACK_INDEX
        audioTrackIndex = INVALID_TRACK_INDEX
    }

    /**
     * Video input surface.
     */
    fun getVideoInputSurface(): Surface = ensure(videoInputSurface)

    private inner class VideoMediaCodecCallbackImpl : MediaCodec.Callback() {
        private val TAG = "VideoMediaCodecCallbackImpl"

        private var startOutBufPresentationTimeUs = 0L

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (IS_DEBUG) logD(TAG, "onInputBufferAvailable() : index=$index")
            // NOP.
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (IS_DEBUG) logD(TAG, "onOutputBufferAvailable() : E")
            if (IS_DEBUG) {
                logD(TAG, "  index = $index")
                logD(TAG, "  info.size = ${info.size}")
                logD(TAG, "  info.offset = ${info.offset}")
                logD(TAG, "  info.presentationTimeUs = ${info.presentationTimeUs}")
                logD(TAG, "  info.flags = ${info.flags}")
            }

            val outBuf: ByteBuffer = ensure(codec.getOutputBuffer(index))
            if (IS_DEBUG) logD(TAG, "  outBuf.remaining = ${outBuf.remaining()}")

            if (info.presentationTimeUs == 0L) {
                // NOP, this frame is not video frame.
            } else {
                if (startOutBufPresentationTimeUs == 0L) {
                    // This is first frame.
                    startOutBufPresentationTimeUs = info.presentationTimeUs
                    info.presentationTimeUs = 0L
                } else {
                    info.presentationTimeUs = info.presentationTimeUs - startOutBufPresentationTimeUs
                }
            }
            if (IS_DEBUG) logD(TAG, "Video Out Buf Revised PresentationTimeUs = ${info.presentationTimeUs}")

            // Buffer flags.
            val isNoFlag = info.flags == 0
            val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
            val isPartialFrame = info.flags and MediaCodec.BUFFER_FLAG_PARTIAL_FRAME != 0
            val isCodecConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            val isEndOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (IS_DEBUG) {
                if (isNoFlag) logD(TAG, "## BUFFER_FLAG_NO_FLAG")
                if (isKeyFrame) logD(TAG, "## BUFFER_FLAG_KEY_FRAME")
                if (isPartialFrame) logD(TAG, "## BUFFER_FLAG_PARTIAL_FRAME")
                if (isCodecConfig) logD(TAG, "## BUFFER_FLAG_CODEC_CONFIG")
                if (isEndOfStream) logD(TAG, "## BUFFER_FLAG_END_OF_STREAM")
            }

            if (!isCodecConfig) {
                val muxer: MediaMuxer = ensure(mpegMuxer)
                muxer.writeSampleData(videoTrackIndex, outBuf, info)
            }

            codec.releaseOutputBuffer(index, false)

            if (isEndOfStream) {
                codec.stop()
                codec.release()
                videoMediaCodec = null

                tryToStopMpegMuxer()
            }

            if (IS_DEBUG) logD(TAG, "onOutputBufferAvailable() : X")
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            logE(TAG, "onError() : e=$e")

            // TODO: Handle error.

        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (IS_DEBUG) logD(TAG, "onOutputFormatChanged() : format=$format")

            // After first frame encoded, output format is changed to valid.
            // Add video track here with valid format.

            val muxer = ensure(mpegMuxer)
            synchronized(muxer) {
                videoTrackIndex = muxer.addTrack(format)
                tryToStartMpegMuxer()
            }

            val latch = ensure(mpegMuxerStartLatch)
            latch.countDown()

            if (IS_DEBUG) logD(TAG, "## Video Enc Waiting for Muxer start ...")
            latch.await()
            if (IS_DEBUG) logD(TAG, "## Video Enc Waiting for Muxer start ... GO")
        }
    }

    private inner class AudioMediaCodecCallbackImpl : MediaCodec.Callback() {
        private val TAG = "AudioMediaCodecCallbackImpl"

        private var isInputFinished = false
        private var startOutBufPresentationTimeUs = 0L
        private var previousOutBufPresentationTimeUs = 0L

        private fun getValidNextPresentationTimeUs(outBufPresentationTimeUs: Long): Long {
            if (outBufPresentationTimeUs < previousOutBufPresentationTimeUs) {
                // Output buffer presentation time must be monotonic. (increase only)
                return previousOutBufPresentationTimeUs
            }
            previousOutBufPresentationTimeUs = outBufPresentationTimeUs
            return outBufPresentationTimeUs
        }

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (IS_DEBUG) logD(TAG, "onInputBufferAvailable() : E")
            if (IS_DEBUG) logD(TAG, "## index = $index")

            if (isInputFinished) {
                if (IS_DEBUG) logD(TAG, "Input stream is already finished.")
                return
            }

            val inBuf: ByteBuffer = ensure(codec.getInputBuffer(index))

            val inBytes = inBuf.remaining()
            if (IS_DEBUG) logD(TAG, "## inBuf.remaining = $inBytes")

            val maxReadSize = inBytes - (inBytes % audioFrameSize)
            if (IS_DEBUG) logD(TAG, "## maxReadSize = $maxReadSize")
            if (IS_DEBUG) logD(TAG, "## audioFrameSize = $audioFrameSize")

            val audioRec = ensure(audioRecord)

            var readSize: Int
            var presentationTimeUs: Long
            do {
                readSize = audioRec.read(inBuf, maxReadSize)

                presentationTimeUs = System.nanoTime() / 1000
                if (IS_DEBUG) logD(TAG, "## presentationTimeUs = $presentationTimeUs")

                when (readSize) {
                    AudioRecord.ERROR_INVALID_OPERATION -> {
                        logE(TAG, "AudioRecord.read() : ERROR_INVALID_OPERATION")
                        throw RuntimeException("ERROR_INVALID_OPERATION")
                    }
                    AudioRecord.ERROR_BAD_VALUE -> {
                        logE(TAG, "AudioRecord.read() : ERROR_BAD_VALUE")
                        throw RuntimeException("ERROR_BAD_VALUE")
                    }
                    AudioRecord.ERROR_DEAD_OBJECT -> {
                        logE(TAG, "AudioRecord.read() : ERROR_DEAD_OBJECT")
                        throw RuntimeException("ERROR_DEAD_OBJECT")
                    }
                    AudioRecord.ERROR -> {
                        logE(TAG, "AudioRecord.read() : ERROR")
                        throw RuntimeException("ERROR")
                    }
                    0 -> {
                        logD(TAG, "AudioRecord.read() : readSize == 0")

                        if (audioRec.recordingState == AudioRecord.RECORDSTATE_STOPPED) {
                            logD(TAG, "## Audio recording is already stopped.")
                            break
                        } else {
                            logD(TAG, "## Wait for next buf ...")
                            Thread.sleep(30)
                        }
                    }
                    else -> {
                        if (IS_DEBUG) logD(TAG, "## readSize = $readSize")
                    }
                }
            } while (readSize <= 0)

            if (audioRec.recordingState == AudioRecord.RECORDSTATE_STOPPED) {
                if (IS_DEBUG) logD(TAG, "## RECORDSTATE_STOPPED")

                codec.queueInputBuffer(
                        index,
                        0,
                        readSize,
                        presentationTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM)

                isInputFinished = true

            } else {
                if (IS_DEBUG) logD(TAG, "## RECORDSTATE_RECORDING")

                codec.queueInputBuffer(
                        index,
                        0,
                        readSize,
                        presentationTimeUs,
                        0)
            }

            if (IS_DEBUG) logD(TAG, "onInputBufferAvailable() : X")
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (IS_DEBUG) logD(TAG, "onOutputBufferAvailable() : E")
            if (IS_DEBUG) {
                logD(TAG, "## index=$index")
                logD(TAG, "## size=${info.size}")
                logD(TAG, "## offset=${info.offset}")
                logD(TAG, "## presentationTimeUs=${info.presentationTimeUs}")
                logD(TAG, "## flags=${info.flags}")
            }

            val outBuf: ByteBuffer = ensure(codec.getOutputBuffer(index))
            if (IS_DEBUG) logD(TAG, "## outBuf.remaining = ${outBuf.remaining()}")

            if (info.presentationTimeUs == 0L) {
                // NOP, This frame is not related to audio frame.
            } else {
                if (startOutBufPresentationTimeUs == 0L) {
                    // This is first frame.
                    startOutBufPresentationTimeUs = info.presentationTimeUs
                    info.presentationTimeUs = 0
                } else {
                    info.presentationTimeUs = info.presentationTimeUs - startOutBufPresentationTimeUs
                }
            }
            info.presentationTimeUs = getValidNextPresentationTimeUs(info.presentationTimeUs)
            if (IS_DEBUG) logD(TAG, "Audio Out Buf Revised PresentationTimeUs = ${info.presentationTimeUs}")

            // Buffer flags.
            val isNoFlag = info.flags == 0
            val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
            val isPartialFrame = info.flags and MediaCodec.BUFFER_FLAG_PARTIAL_FRAME != 0
            val isCodecConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            val isEndOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (IS_DEBUG) {
                if (isNoFlag) logD(TAG, "## BUFFER_FLAG_NO_FLAG")
                if (isKeyFrame) logD(TAG, "## BUFFER_FLAG_KEY_FRAME")
                if (isPartialFrame) logD(TAG, "## BUFFER_FLAG_PARTIAL_FRAME")
                if (isCodecConfig) logD(TAG, "## BUFFER_FLAG_CODEC_CONFIG")
                if (isEndOfStream) logD(TAG, "## BUFFER_FLAG_END_OF_STREAM")
            }

            if (!isCodecConfig) {
                val muxer: MediaMuxer = ensure(mpegMuxer)
                muxer.writeSampleData(audioTrackIndex, outBuf, info)
            }

            codec.releaseOutputBuffer(index, false)

            if (isEndOfStream) {
                codec.stop()
                codec.release()
                audioMediaCodec = null

                tryToStopMpegMuxer()
            }

            if (IS_DEBUG) logD(TAG, "onOutputBufferAvailable() : X")
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            logE(TAG, "onError() : e=$e")

            // TODO: Handle error.

        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (IS_DEBUG) logD(TAG, "onOutputFormatChanged() : E")
            if (IS_DEBUG) logD(TAG, "## format=$format")

            // After first frame encoded, output format is changed to valid.
            // Add audio track here with valid format.

            val muxer = ensure(mpegMuxer)
            synchronized(muxer) {
                audioTrackIndex = muxer.addTrack(format)
                tryToStartMpegMuxer()
            }

            val latch = ensure(mpegMuxerStartLatch)
            latch.countDown()

            if (IS_DEBUG) logD(TAG, "## Audio Enc Waiting for Muxer start ...")
            latch.await()
            if (IS_DEBUG) logD(TAG, "## Audio Enc Waiting for Muxer start ... GO")
        }
    }

    private fun tryToStartMpegMuxer() {
        if (IS_DEBUG) logD(TAG, "tryToStartMpegMuxer()")

        if (videoTrackIndex != INVALID_TRACK_INDEX && audioTrackIndex != INVALID_TRACK_INDEX) {
            if (IS_DEBUG) logD(TAG, "tryToStartMpegMuxer() START")

            val muxer = ensure(mpegMuxer)
            muxer.start()

            callbackHandler.post { callback.onRecStarted() }
        }
    }

    private fun tryToStopMpegMuxer() {
        if (IS_DEBUG) logD(TAG, "tryToStopMpegMuxer()")

        if (videoMediaCodec == null && audioMediaCodec == null) {
            if (IS_DEBUG) logD(TAG, "tryToStopMpegMuxer() STOP")

            // Both of video and audio enc are finished.
            val muxer = ensure(mpegMuxer)

            muxer.stop()
            muxer.release()
            mpegMuxer = null

            callbackHandler.post { callback.onRecStopped(mpegFileFullPath) }
        }
    }
}
