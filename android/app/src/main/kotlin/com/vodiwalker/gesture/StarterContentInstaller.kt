package com.vodiwalker.gesture

import android.util.Log
import com.vodiwalker.gesture.core.database.StarterContent
import com.vodiwalker.gesture.core.datastore.UserSettingsDataSource
import com.vodiwalker.gesture.core.model.ActionGraphRepository
import com.vodiwalker.gesture.core.model.GestureTemplateRepository
import com.vodiwalker.gesture.core.model.TriggerRuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the first-run library exactly once.
 *
 * Rationale: a gesture app whose library is empty after setup demonstrates nothing. This installer
 * seeds three purposeful gestures (home / back / notifications) the first time the disclosure is
 * accepted, then records the fact so later launches never resurrect content the user deleted.
 *
 * The write is idempotent by identifier, so a crash mid-seed simply resumes on the next launch.
 */
@Singleton
class StarterContentInstaller @Inject constructor(
    private val templateRepository: GestureTemplateRepository,
    private val graphRepository: ActionGraphRepository,
    private val ruleRepository: TriggerRuleRepository,
    private val settingsDataSource: UserSettingsDataSource,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Seeds starter content if it has never been seeded. Safe to call on every launch.
     */
    fun installIfNeeded() {
        scope.launch {
            val settings = settingsDataSource.settingsFlow.first()
            if (settings.starterContentInstalled) return@launch

            withContext(Dispatchers.IO) {
                runCatching {
                    val now = System.currentTimeMillis()
                    StarterContent.graphs().forEach { graphRepository.upsertGraph(it) }
                    StarterContent.templates(now).forEach { templateRepository.upsertTemplate(it) }
                    StarterContent.rules().forEach { ruleRepository.upsertRule(it) }
                    settingsDataSource.setStarterContentInstalled(true)
                }.onFailure { error ->
                    // Never block the UI: the next launch retries.
                    Log.w(TAG, "Starter content install deferred", error)
                }
            }
        }
    }

    private companion object {
        const val TAG = "VodiStarterContent"
    }
}
