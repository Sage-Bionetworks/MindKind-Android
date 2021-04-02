package org.sagebionetworks.research.mindkind.backgrounddata

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

public class WorkUtils {
    companion object {
        /**
         * Work will be enqueued immediately and if this function has been called before,
         * It will keep the original work request, so we don't upload too frequently.
         * It will also have an initial delay equal to the period interval
         *
         * @param context can be app, activity, or service
         * @param periodWorkName name of the worker to be referenced later when cancelling
         */
        fun enqueueDailyWork(context: Context, periodWorkName: String) {
            val constraint: Constraints = Constraints.Builder()
                    .build()

            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                    DataUsageWorker::class.java, 1, TimeUnit.DAYS)
                    .setConstraints(constraint)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .build()

            val workManager: WorkManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(periodWorkName,
                    ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }

        /**
         * Work will be enqueued immediately and if this function has been called before,
         * It will keep the original work request, so we don't upload too frequently.
         * It will not have an initial delay.
         *
         * @param context can be app, activity, or service
         * @param periodWorkName name of the worker to be referenced later when cancelling
         */
        fun enqueueHourlyWork(context: Context, periodWorkName: String) {
            val constraint: Constraints = Constraints.Builder()
                    .build()

            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                    DataUsageWorker::class.java, 1, TimeUnit.HOURS)
                    .setConstraints(constraint)
                    .build()

            val workManager: WorkManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(DataUsageWorker.periodicWorkName,
                    ExistingPeriodicWorkPolicy.KEEP, workRequest)
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