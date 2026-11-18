package ir.aegis.feature.academy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.launch

@Composable
fun PreparednessAcademyScreen(
    repository: AegisRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalObsidianColors.current
    val scope = rememberCoroutineScope()
    val progressList by repository.observeAcademyProgress().collectAsState(initial = emptyList())
    val progressMap = remember(progressList) { progressList.associateBy { it.courseId } }

    var selectedCourseId by remember { mutableStateOf(AcademyCurriculum.courses.first().id) }
    var activeTab by remember { mutableIntStateOf(0) }

    val course = AcademyCurriculum.courses.first { it.id == selectedCourseId }
    val prog = progressMap[course.id] ?: AcademyProgressRecord(courseId = course.id)

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.ACADEMY,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AcademyCurriculum.courses, key = { it.id }) { c ->
                val cp = progressMap[c.id]
                FilterChip(
                    selected = c.id == selectedCourseId,
                    onClick = { selectedCourseId = c.id },
                    label = {
                        Text("${c.domain.name}${if (cp?.badgeEarned == true) " ★" else ""}", style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        GlassTabRow(
            tabs = listOf(
                tr("Lessons", "درس‌ها"),
                tr("Quiz", "آزمون"),
                tr("Scenario", "سناریو"),
                tr("Progress", "پیشرفت"),
            ),
            selectedIndex = activeTab,
            onSelect = { activeTab = it },
        )
        Spacer(Modifier.height(10.dp))

        when (activeTab) {
            0 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(course.title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                            Text(course.subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                    }
                    items(course.lessons, key = { it.id }) { lesson ->
                        val done = lesson.id in prog.completedLessonIds
                        ObsidianGlassCard(accentBorderColor = if (done) colors.accentEmerald else null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(lesson.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                Text("${lesson.durationMinutes} min", style = MaterialTheme.typography.labelSmall, color = colors.accentCyan)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(lesson.summary, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            lesson.keySteps.forEachIndexed { idx, s ->
                                Text("${idx + 1}. $s", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("! ${lesson.warningNote}", style = MaterialTheme.typography.labelSmall, color = colors.accentAmber)
                            Spacer(Modifier.height(6.dp))
                            GlassButton(
                                label = if (done) tr("Completed ✓", "تکمیل شد ✓") else tr("Mark Lesson Complete", "تکمیل درس"),
                                accent = if (done) colors.accentEmerald else colors.accentCyan,
                                onClick = {
                                    scope.launch {
                                        val nextSet = if (done) prog.completedLessonIds else prog.completedLessonIds + lesson.id
                                        val earned = prog.badgeEarned || (nextSet.size >= course.lessons.size && prog.bestQuizScore >= 1)
                                        repository.upsertAcademyProgress(
                                            prog.copy(
                                                completedLessonIds = nextSet,
                                                totalQuizQuestions = course.quiz.size,
                                                badgeEarned = earned,
                                                updatedAtMillis = System.currentTimeMillis(),
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            1 -> {
                var selectedAnswers by remember(course.id) { mutableStateOf<Map<String, Int>>(emptyMap()) }
                var submittedScore by remember(course.id) { mutableStateOf<Int?>(null) }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(course.quiz, key = { it.id }) { q ->
                        ObsidianGlassCard {
                            Text(q.question, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            q.options.forEachIndexed { idx, opt ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedAnswers[q.id] == idx, onClick = { selectedAnswers = selectedAnswers + (q.id to idx) })
                                    Text(opt, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                }
                            }
                            if (submittedScore != null) {
                                val isRight = selectedAnswers[q.id] == q.correctIndex
                                Text(
                                    (if (isRight) "✓ " else "✗ ") + q.explanation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRight) colors.accentEmerald else colors.accentCrimson,
                                )
                            }
                        }
                    }
                    item {
                        GlassButton(
                            label = tr("Grade Quiz & Save Score", "ثبت نمره آزمون"),
                            accent = colors.accentTeal,
                            onClick = {
                                val score = course.quiz.count { selectedAnswers[it.id] == it.correctIndex }
                                submittedScore = score
                                scope.launch {
                                    val best = maxOf(prog.bestQuizScore, score)
                                    val badge = best >= ((course.quiz.size * 2) / 3).coerceAtLeast(1)
                                    repository.upsertAcademyProgress(
                                        prog.copy(
                                            bestQuizScore = best,
                                            totalQuizQuestions = course.quiz.size,
                                            badgeEarned = prog.badgeEarned || badge,
                                            updatedAtMillis = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                            },
                        )
                        if (submittedScore != null) {
                            Spacer(Modifier.height(6.dp))
                            Text("${tr("Score", "نمره")}: $submittedScore / ${course.quiz.size}", style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                        }
                    }
                }
            }

            2 -> {
                val node = course.scenario.nodes.first()
                var chosen by remember(course.id) { mutableStateOf<AcademyScenarioChoice?>(null) }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text("${tr("Interactive Scenario", "سناریوی تعاملی")}: ${course.scenario.title}", style = MaterialTheme.typography.labelMedium, color = colors.accentAmber)
                            Spacer(Modifier.height(6.dp))
                            Text(node.prompt, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        }
                    }
                    items(node.choices) { choice ->
                        val optimal = choice.scoreDelta >= 75
                        ObsidianGlassCard(
                            enable3DTilt = true,
                            accentBorderColor = if (chosen == choice) (if (optimal) colors.accentEmerald else colors.accentCrimson) else null,
                            onClick = {
                                chosen = choice
                                scope.launch {
                                    repository.upsertAcademyProgress(
                                        prog.copy(
                                            scenarioBestScore = maxOf(prog.scenarioBestScore, choice.scoreDelta),
                                            updatedAtMillis = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                            },
                        ) {
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                        }
                    }
                    chosen?.let { sel ->
                        val optimal = sel.scoreDelta >= 75
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = (if (optimal) colors.accentEmerald else colors.accentAmber).copy(alpha = 0.16f),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("${tr("Tactical Score", "امتیاز تاکتیکی")}: ${sel.scoreDelta}/100", style = MaterialTheme.typography.titleMedium, color = if (optimal) colors.accentEmerald else colors.accentAmber)
                                    Spacer(Modifier.height(4.dp))
                                    Text(sel.feedback, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                val totalLessons = AcademyCurriculum.courses.sumOf { it.lessons.size }
                val completedLessons = AcademyCurriculum.courses.sumOf { c -> progressMap[c.id]?.completedLessonIds?.size ?: 0 }
                val badgesCount = progressMap.values.count { it.badgeEarned }
                val pct = if (totalLessons > 0) (completedLessons * 100 / totalLessons) else 0

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(tr("Overall Academy Mastery: $pct%", "تسلط کلی آکادمی: $pct٪"), style = MaterialTheme.typography.titleLarge, color = colors.accentCyan)
                            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = colors.accentTeal)
                            Text("$completedLessons / $totalLessons lessons completed · $badgesCount / ${AcademyCurriculum.courses.size} badges earned", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                    }
                    items(AcademyCurriculum.courses, key = { it.id }) { c ->
                        val cp = progressMap[c.id]
                        ObsidianGlassCard {
                            Text(c.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text(
                                "Lessons: ${cp?.completedLessonIds?.size ?: 0}/${c.lessons.size} · Quiz Best: ${cp?.bestQuizScore ?: 0}/${c.quiz.size} · Scenario Best: ${cp?.scenarioBestScore ?: 0}/100 · ${if (cp?.badgeEarned == true) "BADGE EARNED ★" else "In Progress"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cp?.badgeEarned == true) colors.accentEmerald else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
