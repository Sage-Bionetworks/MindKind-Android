package org.sagebionetworks.research.mindkind.backgrounddata

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.work.*
import org.sagebionetworks.research.mindkind.BuildConfig
import java.util.concurrent.TimeUnit

public class DataUsageWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        public val LOG_TAG = DataUsageWorker::class.java.simpleName
        public val periodicWorkName = "DataUsageWorker"
    }

    override fun doWork(): Result {

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Data usage worker initiated")
        }

        val ctx = this.applicationContext

        // Request that background data service record mobile data usage
        val intent = Intent(ctx, BackgroundDataService::class.java)
        intent.action = BackgroundDataService.DATA_USAGE_RECEIVER_ACTION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }

        return Result.success()
    }
}