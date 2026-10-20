package sh.klattergetz.morzelab

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/** One continuous oscillator per keydown avoids repeated ToneGenerator start artifacts. */
class MorseTone {
    @Volatile private var active = false
    @Volatile var frequencyHz: Int = 700
    private var worker: Thread? = null

    @Synchronized fun start() {
        if (active) return
        active = true
        worker = thread(name = "morse-tone", isDaemon = true) {
            val rate = 44100
            val chunk = ShortArray(512)
            val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuffer, chunk.size * 2) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            var phase = 0.0
            try {
                track.play()
                while (active) {
                    val increment = 2.0 * PI * frequencyHz / rate
                    for (i in chunk.indices) {
                        // Soft gain, prevents clipping.
                        chunk[i] = (sin(phase) * 9000).toInt().toShort()
                        phase += increment
                        if (phase > 2 * PI) phase -= 2 * PI
                    }
                    track.write(chunk, 0, chunk.size)
                }
                track.pause()
                track.flush()
            } finally { track.release() }
        }
    }
    @Synchronized fun stop() { active = false }
    fun release() = stop()
}
