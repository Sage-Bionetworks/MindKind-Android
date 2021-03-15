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

        /**
         * Enqueue daily work to upload to bridge
         * Ideally it's supposed to run at least once during 24 hours.
         * It's preference is when device is connected to wifi, charging, and when battery isn't low
         *
         * Work will be enqueued immediately and if this function has been called before,
         * It will keep the original work request, so we don't upload too frequently.
         *
         * @param context can be app, activity, or service
         */
        fun enqueueDailyWork(context: Context) {
            val constraint: Constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                    BridgeUploadWorker::class.java, 1, TimeUnit.DAYS)
                    .setConstraints(constraint)
                    .build()

            val workManager: WorkManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(periodicWorkName,
                    ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }

        /**
         * Call to cancel periodic work, like when the user withdraws from or is done with the study
         * @param context can be app, activity, or service
         */
        fun cancelDailyWork(context: Context) {
            val workManager: WorkManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(periodicWorkName)
        }
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