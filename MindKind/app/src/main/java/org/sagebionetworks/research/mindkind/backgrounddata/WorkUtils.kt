package org.sagebionetworks.research.mindkind.backgrounddata

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

public class WorkUtils {
    companion object {

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
        fun enqueueDailyWorkNetwork(context: Context,
                                    workerClass: Class<out ListenableWorker?>,
                                    periodicWorkName: String) {

            val constraint: Constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                    workerClass, 1, TimeUnit.DAYS)
                    .setConstraints(constraint)
                    .build()

            val workManager: WorkManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(periodicWorkName,
                    ExistingPeriodicWorkPolicy.REPLACE, workRequest)
        }

        /**
         * Schedule work at the fastest possible interval available to the system
         * @param context can be app, activity, or service
         * @param periodWorkName name of the worker to be referenced later when cancelling
         */
        fun enqueueFastestWorker(context: Context,
                              workerClass: Class<out ListenableWorker?>,
                              periodicWorkName: String) {

            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                    workerClass, PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                    TimeUnit.MILLISECONDS)
                    .build()

            val workManager: WorkManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(periodicWorkName,
                    ExistingPeriodicWorkPolicy.REPLACE, workRequest)
        }

        /**
         * Work will be enqueued immediately and if this function has been called before,
         * It will keep the original work request, so we don't upload too frequently.
         * It will not have an initial delay.
         *
         * @param context can be app, activity, or service
         * @param periodWorkName name of the worker to be referenced later when cancelling
         */
        fun enqueueHourlyWork(context: Context,
                              workerClass: Class<out ListenableWorker?>,
                              periodicWorkName: String) {

            val constraint: Constraints = Constraints.Builder()
                    .build()

            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                    workerClass, 1, TimeUnit.HOURS)
                    .setConstraints(constraint)
                    .build()

            val workManager: WorkManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(periodicWorkName,
                    ExistingPeriodicWorkPolicy.REPLACE, workRequest)
        }

        /**
         * Call to cancel periodic work, like when the user withdraws from or is done with the study
         * @param periodWorkName name of the worker to be referenced later when cancelling
         */
        fun cancelPeriodicWork(context: Context, periodWorkName: String) {
            val workManager: WorkManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(periodWorkName)
        }
    }
}