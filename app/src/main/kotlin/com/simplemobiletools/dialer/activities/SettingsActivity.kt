package com.simplemobiletools.dialer.activities

import android.annotation.TargetApi
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import com.simplemobiletools.commons.activities.ManageBlockedNumbersActivity
import com.simplemobiletools.commons.dialogs.ChangeDateTimeFormatDialog
import com.simplemobiletools.commons.dialogs.FeatureLockedDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.dialogs.ExportCallHistoryDialog
import com.simplemobiletools.dialer.dialogs.ManageVisibleTabsDialog
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.models.RecentCall
import kotlinx.android.synthetic.main.activity_main.dialpad_title
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {

    private val callHistoryFileType = "application/json"

    var defaultTitlePadding = -1

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            toast(R.string.importing)
            importCallHistory(uri)
        }
    }

    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(callHistoryFileType)) { uri ->
        if (uri != null) {
            toast(R.string.exporting)
            RecentsHelper(this).getRecentCalls(false, Int.MAX_VALUE) { recents ->
                exportCallHistory(recents, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateMaterialActivityViews(settings_coordinator, settings_holder, useTransparentNavigation = true, useTopSearchMenu = false)
//        setupMaterialScrollListener(settings_nested_scrollview, settings_toolbar)
        settings_nested_scrollview.background.applyColorFilter(getSecondaryBackgroundColor())
    }

    override fun onResume() {
        super.onResume()
//        setupToolbar(settings_toolbar, NavigationIcon.Arrow)
        top_toolbar.navigationIcon = resources.getColoredDrawableWithColor(R.drawable.ic_arrow_left_vector, getProperBackgroundColor().getContrastColor())
        top_toolbar.setNavigationOnClickListener {
            hideKeyboard()
            finish()
        }

        // Update actionbar height to 25% of screen height
        if (defaultTitlePadding < 0) {
            defaultTitlePadding = dialpad_title.paddingTop
        }
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels
        var height = if (displayWidth < displayHeight) {
            Math.max(displayHeight * 0.25f, 440f)
        } else {
            Math.max(displayHeight * 0.33f, 260f)
        }
        dialpad_title.layoutParams.height = height.roundToInt()
        // Make sure that text wont get cut off
        if (height < 440f) {
            val newPadding = Math.max(defaultTitlePadding - (440f - height), 0f)
            dialpad_title.setPadding(dialpad_title.paddingLeft, newPadding.roundToInt(), dialpad_title.paddingRight, dialpad_title.paddingBottom)
        }
//        val newFontSize = Math.min(Math.max(height - dialpad_title.paddingTop - dialpad_title.paddingBottom, 50f), defaultTitleFontSize)
//        dialpad_title.setTextSize(TypedValue.COMPLEX_UNIT_PX, newFontSize)


        setupPurchaseThankYou()
        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupManageBlockedNumbers()
        setupManageSpeedDial()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupManageShownTabs()
        setupDefaultTab()
        setupDialPadOpen()
        setupGroupSubsequentCalls()
        setupStartNameWithSurname()
        setupDialpadVibrations()
        setupDialpadNumbers()
        setupDialpadBeeps()
        setupShowCallConfirmation()
        setupDisableProximitySensor()
        setupDisableSwipeToAnswer()
        setupAlwaysShowFullscreen()
        setupCallsExport()
        setupCallsImport()
        updateTextColors(settings_holder)

        arrayOf(
            settings_color_customization_section_label,
            settings_general_settings_label,
            settings_startup_label,
            settings_calls_label,
            settings_migration_section_label
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_color_customization_label.text = getCustomizeColorsString()
        settings_color_customization_holder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())
        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() {
        settings_manage_blocked_numbers_label.text = addLockedLabelIfNeeded(R.string.manage_blocked_numbers)
        settings_manage_blocked_numbers_holder.beVisibleIf(isNougatPlus())
        settings_manage_blocked_numbers_holder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                Intent(this, ManageBlockedNumbersActivity::class.java).apply {
                    startActivity(this)
                }
            } else {
                FeatureLockedDialog(this) { }
            }
        }
    }

    private fun setupManageSpeedDial() {
        settings_manage_speed_dial_holder.setOnClickListener {
            Intent(this, ManageSpeedDialActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupChangeDateTimeFormat() {
        settings_change_date_time_format_holder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupFontSize() {
        settings_font_size.text = getFontSizeText()
        settings_font_size_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settings_font_size.text = getFontSizeText()
            }
        }
    }

    private fun setupManageShownTabs() {
        settings_manage_tabs_holder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupDefaultTab() {
        settings_default_tab.text = getDefaultTabText()
        settings_default_tab_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_CONTACTS, getString(R.string.contacts_tab)),
                RadioItem(TAB_FAVORITES, getString(R.string.favorites_tab)),
                RadioItem(TAB_CALL_HISTORY, getString(R.string.call_history_tab)),
                RadioItem(TAB_LAST_USED, getString(R.string.last_used_tab))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
                config.defaultTab = it as Int
                settings_default_tab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (baseConfig.defaultTab) {
            TAB_CONTACTS -> R.string.contacts_tab
            TAB_FAVORITES -> R.string.favorites_tab
            TAB_CALL_HISTORY -> R.string.call_history_tab
            else -> R.string.last_used_tab
        }
    )

    private fun setupDialPadOpen() {
        settings_open_dialpad_at_launch.isChecked = config.openDialPadAtLaunch
        settings_open_dialpad_at_launch_holder.setOnClickListener {
            settings_open_dialpad_at_launch.toggle()
            config.openDialPadAtLaunch = settings_open_dialpad_at_launch.isChecked
        }
    }

    private fun setupGroupSubsequentCalls() {
        settings_group_subsequent_calls.isChecked = config.groupSubsequentCalls
        settings_group_subsequent_calls_holder.setOnClickListener {
            settings_group_subsequent_calls.toggle()
            config.groupSubsequentCalls = settings_group_subsequent_calls.isChecked
        }
    }

    private fun setupStartNameWithSurname() {
        settings_start_name_with_surname.isChecked = config.startNameWithSurname
        settings_start_name_with_surname_holder.setOnClickListener {
            settings_start_name_with_surname.toggle()
            config.startNameWithSurname = settings_start_name_with_surname.isChecked
        }
    }

    private fun setupDialpadVibrations() {
        settings_dialpad_vibration.isChecked = config.dialpadVibration
        settings_dialpad_vibration_holder.setOnClickListener {
            settings_dialpad_vibration.toggle()
            config.dialpadVibration = settings_dialpad_vibration.isChecked
        }
    }

    private fun setupDialpadNumbers() {
        settings_hide_dialpad_numbers.isChecked = config.hideDialpadNumbers
        settings_hide_dialpad_numbers_holder.setOnClickListener {
            settings_hide_dialpad_numbers.toggle()
            config.hideDialpadNumbers = settings_hide_dialpad_numbers.isChecked
        }
    }

    private fun setupDialpadBeeps() {
        settings_dialpad_beeps.isChecked = config.dialpadBeeps
        settings_dialpad_beeps_holder.setOnClickListener {
            settings_dialpad_beeps.toggle()
            config.dialpadBeeps = settings_dialpad_beeps.isChecked
        }
    }

    private fun setupShowCallConfirmation() {
        settings_show_call_confirmation.isChecked = config.showCallConfirmation
        settings_show_call_confirmation_holder.setOnClickListener {
            settings_show_call_confirmation.toggle()
            config.showCallConfirmation = settings_show_call_confirmation.isChecked
        }
    }

    private fun setupDisableProximitySensor() {
        settings_disable_proximity_sensor.isChecked = config.disableProximitySensor
        settings_disable_proximity_sensor_holder.setOnClickListener {
            settings_disable_proximity_sensor.toggle()
            config.disableProximitySensor = settings_disable_proximity_sensor.isChecked
        }
    }

    private fun setupDisableSwipeToAnswer() {
        settings_disable_swipe_to_answer.isChecked = config.disableSwipeToAnswer
        settings_disable_swipe_to_answer_holder.setOnClickListener {
            settings_disable_swipe_to_answer.toggle()
            config.disableSwipeToAnswer = settings_disable_swipe_to_answer.isChecked
        }
    }

    private fun setupAlwaysShowFullscreen() {
        settings_always_show_fullscreen.isChecked = config.alwaysShowFullscreen
        settings_always_show_fullscreen_holder.setOnClickListener {
            settings_always_show_fullscreen.toggle()
            config.alwaysShowFullscreen = settings_always_show_fullscreen.isChecked
        }
    }

    private fun setupCallsExport() {
        settings_export_calls_holder.setOnClickListener {
            ExportCallHistoryDialog(this) { filename ->
                saveDocument.launch(filename)
            }
        }
    }

    private fun setupCallsImport() {
        settings_import_calls_holder.setOnClickListener {
            getContent.launch(callHistoryFileType)
        }
    }

    private fun importCallHistory(uri: Uri) {
        try {
            val jsonString = contentResolver.openInputStream(uri)!!.use { inputStream ->
                inputStream.bufferedReader().readText()
            }

            val objects = Json.decodeFromString<List<RecentCall>>(jsonString)

            if (objects.isEmpty()) {
                toast(R.string.no_entries_for_importing)
                return
            }

            RecentsHelper(this).restoreRecentCalls(this, objects) {
                toast(R.string.importing_successful)
            }
        } catch (_: SerializationException) {
            toast(R.string.invalid_file_format)
        } catch (_: IllegalArgumentException) {
            toast(R.string.invalid_file_format)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun exportCallHistory(recents: List<RecentCall>, uri: Uri) {
        if (recents.isEmpty()) {
            toast(R.string.no_entries_for_exporting)
        } else {
            try {
                val outputStream = contentResolver.openOutputStream(uri)!!

                val jsonString = Json.encodeToString(recents)
                outputStream.use {
                    it.write(jsonString.toByteArray())
                }
                toast(R.string.exporting_successful)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}
