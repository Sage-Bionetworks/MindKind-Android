package org.sagebionetworks.research.mindkind.backgrounddata

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.sagebionetworks.research.mindkind.BuildConfig

public class BackgroundDataBootReceiver: BroadcastReceiver() {

    companion object {
        public val LOG_TAG = BackgroundDataBootReceiver::class.java.simpleName
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (BuildConfig.DEBUG) {
            Log.d(DataUsageWorker.LOG_TAG, "Boot receiver created")
        }

        val ctx = context ?: run { return }

        // Request that background data service record mobile data usage
        val intent = Intent(ctx, BackgroundDataService::class.java)
        intent.action = BackgroundDataService.DATA_USAGE_RECEIVER_ACTION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }
}