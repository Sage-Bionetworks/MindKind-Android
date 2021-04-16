package org.sagebionetworks.research.mindkind.backgrounddata

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.sagebionetworks.research.mindkind.BuildConfig

class AmbientLightWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        public val LOG_TAG = AmbientLightWorker::class.java.simpleName
        public val periodicWorkName = "AmbientLightWorker"
    }

    override fun doWork(): Result {

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Ambient light worker initiated")
        }

        val ctx = this.applicationContext

        // Request that background data service record mobile data usage
        val intent = Intent(ctx, BackgroundDataService::class.java)
        intent.action = BackgroundDataService.AMBIENT_LIGHT_WORKER_ACTION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }

        return Result.success()
    }
}