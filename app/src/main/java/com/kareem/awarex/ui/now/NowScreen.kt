package com.kareem.awarex.ui.now

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kareem.awarex.core.model.AttentionCard
import com.kareem.awarex.core.model.InsightCard
import com.kareem.awarex.core.model.InsightKind
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.core.model.SituationCard
import com.kareem.awarex.core.model.SituationState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class AppTab(val label: String) {
    NOW("Now"),
    WORLD("World"),
    MEMORY("Memory"),
    SETTINGS("Settings")
}

@Composable
fun NowScreen(
    incomingSharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    viewModel: NowViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.NOW) }
    var notificationAccessEnabled by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }
    var proactivePermissionGranted by remember { mutableStateOf(hasPostNotificationPermission(context)) }
    val proactivePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> proactivePermissionGranted = granted }

    LaunchedEffect(incomingSharedText) {
        val text = incomingSharedText?.trim().orEmpty()
        if (text.isNotEmpty()) {
            viewModel.captureExternal(text)
            selectedTab = AppTab.NOW
            onSharedTextConsumed()
        }
    }

    LifecycleResumeEffect(viewModel) {
        notificationAccessEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
        proactivePermissionGranted = hasPostNotificationPermission(context)
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(if (selectedTab == tab) "●" else "○") },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            ) {
                state.message?.let { message ->
                    StatusStrip(message = message, isError = false)
                }
                state.error?.let { error ->
                    StatusStrip(message = error, isError = true)
                }
                when (selectedTab) {
                    AppTab.NOW -> NowSurface(
                        state = state,
                        notificationAccessEnabled = notificationAccessEnabled,
                        onInputChanged = viewModel::onInputChanged,
                        onCapture = viewModel::capture,
                        onDismissInsight = viewModel::dismissInsight,
                        onSnoozeInsight = viewModel::snoozeInsight
                    )
                    AppTab.WORLD -> WorldSurface(state.situations)
                    AppTab.MEMORY -> MemorySurface(state.recentEvidence)
                    AppTab.SETTINGS -> SettingsSurface(
                        notificationAccessEnabled = notificationAccessEnabled,
                        proactiveEnabled = state.proactiveEnabled,
                        proactivePermissionGranted = proactivePermissionGranted,
                        onOpenNotificationAccess = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onRequestProactivePermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                proactivePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onProactiveEnabledChanged = viewModel::setProactiveEnabled,
                        onRestoreInsights = viewModel::restoreInsights
                    )
                }
            }
        }
    }
}

@Composable
private fun NowSurface(
    state: NowUiState,
    notificationAccessEnabled: Boolean,
    onInputChanged: (String) -> Unit,
    onCapture: () -> Unit,
    onDismissInsight: (String) -> Unit,
    onSnoozeInsight: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ProductHeader(
                title = "Now",
                subtitle = when {
                    state.insights.isNotEmpty() -> "${state.insights.size} ${if (state.insights.size == 1) "thing" else "things"} AWAREX noticed for you."
                    state.attention.isNotEmpty() -> "${state.attention.size} open ${if (state.attention.size == 1) "loop" else "loops"} worth tracking."
                    else -> "Quiet by design. Nothing has earned your attention."
                }
            )
        }
        item { AwarenessPulse(active = notificationAccessEnabled) }

        if (state.insights.isNotEmpty()) {
            item { SectionTitle("AWAREX noticed") }
            items(state.insights, key = { "insight:${it.id}" }) { insight ->
                InsightCardView(
                    insight = insight,
                    onDismiss = { onDismissInsight(insight.id) },
                    onSnooze = { onSnoozeInsight(insight.id) }
                )
            }
        }

        if (state.attention.isNotEmpty()) {
            item { SectionTitle("Needs you") }
            items(state.attention, key = { "attention:${it.loopId}" }) { card ->
                AttentionCardView(card)
            }
        }

        if (state.insights.isEmpty() && state.attention.isEmpty() && state.situations.isNotEmpty()) {
            item {
                QuietCard("AWAREX is building context from ${state.situations.size} active ${if (state.situations.size == 1) "situation" else "situations"}. Nothing needs interrupting you yet.")
            }
        }

        item {
            CaptureCard(
                input = state.input,
                working = state.working,
                onInputChanged = onInputChanged,
                onCapture = onCapture
            )
        }
    }
}

@Composable
private fun WorldSurface(situations: List<SituationCard>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ProductHeader(
                title = "World",
                subtitle = if (situations.isEmpty()) {
                    "Situations will appear when evidence begins to connect."
                } else {
                    "${situations.size} evolving ${if (situations.size == 1) "situation" else "situations"} built from real evidence."
                }
            )
        }
        if (situations.isEmpty()) {
            item { QuietCard("AWAREX has not connected enough evidence into a situation yet.") }
        } else {
            items(situations, key = { "world:${it.id}" }) { situation ->
                SituationCardView(situation)
            }
        }
    }
}

@Composable
private fun MemorySurface(evidence: List<Observation>) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(evidence, query) {
        val needle = query.trim()
        if (needle.isEmpty()) evidence else evidence.filter {
            it.text.contains(needle, ignoreCase = true) || it.source.contains(needle, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ProductHeader(
                title = "Memory",
                subtitle = "Canonical evidence: the useful history AWAREX can reason over."
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search people, projects, values or evidence") },
                shape = RoundedCornerShape(18.dp)
            )
        }
        if (filtered.isEmpty()) {
            item { QuietCard(if (query.isBlank()) "Memory starts with the first real observation." else "No evidence matches this search.") }
        } else {
            items(filtered, key = { "memory:${it.id}" }) { observation ->
                EvidenceRow(observation)
            }
        }
    }
}

@Composable
private fun SettingsSurface(
    notificationAccessEnabled: Boolean,
    proactiveEnabled: Boolean,
    proactivePermissionGranted: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onRequestProactivePermission: () -> Unit,
    onProactiveEnabledChanged: (Boolean) -> Unit,
    onRestoreInsights: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ProductHeader(
                title = "Settings",
                subtitle = "AWAREX 1.0 · local-first awareness with evidence before conclusions."
            )
        }
        item {
            SettingsCard(
                title = "Passive awareness",
                status = if (notificationAccessEnabled) "ACTIVE" else "OFF",
                body = "Observe useful notification text while filtering operational and social noise before it reaches memory."
            ) {
                OutlinedButton(onClick = onOpenNotificationAccess, modifier = Modifier.fillMaxWidth()) {
                    Text(if (notificationAccessEnabled) "Notification access settings" else "Enable notification awareness")
                }
            }
        }
        item {
            SettingsCard(
                title = "Proactive alerts",
                status = when {
                    !proactiveEnabled -> "PAUSED"
                    proactivePermissionGranted -> "ACTIVE"
                    else -> "PERMISSION NEEDED"
                },
                body = "AWAREX may alert you about overdue commitments or high-value discoveries. Repeat alerts are rate-limited."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Allow proactive attention", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = proactiveEnabled, onCheckedChange = onProactiveEnabledChanged)
                }
                if (!proactivePermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequestProactivePermission, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow Android notifications")
                    }
                }
            }
        }
        item {
            SettingsCard(
                title = "Insight controls",
                status = "PRIVATE",
                body = "Dismissed and snoozed insights stay on this device and only affect what AWAREX surfaces to you."
            ) {
                OutlinedButton(onClick = onRestoreInsights, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore dismissed and snoozed insights")
                }
            }
        }
        item {
            QuietCard("Core capture, commitments, change detection, situations and prioritization continue locally even if no cloud AI is available.")
        }
    }
}

@Composable
private fun ProductHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = "AWAREX",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(5.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AwarenessPulse(active: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (active) "PASSIVE AWARENESS · ACTIVE" else "PASSIVE AWARENESS · OFF",
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Text("local-first", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightCardView(
    insight: InsightCard,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (insight.kind) {
                        InsightKind.CHANGE -> "CHANGE DETECTED"
                        InsightKind.COMPLETION -> "LOOP CLOSED"
                        InsightKind.DISCOVERY -> "DISCOVERY"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${(insight.confidence * 100).roundToInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(insight.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(insight.body, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Text("Why it matters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(3.dp))
            Text(insight.whyItMatters, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (insight.evidenceTexts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Evidence", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                insight.evidenceTexts.take(3).forEach { evidence ->
                    Spacer(Modifier.height(4.dp))
                    Text("• $evidence", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSnooze) { Text("Snooze 24h") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun AttentionCardView(card: AttentionCard) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (card.overdue) "OVERDUE" else "MONITORING",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (card.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                card.dueAt?.let {
                    Text(formatTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(card.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(card.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("Evidence", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(card.evidenceText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SituationCardView(situation: SituationCard) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (situation.state) {
                        SituationState.WAITING -> "WAITING"
                        SituationState.CHANGED -> "CHANGED"
                        SituationState.RESOLVED -> "RESOLVED"
                        SituationState.DEVELOPING -> "DEVELOPING"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text("${situation.evidenceCount} evidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(7.dp))
            Text(situation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            situation.primaryEntity?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(7.dp))
            Text(situation.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Updated ${formatTime(situation.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CaptureCard(
    input: String,
    working: Boolean,
    onInputChanged: (String) -> Unit,
    onCapture: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ADD CONTEXT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tell AWAREX something real when passive capture cannot see it. It becomes evidence, not a chat message.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                enabled = !working,
                placeholder = { Text("e.g. Revised Negma marble quotation 447000 EGP") },
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onCapture,
                modifier = Modifier.fillMaxWidth(),
                enabled = input.isNotBlank() && !working,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Add evidence", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EvidenceRow(observation: Observation) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(observation.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(7.dp))
            Text(
                "${humanSource(observation.source)} · ${formatTime(observation.observedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    status: String,
    body: String,
    content: @Composable Column.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun QuietCard(text: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(text, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusStrip(message: String, isError: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            message,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

private fun hasPostNotificationPermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun humanSource(source: String): String = when {
    source == "manual" -> "manual"
    source == "share" -> "shared to AWAREX"
    source.startsWith("notification:") -> source.removePrefix("notification:")
    else -> source
}

private fun formatTime(epochMillis: Long): String = DateTimeFormatter
    .ofPattern("dd MMM · HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))
