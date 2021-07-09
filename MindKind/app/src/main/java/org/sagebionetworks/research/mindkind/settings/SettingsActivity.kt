package org.sagebionetworks.research.mindkind.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_settings.*
import org.sagebionetworks.research.mindkind.BuildConfig
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.R2.id.email
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.conversation.ConfirmationDialog
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier


open class SettingsActivity: AppCompatActivity() {

    companion object {
        const val extraSettingsId = "EXTRA_SETTINGS_PAGE"

        fun logInfo(msg: String) {
            Log.i(SettingsActivity::class.java.canonicalName, msg)
        }

        fun start(baseCtx: Context, settingsPage: String) {
            val intent = Intent(baseCtx, SettingsActivity::class.java)
            intent.putExtra(SettingsActivity.extraSettingsId, settingsPage)
            baseCtx.startActivity(intent)
        }
    }

    lateinit var sharedPrefs: SharedPreferences
    lateinit var settingsItems: MutableList<SettingsItem>
    lateinit var backgroundItems: MutableList<SettingsItem>

    var settingsTitle: String? = null

    val isSettingsMain: Boolean
        get() = (settingsTitle == "Settings")

    val isDataTracking: Boolean
        get() = (settingsTitle == "Data Settings")

    val versionStr = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            .replace("-debug", "")
            .replace("-release", "")

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        sharedPrefs = BackgroundDataService.createSharedPrefs(this)
        settingsItems = mutableListOf(
                SettingsItem(getString(R.string.settings_about_app_title), versionStr, true, false),
                SettingsItem(getString(R.string.settings_mental_health_resources_title), null, false, false),
                SettingsItem(getString(R.string.settings_about_study_title), null, false, false),
                SettingsItem(getString(R.string.settings_about_team_title), null, false, false),
                SettingsItem(getString(R.string.settings_informed_consent_title), null, false, false),
                SettingsItem(getString(R.string.settings_privacy_policy_title), null, false, false),
                SettingsItem(getString(R.string.settings_terms_of_service_title), null, false, false),
                SettingsItem(getString(R.string.settings_contact_us_title), null, false, false),
                SettingsItem(getString(R.string.settings_licenses), null, false, false),
                SettingsItem(getString(R.string.settings_options_title), null, false, true),
                SettingsItem(getString(R.string.settings_withdraw_title), getString(R.string.settings_withdraw_detail), false, false),
                SettingsItem(getString(R.string.settings_background_collection_title), getString(R.string.settings_background_collection_detail), false, false),
                SettingsItem(getString(R.string.settings_delete_data_title), getString(R.string.settings_delete_data_detail), false, false))
        backgroundItems = mutableListOf(
                SettingsItem(getString(R.string.settings_room_brightness_title), getString(R.string.settings_room_brightness_detail), false, false, SageTaskIdentifier.AmbientLight, true),
                SettingsItem(getString(R.string.settings_screen_time_title), getString(R.string.settings_screen_time_detail), false, false, SageTaskIdentifier.ScreenTime, true),
                SettingsItem(getString(R.string.settings_charging_time_title), getString(R.string.settings_charging_time_detail), false, false, SageTaskIdentifier.ChargingTime, true),
                SettingsItem(getString(R.string.settings_battery_stats_title), getString(R.string.settings_battery_stats_detail), false, false, SageTaskIdentifier.BatteryStatistics, true),
                SettingsItem(getString(R.string.settings_data_usage_title), getString(R.string.settings_data_usage_detail), false, false, SageTaskIdentifier.DataUsage, true))

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

        var items: List<SettingsItem>? = null
        settingsTitle = intent.extras?.getString(extraSettingsId)
        settings_title.text = settingsTitle

        if(isSettingsMain) {
            items = settingsItems
        } else {
            items = backgroundItems
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
                    getString(R.string.settings_contact_us_title) -> sendEmail(getString(R.string.settings_email_contact_us),
                                                                        getString(R.string.settings_email_contact_us_subject))
                    else -> processDataTracking(item)
                }
            }
        })
        settingsRecycler.adapter = adapter

        if (isDataTracking) {
            adapter.updateDataTrackingItems(sharedPrefs)
        }
    }

    override fun onPause() {
        super.onPause()

        if (isDataTracking) {
            BackgroundDataService.notifySettingsChanged(this)
        }
    }

    fun goToBackgroundDataCollection() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(extraSettingsId, "Data Settings")
        startActivity(intent)
    }

    fun goToWithdrawal() {
        startActivity(Intent(this, WithdrawalActivity::class.java))
    }

    fun showDeleteMyDataContactUs() {
        showContactUsDialog()
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
        val itemUnwrapped = item ?: run { return }
        if (backgroundItems.map { it.identifier }.contains(itemUnwrapped.identifier)) {
            saveDataTrackingPermission(itemUnwrapped.identifier, itemUnwrapped.active)
        } else {
            Toast.makeText(this, "Not implemented yet.", Toast.LENGTH_LONG).show()
        }
    }

    fun showDataTrackingDialog(item: SettingsItem) {
        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_data_tracking)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        val title = dialog.findViewById<TextView>(R.id.data_tracking_title)
        title?.text = item.label

        val toggleButton = dialog.findViewById<ToggleButton>(R.id.toggle_button)
        toggleButton?.textOn = "ON"
        toggleButton?.textOff = "OFF"
        toggleButton?.isChecked = item.subtext == "On"

        dialog.findViewById<View>(R.id.cancel_button)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.save_button)?.setOnClickListener {
            saveDataTrackingPermission(item.identifier, toggleButton?.isChecked ?: false)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showContactUsDialog() {
        val fm = supportFragmentManager

        val dialog = ConfirmationDialog.newInstance(
                getString(R.string.settings_delete_your_data),
                getString(R.string.settings_delete_your_data_desc),
                getString(R.string.cancel),
                getString(R.string.settings_contact_us))

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

    fun saveDataTrackingPermission(identifier: String, newIsOn: Boolean) {
        BackgroundDataService.setDataAllowedToBeTracked(sharedPrefs, identifier, newIsOn)
        (settingsRecycler.adapter as? SettingsAdapter)?.updateDataTrackingItems(sharedPrefs)
    }
}
