package org.sagebionetworks.research.mindkind.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_settings.*
import org.sagebionetworks.research.mindkind.R

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

    var settingsItems = mutableListOf(
            SettingsItem("About MindKind App", "Version 1.0", true, false),
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
            SettingsItem("Ambient Light", "On", false, false),
            SettingsItem("Step Count", "On", false, false),
            SettingsItem("Activity Monitor", "On", false, false),
            SettingsItem("Screen Time", "On", false, false),
            SettingsItem("Screen Lock / Unlock", "On", false, false),
            SettingsItem("Battery Drain", "Off", false, false),
            SettingsItem("Data Usage", "On", false, false),
            SettingsItem("Instagram", "On", false, false),
            SettingsItem("Facebook", "Off", false, false))

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

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
        val title = intent.extras?.getString(extraSettingsId)
        settings_title.text = title
        if(title == "Settings") {
            items = settingsItems
        } else {
            items = backgroundItems
        }

        val activity = this
        val adapter = SettingsAdapter(items, object: SettingsAdapterListener {
            override fun onItemClicked(label: String?) {
                logInfo("Item clicked $label")
                if(label == "Background Data Collection") {
                    val intent = Intent(activity, SettingsActivity::class.java)
                    intent.putExtra(extraSettingsId, "Data Settings")
                    startActivity(intent)
                } else {
                    Toast.makeText(activity, "Not implemented yet.", Toast.LENGTH_LONG).show()
                }
            }
        })
        settingsRecycler.adapter = adapter

    }

}
