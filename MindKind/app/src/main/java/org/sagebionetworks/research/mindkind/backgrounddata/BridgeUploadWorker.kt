package org.sagebionetworks.research.mindkind.backgrounddata

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.work.*
import org.sagebionetworks.research.mindkind.BuildConfig
import java.util.concurrent.TimeUnit

public class BridgeUploadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        public val LOG_TAG = BridgeUploadWorker::class.java.simpleName
        public val periodicWorkName = "PeriodicBridgeUploader"
    }

    override fun doWork(): Result {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Requesting bridge upload from Worker")
        }

        val ctx = this.applicationContext
        // Request upload from bridge background data service
        val intent = Intent(ctx, BackgroundDataService::class.java)
        intent.action = BackgroundDataService.WIFI_CHARGING_UPLOAD_DATA_ACTION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }

        return Result.success()
    }
}