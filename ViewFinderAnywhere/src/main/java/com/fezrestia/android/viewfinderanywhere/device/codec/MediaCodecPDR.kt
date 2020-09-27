package com.fezrestia.android.viewfinderanywhere.device.codec

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.fezrestia.android.lib.util.log.Log

internal object MediaCodecPDR {
    private const val TAG = "MediaCodecPDR"

    fun getVideoMediaFormat(videoWidth: Int, videoHeight: Int): MediaFormat {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getVideoMediaFormat() : w=$videoWidth, h=$videoHeight")

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

        if (Log.IS_DEBUG) Log.logDebug(TAG, "Video MediaFormat = $format")

        return format
    }

    fun getVideoEncoderName(videoMediaFormat: MediaFormat): String {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

        val encoderName = codecList.findEncoderForFormat(videoMediaFormat)
        if (Log.IS_DEBUG) Log.logDebug(TAG, "Video Encoder Name = $encoderName")

        if (Log.IS_DEBUG) {
            val videoWidth = videoMediaFormat.getInteger(MediaFormat.KEY_WIDTH)
            val videoHeight = videoMediaFormat.getInteger(MediaFormat.KEY_HEIGHT)

            codecList.codecInfos.forEach { codecInfo ->
                if (codecInfo.name == encoderName) {
                    Log.logDebug(TAG, "#### Valid Encoder")
                    Log.logDebug(TAG, "## MediaCodec = ${codecInfo.name}")
                    codecInfo.supportedTypes.forEach { type ->
                        Log.logDebug(TAG, "    type = $type")
                    }
                    val capabilities = codecInfo.getCapabilitiesForType(videoMediaFormat.getString(MediaFormat.KEY_MIME))

                    Log.logDebug(TAG, "  Color Formats :")
                    capabilities.colorFormats.forEach { colorFormat ->
                        Log.logDebug(TAG, "    format = $colorFormat")
                    }

                    val encoderCaps = capabilities.encoderCapabilities
                    Log.logDebug(TAG, "  Encoder Capabilities :")
                    Log.logDebug(TAG, "    complexityRange = ${encoderCaps.complexityRange}")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        Log.logDebug(TAG, "    qualityRange = ${encoderCaps.qualityRange}")
                    }
                    Log.logDebug(TAG, "    CBR = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)}")
                    Log.logDebug(TAG, "    CQ  = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)}")
                    Log.logDebug(TAG, "    VBR = ${encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)}")
                    val videoCaps = capabilities.videoCapabilities

                    Log.logDebug(TAG, "  Video Capabilities :")
                    Log.logDebug(TAG, "    achievableFpsRange = ${videoCaps.getAchievableFrameRatesFor(videoWidth, videoHeight)}")
                    Log.logDebug(TAG, "    supportedFpsRange = ${videoCaps.getSupportedFrameRatesFor(videoWidth, videoHeight)}")
                    Log.logDebug(TAG, "    bpsRange = ${videoCaps.bitrateRange}")
                    Log.logDebug(TAG, "    widthAlign = ${videoCaps.widthAlignment}")
                    Log.logDebug(TAG, "    heightAlign = ${videoCaps.heightAlignment}")
                    Log.logDebug(TAG, "    supportedWidth = ${videoCaps.supportedWidths}")
                    Log.logDebug(TAG, "    supportedHeight = ${videoCaps.supportedHeights}")

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        videoCaps.supportedPerformancePoints?.forEach { perfPoint ->
                            Log.logDebug(TAG, "## performance point = $perfPoint")
                        }
                    }
                }
            }
        }

        return encoderName
    }
}
