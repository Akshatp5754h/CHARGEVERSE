package com.akshat.chargeverse.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.data.db.AutomationAction
import com.akshat.chargeverse.data.db.AutomationRuleEntity
import com.akshat.chargeverse.data.db.AutomationTrigger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutomationUiState(
    val rules: List<AutomationRuleEntity> = emptyList(),
    val profiles: List<com.akshat.chargeverse.data.db.AnimationProfileEntity> = emptyList(),
)

class AutomationViewModel : ViewModel() {

    private val db = ServiceLocator.db

    val ui: StateFlow<AutomationUiState> = combine(
        db.automationRuleDao().observeAll(),
        db.animationProfileDao().observeAll(),
    ) { rules, profiles ->
        AutomationUiState(rules, profiles)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AutomationUiState())

    fun createRule(
        trigger: AutomationTrigger,
        triggerValue: Int,
        action: AutomationAction,
        targetProfileId: Long?,
    ) = viewModelScope.launch {
        db.automationRuleDao().insert(
            AutomationRuleEntity(
                trigger = trigger,
                triggerValue = triggerValue,
                action = action,
                targetProfileId = targetProfileId,
                enabled = true,
                createdAtMs = System.currentTimeMillis(),
            )
        )
    }

    fun toggle(rule: AutomationRuleEntity) = viewModelScope.launch {
        db.automationRuleDao().update(rule.copy(enabled = !rule.enabled))
    }

    fun delete(rule: AutomationRuleEntity) = viewModelScope.launch {
        db.automationRuleDao().delete(rule.id)
    }

    fun fire(rule: AutomationRuleEntity) = viewModelScope.launch {
        ServiceLocator.automation.executeMatching(rule.trigger, rule.triggerValue)
    }
}
