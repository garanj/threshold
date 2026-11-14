package com.garan.threshold.ui.settings

import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.NfcTagEntry

/**
 * Deterministic `Modifier.testTag(...)` identifiers for Profiles, Triggers, and editor sheets.
 */
object ProfilesTestTags {
    const val TAB_PROFILES = "profiles_tab_profiles"
    const val TAB_TRIGGERS = "profiles_tab_triggers"
    const val TOP_ADD_BUTTON = "profiles_top_add_button"
    const val ADD_PROFILE_BUTTON = "profiles_add_profile_button"
    const val ADD_TRIGGER_BUTTON = "profiles_add_trigger_button"

    const val PROFILE_EDITOR_NAME_INPUT = "profile_editor_name_input"
    const val PROFILE_EDITOR_ADD_FIRST_TRIGGER = "profile_editor_add_first_trigger"
    const val PROFILE_EDITOR_ADD_OR_RULE = "profile_editor_add_or_rule"
    const val PROFILE_EDITOR_SAVE_BUTTON = "profile_editor_save_button"
    const val PROFILE_EDITOR_CANCEL_BUTTON = "profile_editor_cancel_button"

    const val TRIGGER_PICKER_CREATE_NEW = "trigger_picker_create_new"
    const val TRIGGER_EDITOR_NAME_INPUT = "trigger_editor_name_input"
    const val TRIGGER_EDITOR_TYPE_SCHEDULE = "trigger_editor_type_schedule"
    const val TRIGGER_EDITOR_TYPE_GEO = "trigger_editor_type_geo"
    const val TRIGGER_EDITOR_TYPE_NFC = "trigger_editor_type_nfc"
    const val TRIGGER_EDITOR_NFC_MODE_TOGGLE = "trigger_editor_nfc_mode_toggle"
    const val TRIGGER_EDITOR_NFC_MODE_TIMED = "trigger_editor_nfc_mode_timed"
    const val TRIGGER_EDITOR_SAVE_BUTTON = "trigger_editor_save_button"
    const val TRIGGER_EDITOR_CANCEL_BUTTON = "trigger_editor_cancel_button"

    const val DELETE_CONFIRM_BUTTON = "profiles_delete_confirm_button"
    const val DELETE_CANCEL_BUTTON = "profiles_delete_cancel_button"

    fun profileCard(id: String): String = "profile_card_$id"
    fun profileToggle(id: String): String = "profile_toggle_$id"
    fun profileMoveUp(id: String): String = "profile_move_up_$id"
    fun profileMoveDown(id: String): String = "profile_move_down_$id"
    fun profileEditRules(id: String): String = "profile_edit_rules_$id"
    fun profileConfigureApps(id: String): String = "profile_configure_apps_$id"
    fun profileDelete(id: String): String = "profile_delete_$id"

    fun triggerCard(id: String): String = "trigger_card_$id"
    fun triggerDelete(id: String): String = "trigger_delete_$id"

    fun profileRuleCard(ruleIndex: Int): String = "profile_rule_card_$ruleIndex"
    fun profileRuleAddAnd(ruleIndex: Int): String = "profile_rule_${ruleIndex}_add_and"
    fun profileRuleTriggerRow(ruleIndex: Int, triggerId: String): String =
        "profile_rule_${ruleIndex}_trigger_$triggerId"
    fun profileRuleRemoveTrigger(ruleIndex: Int, triggerId: String): String =
        "profile_rule_${ruleIndex}_remove_$triggerId"

    fun triggerPickerItem(triggerId: String): String = "trigger_picker_item_$triggerId"

    fun triggerEditorScheduleDay(day: DayOfWeek): String = "trigger_editor_schedule_day_${day.name}"
    fun triggerEditorNfcDuration(minutes: Int): String = "trigger_editor_nfc_duration_$minutes"
    fun triggerEditorNfcTagLabel(uid: String): String = "trigger_editor_nfc_tag_label_${NfcTagEntry.normalizeUid(uid)}"
    fun triggerEditorNfcTagRemove(uid: String): String =
        "trigger_editor_nfc_tag_remove_${NfcTagEntry.normalizeUid(uid)}"
}
