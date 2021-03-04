package org.sagebionetworks.research.mindkind.util

import android.content.Context
import androidx.work.*
import org.sagebionetworks.research.mindkind.work.BridgeUploadWorker
import java.util.concurrent.TimeUnit

class WorkUtils {

    fun createOneTimeWorkerImmediately(context: Context) {
        val constraint: Constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                BridgeUploadWorker::class.java, 1, TimeUnit.DAYS)
                .setConstraints(constraint)
                .build()

        val workManager: WorkManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
                "PeriodicBridgeUploader",
                ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    fun createDailyWork(context: Context) {
        val constraint: Constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                BridgeUploadWorker::class.java, 1, TimeUnit.DAYS)
                .setConstraints(constraint)
                .build()

        val workManager: WorkManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
                "PeriodicBridgeUploader",
                ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }
}