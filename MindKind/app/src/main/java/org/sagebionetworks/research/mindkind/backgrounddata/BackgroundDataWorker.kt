package org.sagebionetworks.research.mindkind.backgrounddata

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.sagebionetworks.research.mindkind.BuildConfig

public class DataUsageWorker(ctx: Context, params: WorkerParameters) : BackgroundDataWorker(ctx, params) {
    override val periodicWorkName: String = "DataUsageWorker"
    override val intentActionName: String = BackgroundDataService.DATA_USAGE_RECEIVER_ACTION
}

abstract class BackgroundDataWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        public val LOG_TAG = BackgroundDataWorker::class.java.simpleName
    }

    abstract val periodicWorkName: String
    abstract val intentActionName: String

    override fun doWork(): Result {

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Background data worker invoked $periodicWorkName")
        }

        val ctx = this.applicationContext

        // Request that background data service record mobile data usage
        val intent = Intent(ctx, BackgroundDataService::class.java)
        intent.action = intentActionName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }

        return Result.success()
    }
}