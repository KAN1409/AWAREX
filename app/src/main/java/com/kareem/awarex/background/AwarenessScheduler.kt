package com.kareem.awarex.background

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AwarenessScheduler {
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val immediate = OneTimeWorkRequestBuilder<AwarenessWorker>()
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            immediate
        )

        val periodic = PeriodicWorkRequestBuilder<AwarenessWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    private const val IMMEDIATE_WORK_NAME = "awarex-attention-immediate"
    private const val PERIODIC_WORK_NAME = "awarex-attention-periodic"
}
