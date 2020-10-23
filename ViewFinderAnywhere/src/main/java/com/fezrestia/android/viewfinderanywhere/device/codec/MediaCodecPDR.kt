@file:Suppress("LocalVariableName", "unused")

package com.fezrestia.android.viewfinderanywhere.device.codec

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD

internal object MediaCodecPDR {
    private const val TAG = "MediaCodecPDR"

    fun getVideoMediaFormat(videoWidth: Int, videoHeight: Int): MediaFormat {
        if (IS_DEBUG) logD(TAG, "getVideoMediaFormat() : w=$videoWidth, h=$videoHeight")

        val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                videoWidth,
                videoHeight).apply {
            setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    10000000) // 10[MB/s]
            setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(
                    MediaFormat.KEY_FRAME_RATE,
                    30) // frames/sec
            setInteger(
                    MediaFormat.KEY_I_FRAME_INTERVAL,
                    5) // an I-frame in 5 sec.
        }

        if (IS_DEBUG) logD(TAG, "Video MediaFormat = $format")

        return format
    }

    fun getVideoEncoderName(videoMediaFormat: MediaFormat): String {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

        val encoderName = codecList.findEncoderForFormat(videoMediaFormat)
        if (IS_DEBUG) logD(TAG, "Video Encoder Name = $encoderName")

        if (IS_DEBUG) {
            val videoWidth = videoMediaFormat.getInteger(MediaFormat.KEY_WIDTH)
            val videoHeight = videoMediaFormat.getInteger(MediaFormat.KEY_HEIGHT)

            codecList.codecInfos.forEach { codecInfo ->
                if (codecInfo.name == encoderName) {
                    logD(TAG, "#### Valid Encoder")
                    logD(TAG, "## MediaCodec = ${codecInfo.name}")
                    codecInfo.supportedTypes.forEach { type ->
                        logD(TAG, "    type = $type")
                    }
                    val capabilities = codecInfo.getCapabilitiesForType(videoMediaFormat.getString(MediaFormat.KEY_MIME))

                    logD(TAG, "  Color Formats :")
                    capabilities.colorFormats.forEach { colorFormat ->
                        logD(TAG, "    format = $colorFormat")
                    }

                    val encoderCaps = capabilities.encoderCapabilities
                    logD(TAG, "  Encoder Capabilities :")
                    logD(TAG, "    complexityRange = ${encoderCaps.complexityRange}")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        logD(TAG, "    qualityRange = ${encoderCaps.qualityRange}")
                    }
                    logD(TAG, "    CBR = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)}")
                    logD(TAG, "    CQ  = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)}")
                    logD(TAG, "    VBR = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)}")

                    val videoCaps = capabilities.videoCapabilities
                    logD(TAG, "  Video Capabilities :")
                    logD(TAG, "    achievableFpsRange = ${videoCaps.getAchievableFrameRatesFor(videoWidth, videoHeight)}")
                    logD(TAG, "    supportedFpsRange = ${videoCaps.getSupportedFrameRatesFor(videoWidth, videoHeight)}")
                    logD(TAG, "    bpsRange = ${videoCaps.bitrateRange}")
                    logD(TAG, "    widthAlign = ${videoCaps.widthAlignment}")
                    logD(TAG, "    heightAlign = ${videoCaps.heightAlignment}")
                    logD(TAG, "    supportedWidth = ${videoCaps.supportedWidths}")
                    logD(TAG, "    supportedHeight = ${videoCaps.supportedHeights}")

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        videoCaps.supportedPerformancePoints?.forEach { perfPoint ->
                            logD(TAG, "## performance point = $perfPoint")
                        }
                    }
                }
            }
        }

        return encoderName
    }

    private const val SAMPLING_RATE = 44100
    private const val CHANNEL_COUNT = 1
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    fun getAudioRecordAudioFormat(): AudioFormat {
        return AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLING_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
    }

    fun getAudioRecordMinBufferSize(): Int {
        val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AUDIO_FORMAT)

        if (IS_DEBUG) logD(TAG, "Audio Min Buf Size = $minBufSize")
        return minBufSize
    }

    fun getAudioMediaFormat(): MediaFormat {
        if (IS_DEBUG) logD(TAG, "getAudioMediaFormat()")

        val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                SAMPLING_RATE,
                CHANNEL_COUNT).apply {
            setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    128000) // 128[kbps]
            setInteger(
                    MediaFormat.KEY_PCM_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT)
            setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

        if (IS_DEBUG) logD(TAG, "Audio MediaFormat = $format")

        return format
    }

    fun getAudioEncoderName(audioMediaFormat: MediaFormat): String {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

        val encoderName = codecList.findEncoderForFormat(audioMediaFormat)
        if (IS_DEBUG) logD(TAG, "Audio Encoder Name = $encoderName")

        if (IS_DEBUG) {
            codecList.codecInfos.forEach { codecInfo ->
                if (codecInfo.name == encoderName) {
                    logD(TAG, "#### Valid Encoder")
                    logD(TAG, "## MediaCodec = ${codecInfo.name}")
                    codecInfo.supportedTypes.forEach { type ->
                        logD(TAG, "    type = $type")
                    }
                    val capabilities = codecInfo.getCapabilitiesForType(audioMediaFormat.getString(MediaFormat.KEY_MIME))

                    val encoderCaps = capabilities.encoderCapabilities
                    logD(TAG, "  Encoder Capabilities :")
                    logD(TAG, "    complexityRange = ${encoderCaps.complexityRange}")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        logD(TAG, "    qualityRange = ${encoderCaps.qualityRange}")
                    }
                    logD(TAG, "    CBR = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)}")
                    logD(TAG, "    CQ  = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)}")
                    logD(TAG, "    VBR = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)}")

                    val audioCaps = capabilities.audioCapabilities
                    logD(TAG, "  Audio Capabilities :")
                    logD(TAG, "    bitrateRange = ${audioCaps.bitrateRange}")
                    logD(TAG, "    maxInputChannelCount = ${audioCaps.maxInputChannelCount}")
                    logD(TAG, "    supportedSampleRateRanges = ${audioCaps.supportedSampleRateRanges}")
                    logD(TAG, "    supportedSampleRates = ${audioCaps.supportedSampleRates}")
                }
            }
        }

        return encoderName
    }

    fun calcBufferLengthNanoSec(byteSize: Int): Long {
        val sampleSize = when (AUDIO_FORMAT) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            else -> throw RuntimeException("Unsupported AudioFormat = $AUDIO_FORMAT")
        }

        val sampleCount = byteSize / sampleSize

        val lengthInSec = sampleCount.toFloat() / SAMPLING_RATE.toFloat()

        val lengthInNanoSec = lengthInSec * 1000 * 1000 * 1000

        return lengthInNanoSec.toLong()
    }
}
