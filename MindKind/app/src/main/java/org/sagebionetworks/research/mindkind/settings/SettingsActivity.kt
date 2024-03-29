package org.sagebionetworks.research.mindkind.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_conversation_survey.*
import kotlinx.android.synthetic.main.activity_settings.*
import org.sagebionetworks.research.mindkind.BuildConfig
import org.sagebionetworks.research.mindkind.EntryActivity
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.conversation.ConfirmationDialog
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.returnToEntryActivity
import org.sagebionetworks.researchstack.backbone.ui.ViewWebDocumentActivity


open class SettingsActivity: AppCompatActivity() {

    companion object {
        const val extraSettingsId = "EXTRA_SETTINGS_PAGE"
        const val extraSettingsInitialId = "EXTRA_SETTINGS_INITIAL"
        const val allowAllIdentifier = "AllowAll"
        const val dataSettingsTitle = "Data Settings"

        fun logInfo(msg: String) {
            Log.i(SettingsActivity::class.java.canonicalName, msg)
        }

        fun startDataSettings(baseCtx: Context) {
            val intent = Intent(baseCtx, SettingsActivity::class.java)
            intent.putExtra(SettingsActivity.extraSettingsId, dataSettingsTitle)
            baseCtx.startActivity(intent)
        }

        fun startInitialDataTracking(baseCtx: Context) {
            val intent = Intent(baseCtx, SettingsActivity::class.java)
            intent.putExtra(SettingsActivity.extraSettingsId, dataSettingsTitle)
            intent.putExtra(SettingsActivity.extraSettingsInitialId, dataSettingsTitle)
            baseCtx.startActivity(intent)
        }
    }

    lateinit var sharedPrefs: SharedPreferences

    var handler: Handler? = null

    var settingsTitle: String? = null

    val isSettingsMain: Boolean
        get() = (settingsTitle == "Settings")

    val isDataTracking: Boolean
        get() = (settingsTitle == "Data Settings")

    val versionStr = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            .replace("-debug", "")
            .replace("-release", "")

    fun backgroundItems(): List<SettingsItem> {
        return (settingsRecycler.adapter as? SettingsAdapter)?.dataSet ?: listOf()
    }

    fun settingsAdapter(): SettingsAdapter? {
        return settingsRecycler.adapter as? SettingsAdapter
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        handler = Handler()

        sharedPrefs = BackgroundDataService.createSharedPrefs(this)
        val settingsItems = mutableListOf(
                SettingsItem(LayoutType.HEADER_WITH_BACKGROUND, getString(R.string.settings_about_app_title),
                        versionStr, true, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_mental_health_resources_title),
                        null, false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_about_study_title),
                        null, false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_about_team_title),
                        null, false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_informed_consent_title),
                        null, false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_privacy_policy_title),
                        null, false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_terms_of_service_title),
                        null, false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_contact_us_title),
                        getString(R.string.settings_email_contact_us),
                        false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_licenses),
                        null, false, false),
                SettingsItem(LayoutType.HEADER, getString(R.string.settings_options_title),
                        null, false, true),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_withdraw_title),
                        getString(R.string.settings_withdraw_detail),
                        false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_background_collection_title),
                        getString(R.string.settings_background_collection_detail),
                        false, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_delete_data_title),
                        getString(R.string.settings_delete_data_detail),
                        false, false))

        val backgroundItems = mutableListOf(
                SettingsItem(LayoutType.HEADER,
                        getString(R.string.settings_recorders_title),
                        getString(R.string.settings_recorders_detail),
                        true, false),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_allow_all),
                        null, false, false, allowAllIdentifier, true),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_room_brightness_title),
                        getString(R.string.settings_room_brightness_detail), false,
                        false, SageTaskIdentifier.AmbientLight, true),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_screen_time_title),
                        getString(R.string.settings_screen_time_detail), false,
                        false, SageTaskIdentifier.ScreenTime, true),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_charging_time_title),
                        getString(R.string.settings_charging_time_detail), false,
                        false, SageTaskIdentifier.ChargingTime, true),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_battery_stats_title),
                        getString(R.string.settings_battery_stats_detail), false,
                        false, SageTaskIdentifier.BatteryStatistics, true),
                SettingsItem(LayoutType.ITEM, getString(R.string.settings_data_usage_title),
                        getString(R.string.settings_data_usage_detail), false,
                        false, SageTaskIdentifier.DataUsage, true))

        val footerItem = SettingsItem(LayoutType.FOOTER, "", "", false,
                false, SageTaskIdentifier.DataUsage, true, false,
                        getString(R.string.settings_continue))

        back_button.setOnClickListener {
            finish()
        }

        val llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        settingsRecycler.layoutManager = llm

        val itemDecorator = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        ResourcesCompat.getDrawable(resources, R.drawable.settings_divider, null)?.let {
            itemDecorator.setDrawable(it)
        }
        settingsRecycler.addItemDecoration(itemDecorator)

        settingsTitle = intent.extras?.getString(extraSettingsId)
        settings_title.text = settingsTitle

        val items = if(isSettingsMain) {
            settingsItems
        } else if (null != intent.extras?.getString(extraSettingsInitialId)) {
            backgroundItems + listOf(footerItem)
        } else {
            backgroundItems
        }

        val adapter = SettingsAdapter(items, object : SettingsAdapterListener {
            override fun onItemClicked(item: SettingsItem?) {
                logInfo("Item clicked $item.label")
                when (item?.identifier) {
                    getString(R.string.settings_background_collection_title) -> goToBackgroundDataCollection()
                    getString(R.string.settings_withdraw_title) -> goToWithdrawal()
                    getString(R.string.settings_delete_data_title) -> showDeleteMyDataContactUs()
                    getString(R.string.settings_mental_health_resources_title) -> goToWebpage(getString(R.string.settings_url_mental_health))
                    getString(R.string.settings_about_study_title) -> goToWebpage(getString(R.string.settings_url_about_study))
                    getString(R.string.settings_about_team_title) -> goToWebpage(getString(R.string.settings_url_about_team))
                    getString(R.string.settings_informed_consent_title) -> goToWebpage(getString(R.string.settings_url_informed_consent))
                    getString(R.string.settings_privacy_policy_title) -> goToWebpage(getString(R.string.settings_url_privacy_policy))
                    getString(R.string.settings_terms_of_service_title) -> goToWebpage(getString(R.string.settings_url_terms_of_service))
                    getString(R.string.settings_licenses) -> goToLicense()
                    getString(R.string.settings_contact_us_title) -> sendEmail(getString(R.string.settings_email_contact_us),
                                                                        getString(R.string.settings_email_contact_us_subject))
                    else -> processDataTracking(item)
                }
            }

            // Needs to be done immediately for entry activity navigation
            @SuppressLint("ApplySharedPref")
            override fun onFooterClicked(item: SettingsItem?) {
                sharedPrefs.edit().putBoolean(EntryActivity.prefsOnboardingKey, true).commit()
                // Currently this is only used after welcome screen, so now go to the home screen
                returnToEntryActivity()
            }
        })
        settingsRecycler.adapter = adapter

        if (isDataTracking) {
            refreshBackgroundItems()
        }
    }

    override fun onPause() {
        super.onPause()

        if (isDataTracking) {
            BackgroundDataService.notifySettingsChanged(this)
        }
    }

    fun safeToggleChange(operation: (() -> Unit)) {
        settingsAdapter()?.isListenerPaused = true
        operation.invoke()
        settingsAdapter()?.isListenerPaused = false
    }

    fun goToBackgroundDataCollection() {
        SettingsActivity.startDataSettings(this)
    }

    fun goToWithdrawal() {
        startActivity(Intent(this, WithdrawalActivity::class.java))
    }

    fun showDeleteMyDataContactUs() {
        showContactUsDialog()
    }

    fun goToLicense() {
        val title = getString(R.string.settings_licenses)
        startActivity(ViewWebDocumentActivity.newIntentForPath(this,
                title, "file:///android_asset/html/Licenses.html"))
    }

    fun goToWebpage(uriString: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
        startActivity(browserIntent)
    }

    fun sendEmail(emailAddress: String, subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:") // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, emailAddress +
                    " cannot send email", Toast.LENGTH_LONG)
        }
    }

    fun processDataTracking(item: SettingsItem?) {
        // Bug fix for RecyclerView needing time to animate the Toggle button
        handler?.postDelayed({
            val itemUnwrapped = item ?: run { return@postDelayed }
            if (itemUnwrapped.identifier == allowAllIdentifier) {
                toggleAllowBackgroundItems(itemUnwrapped.active)
            } else if (backgroundItems().map { it.identifier }.contains(itemUnwrapped.identifier)) {
                saveDataTrackingPermission(itemUnwrapped.identifier, itemUnwrapped.active)
                safeToggleChange {
                    allowAllItem()?.active = isBackgroundDataAllActive()
                }
                settingsRecycler.adapter?.notifyDataSetChanged()
            } else {
                Toast.makeText(this, "Not implemented yet.", Toast.LENGTH_LONG).show()
            }
        }, 100)
    }

    private fun showContactUsDialog() {
        val fm = supportFragmentManager

        val dialog = ConfirmationDialog.newContactUsInstance(
                getString(R.string.settings_delete_your_data),
                getString(R.string.settings_delete_your_data_desc),
                getString(R.string.cancel),
                getString(R.string.settings_contact_us),
                getString(R.string.settings_delete_your_data_message_desc),
                getString(R.string.settings_email_contact_us))

        dialog.show(fm, ConfirmationDialog.TAG)

        dialog.setActionListener {
            // Send email to delete data
            sendEmail("MindKindSupport@sagebionetworks.org", "Delete My Data")
        }

        dialog.skipButtonListener = View.OnClickListener {
            dialog.dismiss()
        }

        dialog.cancelListener = View.OnClickListener {
            dialog.dismiss()
        }
    }

    private fun toggleableItems(): List<SettingsItem> {
        val allowAllIdx = backgroundItems().map {it.identifier}.indexOf(allowAllIdentifier) + 1
        val size = backgroundItems().size
        return backgroundItems().subList(allowAllIdx, size)
                .filter { it.type != LayoutType.FOOTER }
    }

    private fun isBackgroundDataAllActive(): Boolean {
        return toggleableItems().none { !it.active }
    }

    private fun allowAllItem(): SettingsItem? {
        return backgroundItems().firstOrNull { it.identifier == allowAllIdentifier }
    }

    private fun toggleAllowBackgroundItems(newActiveState: Boolean) {
        safeToggleChange {
            backgroundItems().forEach {
                if (it.identifier != allowAllIdentifier) {
                    saveDataTrackingPermission(it.identifier, newActiveState)
                    it.active = newActiveState
                }
            }
        }
        settingsRecycler.adapter?.notifyDataSetChanged()
    }

    fun refreshBackgroundItems() {
        val items = BackgroundDataService.loadDataAllowedToBeTracked(sharedPrefs)
        safeToggleChange {
            backgroundItems().forEach {
                if (it.identifier != allowAllIdentifier) {
                    it.active = items.contains(it.identifier)
                }
            }
            allowAllItem()?.active = isBackgroundDataAllActive()
        }
        settingsAdapter()?.notifyDataSetChanged()
    }

    fun saveDataTrackingPermission(identifier: String, newIsOn: Boolean) {
        BackgroundDataService.setDataAllowedToBeTracked(sharedPrefs, identifier, newIsOn)
    }
}
