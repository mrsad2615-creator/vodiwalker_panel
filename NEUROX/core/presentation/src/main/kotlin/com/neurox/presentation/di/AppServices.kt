package com.neurox.presentation.di

import com.neurox.audio.AudioPolicy
import com.neurox.domain.model.AudioSettings
import com.neurox.domain.repository.AchievementRepository
import com.neurox.domain.repository.AudioRepository
import com.neurox.domain.repository.ExerciseRepository
import com.neurox.domain.repository.PlanRepository
import com.neurox.domain.repository.ProfileRepository
import com.neurox.domain.repository.SessionRepository
import com.neurox.domain.repository.SettingsRepository
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.io.BackupFiles

/**
 * The services a screen is allowed to use.
 *
 * WHY THIS EXISTS (dependency inversion)
 * The feature modules are screens. Before this interface, every route imported
 * `com.neurox.app.di.AppContainer` - the *application* module's class - to reach the
 * repositories. That is a dependency cycle: `:app` depends on every feature, so no
 * feature can depend on `:app`. It compiled only because the old audit compiled each
 * module in isolation and treated the missing symbol as a "platform gap".
 *
 * The fix is the standard one: the screens declare the interface they need, in a
 * module they already depend on (`core:presentation`), and the application supplies an
 * implementation. `core:presentation` stays pure JVM - no Android, no Compose - so
 * this interface is testable with fakes and the compile gate keeps working.
 *
 * WHAT IS NOT HERE
 * `VaultService`, `BackupRestoreCoordinator` and the haptics implementation are
 * Android/IO concerns; the few screens that need them take them as explicit
 * parameters, so a screen's dependency list is visible in its signature.
 */
interface AppServices {

    val profileRepository: ProfileRepository
    val sessionRepository: SessionRepository
    val settingsRepository: SettingsRepository
    val audioRepository: AudioRepository
    val achievementRepository: AchievementRepository
    val exerciseRepository: ExerciseRepository
    val planRepository: PlanRepository

    /** The audio policy consulted before every cue. Process-wide on purpose. */
    val audioPolicy: AudioPolicy

    /** Cached audio settings the policy reads; never performs I/O. */
    val audioSettings: AudioSettingsCache

    /**
     * Backup file I/O.
     *
     * On the boundary rather than passed down from the Activity because two screens
     * await it directly: export needs to write the bytes it produces, and import
     * needs to read the file the user picks. Both were previously wired to empty
     * lambdas, which made backup and restore unreachable features.
     */
    val backupFiles: BackupFiles

    /**
     * Current string catalogue.
     *
     * Screens receive the catalogue as a parameter and never read it from here; this
     * property exists so the shell can decide what to pass down.
     */
    val strings: Strings

    /** Holds the audio settings the policy reads. */
    interface AudioSettingsCache {
        val current: AudioSettings
        fun update(settings: AudioSettings)
    }
}
