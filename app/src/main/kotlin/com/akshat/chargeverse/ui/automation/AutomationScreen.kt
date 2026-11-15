package com.akshat.chargeverse.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.R
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.data.db.AutomationAction
import com.akshat.chargeverse.data.db.AutomationTrigger
import com.akshat.chargeverse.ui.components.CvIcon
import com.akshat.chargeverse.ui.components.EmptyState
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle

@Composable
fun AutomationScreen(viewModel: AutomationViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(top = 56.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Automation", style = MaterialTheme.typography.headlineMedium)
            Text("Rules that react to charging and battery events", style = MaterialTheme.typography.bodyMedium, color = DimText)
        }
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("Rules (${state.rules.size})", Modifier.weight(1f))
            Box(
                Modifier
                    .background(NeonBlue.copy(alpha = 0.16f), MaterialTheme.shapes.small)
                    .clickable { showCreate = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CvIcon(R.drawable.ic_util_plus, NeonBlue, Modifier.height(15.dp))
                    Spacer(Modifier.padding(4.dp))
                    Text("New rule", style = MaterialTheme.typography.labelLarge, color = NeonBlue)
                }
            }
        }

        if (showCreate) {
            CreateRuleCard(
                profiles = state.profiles,
                onCancel = { showCreate = false },
                onCreate = { trigger, value, action, profileId ->
                    viewModel.createRule(trigger, value, action, profileId)
                    showCreate = false
                },
            )
        }

        if (state.rules.isEmpty() && !showCreate) {
            EmptyState(
                iconRes = R.drawable.ic_nav_automation,
                title = "No rules yet",
                body = "Create a rule, e.g. “when charger connects → play connect sound”.",
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { viewModel.toggle(rule) },
                        onDelete = { viewModel.delete(rule) },
                        onTest = { viewModel.fire(rule) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateRuleCard(
    profiles: List<com.akshat.chargeverse.data.db.AnimationProfileEntity>,
    onCancel: () -> Unit,
    onCreate: (AutomationTrigger, Int, AutomationAction, Long?) -> Unit,
) {
    var trigger by remember { mutableStateOf(AutomationTrigger.CHARGER_CONNECTED) }
    var percent by remember { mutableStateOf(50) }
    var action by remember { mutableStateOf(AutomationAction.PLAY_CONNECT_SOUND) }
    var profileId by remember { mutableStateOf<Long?>(null) }

    GlassCard(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text("New automation rule", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Text("WHEN", style = MaterialTheme.typography.labelMedium, color = DimText)
        TriggerPicker(trigger) { trigger = it }
        if (trigger == AutomationTrigger.PERCENT_ABOVE || trigger == AutomationTrigger.PERCENT_BELOW) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Threshold: $percent%", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.Slider(
                    value = percent.toFloat(),
                    onValueChange = { percent = it.toInt() },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
        }
        Text("THEN", style = MaterialTheme.typography.labelMedium, color = DimText)
        ActionPicker(action) { action = it }
        if (action == AutomationAction.APPLY_PROFILE && profiles.isNotEmpty()) {
            Text("Profile", style = MaterialTheme.typography.labelMedium, color = DimText)
            profiles.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { profileId = p.id }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CvIcon(
                        if (profileId == p.id) R.drawable.ic_util_check else R.drawable.ic_nav_animations,
                        if (profileId == p.id) NeonBlue else DimText,
                        Modifier.height(16.dp),
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            androidx.compose.material3.Button(onClick = {
                onCreate(trigger, percent, action, if (action == AutomationAction.APPLY_PROFILE) profileId else null)
            }) { Text("Create rule") }
        }
    }
}

@Composable
private fun TriggerPicker(selected: AutomationTrigger, onSelect: (AutomationTrigger) -> Unit) {
    Column {
        AutomationTrigger.entries.forEach { t ->
            ChoiceRow(t.name.lowercase().replace('_', ' '), selected == t) { onSelect(t) }
        }
    }
}

@Composable
private fun ActionPicker(selected: AutomationAction, onSelect: (AutomationAction) -> Unit) {
    Column {
        AutomationAction.entries.forEach { a ->
            ChoiceRow(a.name.lowercase().replace('_', ' '), selected == a) { onSelect(a) }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(14.dp)
                .background(
                    if (selected) NeonBlue else androidx.compose.ui.graphics.Color(0x33FFFFFF),
                    androidx.compose.foundation.shape.CircleShape,
                )
                .padding(2.dp)
        )
        Spacer(Modifier.padding(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RuleRow(
    rule: com.akshat.chargeverse.data.db.AutomationRuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GlassFill, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                triggerLabel(rule),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "→ " + rule.action.name.lowercase().replace('_', ' ') +
                    if (rule.targetProfileId != null) " (profile)" else "",
                style = MaterialTheme.typography.labelMedium,
                color = NeonPurple,
            )
            Text(
                if (rule.enabled) "Active" else "Paused",
                style = MaterialTheme.typography.labelSmall,
                color = DimText,
            )
        }
        CvIcon(R.drawable.ic_util_play, NeonBlue, Modifier.padding(6.dp).clickable(onClick = onTest))
        Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        CvIcon(R.drawable.ic_util_trash, DimText, Modifier.padding(6.dp).clickable(onClick = onDelete))
    }
}

private fun triggerLabel(rule: com.akshat.chargeverse.data.db.AutomationRuleEntity): String =
    when (rule.trigger) {
        AutomationTrigger.PERCENT_ABOVE -> "Battery above ${rule.triggerValue}%"
        AutomationTrigger.PERCENT_BELOW -> "Battery below ${rule.triggerValue}%"
        else -> rule.trigger.name.lowercase().replace('_', ' ')
    }
