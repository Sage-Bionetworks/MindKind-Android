package org.sagebionetworks.research.mindkind.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
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
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier

open class SettingsActivity: AppCompatActivity() {

    companion object {
        const val extraSettingsId = "EXTRA_SETTINGS_PAGE"

        fun logInfo(msg: String) {
            Log.i(SettingsActivity::class.simpleName, msg)
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
            SettingsItem("About MindKind App", versionStr, true, false),
            SettingsItem("About our team", null, false, false),
            SettingsItem("FAQ", null, false, false),
            SettingsItem("Feedback for Us", null, false, false),
            SettingsItem("Privacy Policy", null, false, false),
            SettingsItem("Study Information Sheet", null, false, false),
            SettingsItem("Licenses & Copyright", null, false, false),
            SettingsItem("Options", null, false, true),
            SettingsItem("Withdraw From Study", "Currently enrolled", false, false),
            SettingsItem("Background Data Collection", "What data are we collecting?", false, false))

    var backgroundItems = mutableListOf(
            SettingsItem("Ambient Light", "Off", false, false, SageTaskIdentifier.AmbientLight),
            SettingsItem("Screen Time", "Off", false, false, SageTaskIdentifier.ScreenTime),
            SettingsItem("Charging Time", "Off", false, false, SageTaskIdentifier.ChargingTime),
            SettingsItem("Battery Statistics", "Off", false, false, SageTaskIdentifier.BatteryStatistics),
            SettingsItem("Data Usage", "Off", false, false, SageTaskIdentifier.DataUsage))

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

        val adapter = SettingsAdapter(items, object: SettingsAdapterListener {
            override fun onItemClicked(item: SettingsItem?) {
                logInfo("Item clicked $item.label")
                when(item?.identifier) {
                    "Background Data Collection" -> goToBackgroundDataCollection()
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

    fun processDataTracking(item: SettingsItem?) {
        val itemUnwrapped = item ?: run { return }
        if (backgroundItems.map { it.identifier }.contains(itemUnwrapped.identifier)) {
            showDataTrackingDialog(itemUnwrapped)
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

    fun saveDataTrackingPermission(identifier: String, newIsOn: Boolean) {
        BackgroundDataService.setDataAllowedToBeTracked(sharedPrefs, identifier, newIsOn)
        (settingsRecycler.adapter as? SettingsAdapter)?.updateDataTrackingItems(sharedPrefs)
    }
}
