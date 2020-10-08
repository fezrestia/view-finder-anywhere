@file:Suppress("PrivatePropertyName", "LocalVariableName")

package com.fezrestia.android.viewfinderanywhere.device.codec

import android.content.Context
import android.hardware.SensorManager
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
import com.fezrestia.android.lib.util.log.Log
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

    fun <T : Any> ensure(nullable: T?): T = nullable ?: throw Exception("Ensure FAIL")

    interface Callback {
        fun onRecStarted()
        fun onRecStopped(recFileFullPath: String)
    }

    /**
     * CONSTRUCTOR.
     */
    init {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR()")

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

        rotDegCallback.enable()
    }

    /**
     * Release all resources.
     */
    fun release() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "release()")

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
        if (Log.IS_DEBUG) Log.logDebug(TAG, "setup()")

        videoMediaCodec = MediaCodec.createByCodecName(videoEncoderName).apply {
            setCallback(VideoMediaCodecCallbackImpl(), videoHandler)
            configure(
                    videoMediaFormat,
                    null, // output surface.
                    null, // media crypto.
                    MediaCodec.CONFIGURE_FLAG_ENCODE)

            videoInputSurface = createInputSurface()
        }

        val audioBufSize = MediaCodecPDR.getAudioRecordMinBufferSize() * 2
        audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(audioBufSize)
                .build()
        audioRecord?.let { it ->
            val bufFullFrameCount = audioBufSize / audioFrameSize
            it.positionNotificationPeriod = bufFullFrameCount
            it.setRecordPositionUpdateListener(AudioRecordCallback())
        }

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
     */
    fun start() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start()")

        val videoEnc = ensure(videoMediaCodec)
        videoEnc.start()

        val audioRec = ensure(audioRecord)
        audioRec.startRecording()

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
        if (Log.IS_DEBUG) Log.logDebug(TAG, "reset()")

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
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onInputBufferAvailable() : index=$index")
            // NOP.
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputBufferAvailable() : E")
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "  index = $index")
                Log.logDebug(TAG, "  info.size = ${info.size}")
                Log.logDebug(TAG, "  info.offset = ${info.offset}")
                Log.logDebug(TAG, "  info.presentationTimeUs = ${info.presentationTimeUs}")
                Log.logDebug(TAG, "  info.flags = ${info.flags}")
            }

            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "FLAG = BUFFER_FLAG_END_OF_STREAM")

                codec.stop()
                codec.release()
                videoMediaCodec = null

                tryToStopMpegMuxer()
            } else {
                val outBuf: ByteBuffer = ensure(codec.getOutputBuffer(index))
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  outBuf.remaining = ${outBuf.remaining()}")

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
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Video Out Buf Revised PresentationTimeUs = ${info.presentationTimeUs}")

                val muxer: MediaMuxer = ensure(mpegMuxer)
                muxer.writeSampleData(videoTrackIndex, outBuf, info)

                codec.releaseOutputBuffer(index, false)
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputBufferAvailable() : X")
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
            synchronized(muxer) {
                videoTrackIndex = muxer.addTrack(format)
                tryToStartMpegMuxer()
            }

            val latch = ensure(mpegMuxerStartLatch)
            latch.countDown()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Video Enc Waiting for Muxer start ...")
            latch.await()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Video Enc Waiting for Muxer start ... GO")
        }
    }

    private inner class AudioRecordCallback : AudioRecord.OnRecordPositionUpdateListener {
        private val TAG = "AudioRecordCallback"

        override fun onMarkerReached(recorder: AudioRecord) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onMarkerReached() : E")
            // NOP.
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onMarkerReached() : X")
        }

        override fun onPeriodicNotification(recorder: AudioRecord) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPeriodicNotification() : E")

            val audioEnc = ensure(audioMediaCodec)
            audioEnc.start()

            recorder.setRecordPositionUpdateListener(null)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "onPeriodicNotification() : X")
        }
    }

    private inner class AudioMediaCodecCallbackImpl : MediaCodec.Callback() {
        private val TAG = "AudioMediaCodecCallbackImpl"

        private var isInputFinished = false
        private var startOutBufPresentationTimeUs = 0L
        private var previousOutBufPresentationTimeUs = 0L
        private var outputStartDelayUs = 0L

        private fun getValidNextPresentationTimeUs(outBufPresentationTimeUs: Long): Long {
            if (outBufPresentationTimeUs < previousOutBufPresentationTimeUs) {
                // Output buffer presentation time must be monotonic. (increase only)
                return previousOutBufPresentationTimeUs
            }
            previousOutBufPresentationTimeUs = outBufPresentationTimeUs
            return outBufPresentationTimeUs
        }

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onInputBufferAvailable() : E")
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## index = $index")

            if (isInputFinished) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Input stream is already finished.")
                return
            }

            val inBuf: ByteBuffer = ensure(codec.getInputBuffer(index))

            val inBytes = inBuf.remaining()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## inBuf.remaining = $inBytes")

            val maxReadSize = inBytes - (inBytes % audioFrameSize)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## maxReadSize = $maxReadSize")
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## audioFrameSize = $audioFrameSize")

            val audioRec = ensure(audioRecord)

            var presentationTimeUs: Long
            var readSize: Int
            do {
                presentationTimeUs = System.nanoTime() / 1000
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## presentationTimeUs = $presentationTimeUs")

                readSize = audioRec.read(inBuf, maxReadSize)
                when (readSize) {
                    AudioRecord.ERROR_INVALID_OPERATION -> {
                        Log.logError(TAG, "AudioRecord.read() : ERROR_INVALID_OPERATION")
                        throw RuntimeException("ERROR_INVALID_OPERATION")
                    }
                    AudioRecord.ERROR_BAD_VALUE -> {
                        Log.logError(TAG, "AudioRecord.read() : ERROR_BAD_VALUE")
                        throw RuntimeException("ERROR_BAD_VALUE")
                    }
                    AudioRecord.ERROR_DEAD_OBJECT -> {
                        Log.logError(TAG, "AudioRecord.read() : ERROR_DEAD_OBJECT")
                        throw RuntimeException("ERROR_DEAD_OBJECT")
                    }
                    AudioRecord.ERROR -> {
                        Log.logError(TAG, "AudioRecord.read() : ERROR")
                        throw RuntimeException("ERROR")
                    }
                    0 -> {
                        Log.logDebug(TAG, "AudioRecord.read() : readSize == 0")

                        if (audioRec.recordingState == AudioRecord.RECORDSTATE_STOPPED) {
                            Log.logDebug(TAG, "## Audio recording is already stopped.")
                            break
                        } else {
                            Log.logDebug(TAG, "## Wait for next buf ...")
                            Thread.sleep(30)
                        }
                    }
                    else -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## readSize = $readSize")
                    }
                }
            } while (readSize <= 0)

            if (outputStartDelayUs == 0L) {
                // First frame.
                val frameCount: Long = readSize.toLong() / audioFrameSize
                outputStartDelayUs = frameCount * 1000 * 1000 / audioFormat.sampleRate

                if (Log.IS_DEBUG) Log.logDebug(TAG, "## outputStartDelayUs = $outputStartDelayUs")
            }

            if (audioRec.recordingState == AudioRecord.RECORDSTATE_STOPPED) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## RECORDSTATE_STOPPED")

                codec.queueInputBuffer(
                        index,
                        0,
                        readSize,
                        presentationTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM)

                isInputFinished = true

            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## RECORDSTATE_RECORDING")

                codec.queueInputBuffer(
                        index,
                        0,
                        readSize,
                        presentationTimeUs,
                        0)
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "onInputBufferAvailable() : X")
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputBufferAvailable() : E")
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "## index=$index")
                Log.logDebug(TAG, "## size=${info.size}")
                Log.logDebug(TAG, "## offset=${info.offset}")
                Log.logDebug(TAG, "## presentationTimeUs=${info.presentationTimeUs}")
                Log.logDebug(TAG, "## flags=${info.flags}")
            }

            val outBuf: ByteBuffer = ensure(codec.getOutputBuffer(index))
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## outBuf.remaining = ${outBuf.remaining()}")

            if (info.presentationTimeUs == 0L) {
                // NOP, This frame is not related to audio frame.
            } else {
                if (startOutBufPresentationTimeUs == 0L) {
                    // This is first frame.
                    startOutBufPresentationTimeUs = info.presentationTimeUs
                    info.presentationTimeUs = outputStartDelayUs
                } else {
                    info.presentationTimeUs = info.presentationTimeUs - startOutBufPresentationTimeUs
                }
            }
            info.presentationTimeUs = getValidNextPresentationTimeUs(info.presentationTimeUs)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Audio Out Buf Revised PresentationTimeUs = ${info.presentationTimeUs}")

            val muxer: MediaMuxer = ensure(mpegMuxer)
            muxer.writeSampleData(audioTrackIndex, outBuf, info)

            codec.releaseOutputBuffer(index, false)

            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## FLAG = BUFFER_FLAG_END_OF_STREAM")

                codec.stop()
                codec.release()
                audioMediaCodec = null

                tryToStopMpegMuxer()
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputBufferAvailable() : X")
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.logError(TAG, "onError() : e=$e")

            // TODO: Handle error.

        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputFormatChanged() : E")
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## format=$format")

            // After first frame encoded, output format is changed to valid.
            // Add audio track here with valid format.

            val muxer = ensure(mpegMuxer)
            synchronized(muxer) {
                audioTrackIndex = muxer.addTrack(format)
                tryToStartMpegMuxer()
            }

            val latch = ensure(mpegMuxerStartLatch)
            latch.countDown()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Audio Enc Waiting for Muxer start ...")
            latch.await()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Audio Enc Waiting for Muxer start ... GO")
        }
    }

    private fun tryToStartMpegMuxer() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "tryToStartMpegMuxer()")

        if (videoTrackIndex != INVALID_TRACK_INDEX && audioTrackIndex != INVALID_TRACK_INDEX) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "tryToStartMpegMuxer() START")

            val muxer = ensure(mpegMuxer)
            muxer.start()

            callbackHandler.post { callback.onRecStarted() }
        }
    }

    private fun tryToStopMpegMuxer() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "tryToStopMpegMuxer()")

        if (videoMediaCodec == null && audioMediaCodec == null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "tryToStopMpegMuxer() STOP")

            // Both of video and audio enc are finished.
            val muxer = ensure(mpegMuxer)

            muxer.stop()
            muxer.release()
            mpegMuxer = null

            callbackHandler.post { callback.onRecStopped(mpegFileFullPath) }
        }
    }
}
