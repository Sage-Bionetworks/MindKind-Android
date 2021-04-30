package org.sagebionetworks.research.mindkind

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import dagger.android.AndroidInjection
import org.sagebionetworks.research.mindkind.R

open class RegistrationActivity: AppCompatActivity() {

    companion object {
        fun logInfo(msg: String) {
            Log.i(RegistrationActivity::class.simpleName, msg)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_registration)

        // TODO: enable continue button after valid phone entered

    }

}
