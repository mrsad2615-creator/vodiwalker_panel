package com.neurox.app.di

import android.content.Context
import com.neurox.audio.AudioPolicy
import com.neurox.common.time.SystemClock
import com.neurox.common.time.TimingCalibration
import com.neurox.data.db.NeuroxDatabase
import com.neurox.data.prefs.BackupRestoreCoordinator
import com.neurox.data.prefs.SettingsKeys
import com.neurox.data.repository.AchievementRepositoryImpl
import com.neurox.data.repository.ExerciseRepositoryImpl
import com.neurox.data.repository.PlanRepositoryImpl
import com.neurox.data.repository.ProfileRepositoryImpl
import com.neurox.data.repository.SessionRepositoryImpl
import com.neurox.data.repository.SettingsRepositoryImpl
import com.neurox.domain.model.AudioSettings
import com.neurox.domain.repository.AchievementRepository
import com.neurox.domain.repository.AudioRepository
import com.neurox.domain.repository.ExerciseRepository
import com.neurox.domain.repository.PlanRepository
import com.neurox.domain.repository.ProfileRepository
import com.neurox.domain.repository.SessionRepository
import com.neurox.domain.repository.SettingsRepository
import com.neurox.presentation.di.AppServices
import com.neurox.presentation.i18n.Strings
import com.neurox.feature.session.AndroidHaptics
import com.neurox.feature.session.Haptics
import com.neurox.security.vault.VaultService

/**
 * Manual dependency container.
 *
 * WHY NOT A DI FRAMEWORK: the graph is small, has no scopes that outlive the
 * process, and is created exactly once in [com.neurox.app.NeuroxApplication]. A
 * framework would add a code generator, a build step and a debugging surface for a
 * graph that fits on one screen, and the app stays buildable with the smallest
 * possible dependency set.
 *
 * LIFETIME: one instance per process. Everything here is safe to share: the
 * database is a singleton, the repositories are stateless, and the audio policy is
 * intentionally process-wide so two screens cannot fight over sound.
 */
class AppContainer(context: Context) : AppServices {

    private val appContext: Context = context.applicationContext

    private val database: NeuroxDatabase = NeuroxDatabase.open(appContext)

    override val profileRepository: ProfileRepository = ProfileRepositoryImpl(database.profileDao())
    override val sessionRepository: SessionRepository =
        SessionRepositoryImpl(database.sessionDao(), database.checkpointDao())
    /**
     * One implementation serves both settings surfaces.
     *
     * Audio settings live in the same key/value table as every other preference, so a
     * second implementation could disagree with the first.
     */
    private val settingsImpl: SettingsRepositoryImpl = SettingsRepositoryImpl(database.settingDao())

    override val settingsRepository: SettingsRepository = settingsImpl

    override val audioRepository: AudioRepository = settingsImpl

    override val achievementRepository: AchievementRepository = AchievementRepositoryImpl(database.achievementDao())
    override val exerciseRepository: ExerciseRepository = ExerciseRepositoryImpl(database.exerciseDao())
    override val planRepository: PlanRepository = PlanRepositoryImpl(database.planDao())

    /**
     * Current audio settings for the policy.
     *
     * The policy is consulted on every cue, so it reads through a cached value that
     * the profile loader refreshes. A database read inside the audio path would be a
     * latency risk in exactly the code that must never stutter.
     */
    private val audioSettingsCache: AppServices.AudioSettingsCache = AudioSettingsCache()

    override val audioPolicy = AudioPolicy(
        settingsProvider = { audioSettingsCache.current },
        clock = System::currentTimeMillis,
    )

    override val audioSettings: AppServices.AudioSettingsCache get() = audioSettingsCache

    /**
     * Cue playback.
     *
     * The renderer takes the SAME policy the session audio layer uses, so silent and
     * focus mode are enforced at the one place sound actually leaves the process.
     * Creating it here (not per screen) is also what keeps cues ordered across screens.
     */
    val audioRenderer: com.neurox.audio.platform.AudioRenderer = com.neurox.audio.platform.AudioRenderer(
        policy = audioPolicy,
        settingsProvider = { audioSettingsCache.current },
    )

    val vaultService: VaultService = VaultService()

    val backupCoordinator: BackupRestoreCoordinator = BackupRestoreCoordinator(
        profiles = profileRepository,
        sessions = sessionRepository,
        settings = settingsRepository,
        achievements = achievementRepository,
        exercises = exerciseRepository,
    )

    /** String catalogue for the default language; replaced when a profile loads. */
    override val strings: Strings = Strings.of(SettingsKeys.defaults[SettingsKeys.LANGUAGE_TAG])

    val clock = SystemClock

    /**
     * Haptics.
     *
     * NEUROX SENSE treats vibration as a channel with its own gate, so it is exposed
     * as a small interface rather than a raw Vibrator. Default is a no-op in builds
     * without the permission, which keeps the session path identical everywhere.
     */
    val haptics: Haptics = AndroidHaptics(appContext)

    /**
     * Calibration is per device, not per profile: the hardware does not change when
     * the user switches profile, so measuring it once per device is correct.
     */
    fun calibration(inputMillis: Float, displayMillis: Float): TimingCalibration =
        TimingCalibration(
            inputLatencyMillis = inputMillis.toDouble(),
            displayLatencyMillis = displayMillis.toDouble(),
        )

    /** Mutable holder for the audio settings the policy reads. Never performs I/O. */
    class AudioSettingsCache : AppServices.AudioSettingsCache {
        @Volatile
        override var current: AudioSettings = AudioSettings()
            private set

        override fun update(settings: AudioSettings) {
            current = settings
        }
    }
}
