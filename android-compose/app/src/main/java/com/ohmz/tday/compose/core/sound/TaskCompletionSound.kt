package com.ohmz.tday.compose.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ohmz.tday.compose.R

/**
 * The short pop played when a task is checked off — the same clip the web app plays, so
 * completing a task sounds identical on web, Android and iOS.
 *
 * SoundPool rather than MediaPlayer: the clip is ~13KB and fires on a tap, so it has to be
 * decoded once up front and replayed with no setup cost. MediaPlayer would re-prepare on every
 * completion and lag behind the animation.
 *
 * Deliberately a process-wide singleton. SoundPool owns a decoder and an audio track, and a row is
 * recomposed constantly, so one per row (or per screen) would leak them steadily.
 */
class TaskCompletionSound private constructor(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // Sonification, not media: this is a UI cue, so it follows the system volume and
                // ducks under music rather than fighting whatever the user is listening to.
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var soundId: Int = 0
    @Volatile private var loaded: Boolean = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        soundId = soundPool.load(context, R.raw.task_complete, 1)
    }

    /**
     * Plays the pop, unless the phone is silenced. No-ops while the clip is still decoding — a
     * completion in the first moments after launch is silent rather than delayed, which is the
     * better of the two.
     */
    fun play() {
        if (!loaded) return
        // Ringer mode is the user saying "no noise from apps"; honour it the way the system UI
        // sounds do rather than making this the one thing that still chirps in a meeting.
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        soundPool.play(soundId, VOLUME, VOLUME, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
    }

    companion object {
        private const val MAX_STREAMS = 2
        private const val VOLUME = 0.5f

        @Volatile private var instance: TaskCompletionSound? = null

        fun get(context: Context): TaskCompletionSound =
            instance ?: synchronized(this) {
                instance ?: TaskCompletionSound(context.applicationContext).also { instance = it }
            }
    }
}

/** Remembers the process-wide player so a row can fire the pop without any DI plumbing. */
@Composable
fun rememberTaskCompletionSound(): TaskCompletionSound {
    val context = LocalContext.current
    return remember(context) { TaskCompletionSound.get(context.applicationContext) }
}
