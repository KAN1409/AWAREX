package com.kareem.awarex.ui.now

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
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

@Composable
fun NowScreen(viewModel: NowViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationAccessEnabled by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    LifecycleResumeEffect(viewModel) {
        notificationAccessEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Header(
                    insightCount = state.insights.size,
                    situationCount = state.situations.size,
                    attentionCount = state.attention.size
                )
            }

            item { NotificationAwarenessCard(enabled = notificationAccessEnabled) }

            state.message?.let { message ->
                item { StatusCard(message = message, isError = false) }
            }
            state.error?.let { error ->
                item { StatusCard(message = error, isError = true) }
            }

            if (state.insights.isNotEmpty()) {
                item { SectionTitle("AWAREX noticed") }
                items(state.insights, key = { "insight:${it.id}" }) { insight ->
                    InsightCardView(insight)
                }
            }

            if (state.attention.isNotEmpty()) {
                item { SectionTitle("Needs you") }
                items(state.attention, key = { "attention:${it.loopId}" }) { card ->
                    AttentionCardView(card)
                }
            }

            if (state.situations.isNotEmpty()) {
                item { SectionTitle("Developing") }
                items(state.situations, key = { "situation:${it.id}" }) { situation ->
                    SituationCardView(situation)
                }
            }

            item {
                CaptureCard(
                    input = state.input,
                    working = state.working,
                    onInputChanged = viewModel::onInputChanged,
                    onCapture = viewModel::capture
                )
            }

            item { SectionTitle("Evidence") }
            if (state.recentEvidence.isEmpty()) {
                item { EmptyEvidence() }
            } else {
                items(state.recentEvidence, key = { "evidence:${it.id}" }) { observation ->
                    EvidenceRow(observation)
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun Header(insightCount: Int, situationCount: Int, attentionCount: Int) {
    val summary = when {
        insightCount > 0 -> "$insightCount ${if (insightCount == 1) "discovery" else "discoveries"} across $situationCount ${if (situationCount == 1) "situation" else "situations"}."
        attentionCount > 0 -> "$attentionCount ${if (attentionCount == 1) "thing needs" else "things need"} your attention."
        situationCount > 0 -> "Tracking $situationCount evolving ${if (situationCount == 1) "situation" else "situations"}."
        else -> "Watching for changes, commitments and connections that matter."
    }

    Column(modifier = Modifier.padding(top = 18.dp, bottom = 2.dp)) {
        Text(
            text = "AWAREX",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Now",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NotificationAwarenessCard(enabled: Boolean) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) "PASSIVE AWARENESS · ACTIVE" else "PASSIVE AWARENESS · OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                if (!enabled) {
                    Text(
                        text = "Notification access is needed for background evidence.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            ) {
                Text(if (enabled) "Settings" else "Enable", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InsightCardView(insight: InsightCard) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(19.dp)) {
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
                    text = "${(insight.confidence * 100).roundToInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = insight.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = insight.body,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = insight.whyItMatters,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(15.dp))
            Text(
                text = "Evidence",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(5.dp))
            insight.evidenceTexts.take(2).forEachIndexed { index, evidence ->
                Text(
                    text = if (insight.evidenceTexts.size > 1) "${index + 1}. $evidence" else evidence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (index < insight.evidenceTexts.take(2).lastIndex) Spacer(Modifier.height(5.dp))
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
                    Text(
                        text = formatTime(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(card.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(card.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(13.dp))
            Text("Evidence", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(card.evidenceText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SituationCardView(situation: SituationCard) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
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
                    color = when (situation.state) {
                        SituationState.WAITING -> MaterialTheme.colorScheme.primary
                        SituationState.CHANGED -> MaterialTheme.colorScheme.tertiary
                        SituationState.RESOLVED -> MaterialTheme.colorScheme.primary
                        SituationState.DEVELOPING -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatTime(situation.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = situation.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = situation.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            val footer = buildString {
                append(situation.evidenceCount)
                append(if (situation.evidenceCount == 1) " evidence" else " evidence items")
                situation.primaryEntity?.takeIf(String::isNotBlank)?.let {
                    append(" · ")
                    append(it)
                }
            }
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "ADD EVIDENCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Add something AWAREX did not observe itself. It will connect it to the world model automatically.",
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
                placeholder = { Text("Example: Revised Negma marble quotation is 447000 EGP") },
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(11.dp))
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
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Add to AWAREX", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun EvidenceRow(observation: Observation) {
    Card(
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                text = observation.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "${sourceLabel(observation.source)} · ${formatTime(observation.observedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyEvidence() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Useful evidence will appear here when AWAREX has something durable to remember.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "manual" -> "Manual"
    "notification:com.whatsapp" -> "WhatsApp"
    "notification:com.facebook.orca" -> "Messenger"
    "notification:org.telegram.messenger" -> "Telegram"
    "notification:com.google.android.gm" -> "Gmail"
    else -> source.removePrefix("notification:").substringAfterLast('.').ifBlank { source }
}

private fun formatTime(epochMillis: Long): String = DateTimeFormatter
    .ofPattern("dd MMM · HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))
