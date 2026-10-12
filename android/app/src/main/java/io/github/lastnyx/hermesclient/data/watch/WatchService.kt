package io.github.lastnyx.hermesclient.data.watch

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** Foreground shell only: it keeps the process alive; [WatchHub] decides when it ends. */
class WatchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(WORKING_NOTIFICATION_ID, WatchNotifier(this).working(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        // Process was restarted by the system with nothing to watch.
        if (!WatchHub.active) stopSelf()
        return START_NOT_STICKY
    }
}
