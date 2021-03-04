package org.sagebionetworks.research.mindkind.work

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.reactivex.disposables.CompositeDisposable
import org.sagebionetworks.research.presentation.perform_task.TaskResultManager
import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager
import javax.inject.Inject

public class BridgeUploadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        public val LOG_TAG = BridgeUploadWorker.javaClass.simpleName
    }

    @Inject
    lateinit var taskResultManager: TaskResultManager

    @Inject
    lateinit var taskResultProcessingManager: TaskResultProcessingManager

    private val compositeDisposable = CompositeDisposable()

    override fun doWork(): Result {
        toastAndLog("Bridge Upload Worker")



        return Result.success()
    }

    private fun toastAndLog(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        Log.e(LOG_TAG, message)
    }
}