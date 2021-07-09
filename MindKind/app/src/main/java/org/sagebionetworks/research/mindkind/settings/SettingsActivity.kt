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

    var settingsTitle: String? = null

    val isSettingsMain: Boolean
        get() = (settingsTitle == "Settings")

    val isDataTracking: Boolean
        get() = (settingsTitle == "Data Settings")

    val versionStr = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            .replace("-debug", "")
            .replace("-release", "")

    var settingsItems = mutableListOf(
            SettingsItem(LayoutType.HEADER_WITH_BACKGROUND, "About MindKind App", versionStr, true, false),
            SettingsItem(LayoutType.ITEM,"About our team", null, false, false),
            SettingsItem(LayoutType.ITEM,"FAQ", null, false, false),
            SettingsItem(LayoutType.ITEM,"Feedback for Us", null, false, false),
            SettingsItem(LayoutType.ITEM,"Privacy Policy", null, false, false),
            SettingsItem(LayoutType.ITEM,"Study Information Sheet", null, false, false),
            SettingsItem(LayoutType.ITEM,"Licenses & Copyright", null, false, false),
            SettingsItem(LayoutType.ITEM,"Options", null, false, true),
            SettingsItem(LayoutType.ITEM,"Withdraw From Study", "Currently enrolled", false, false),
            SettingsItem(LayoutType.ITEM, "Background Data Collection", "What data are we collecting?", false, false),
            SettingsItem(LayoutType.ITEM, "Delete My Data", "Request to have your data deleted", false, false))

    var backgroundItems = mutableListOf(
            SettingsItem(LayoutType.HEADER, "Your Data Settings", "This is the data we record. You can turn the recorders on or off now. You can make changes anytime during the study", true, false),
            SettingsItem(LayoutType.ITEM, "Allow all", null, false, false, "", true),
            SettingsItem(LayoutType.ITEM, "Room Brightness", "Measure every 15 minutes to see if the light around you is becoming brighter or darker.", false, false, SageTaskIdentifier.AmbientLight, true),
            SettingsItem(LayoutType.ITEM, "Screen Time", "How long you have your phone screen locked. We don't record what you are doing when your phone is unlocked.", false, false, SageTaskIdentifier.ScreenTime, true),
            SettingsItem(LayoutType.ITEM,"Charging Time", "How long your phone is charging the battery.", false, false, SageTaskIdentifier.ChargingTime, true),
            SettingsItem(LayoutType.ITEM,"Battery Statistics", "What percent of battery you have left on your phone.", false, false, SageTaskIdentifier.BatteryStatistics, true),
            SettingsItem(LayoutType.ITEM, "Data Usage", "How much wifi and cellular data you use. We don't record where or how you use your data.", false, false, SageTaskIdentifier.DataUsage, true))

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        sharedPrefs = BackgroundDataService.createSharedPrefs(this)

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
                    "Background Data Collection" -> goToBackgroundDataCollection()
                    "Withdraw From Study" -> goToWithdrawal()
                    "Delete My Data" -> showDeleteMyDataContactUs()
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
            // Show email intent
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:") // only email apps should handle this
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("MindKindSupport@sagebionetworks.org"))
            intent.putExtra(Intent.EXTRA_SUBJECT, "Delete My Data")
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "MindKindSupport@sagebionetworks.org " +
                        "cannot send email", Toast.LENGTH_LONG)
            }
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
