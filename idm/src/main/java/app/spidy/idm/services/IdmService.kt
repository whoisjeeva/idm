package app.spidy.idm.services


import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.spidy.hiper.controllers.Caller
import app.spidy.hiper.controllers.Hiper
import app.spidy.idm.App
import app.spidy.idm.R
import app.spidy.idm.controllers.formatBytes
import app.spidy.idm.controllers.secsToTime
import app.spidy.idm.data.Snapshot
import app.spidy.idm.interfaces.IdmListener
import app.spidy.kotlinutils.onUiThread
import java.io.File
import java.io.RandomAccessFile

class IdmService: Service() {
    companion object {
        const val STICKY_NOTIFICATION_ID = 101
    }

    private lateinit var snapshot: Snapshot
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManagerCompat
    private val queue = ArrayList<Snapshot>()
    private var downloadSpeed: String = "0Kb/s"
    private var remainingTime: String = "0sec"
    private var progress: Int = 0
    private var isDone = false
    private val hiper = Hiper()
    private var prevDownloaded = 0L
    private var isCalcSpeedRunning = false
    private var caller: Caller? = null

    var idmListener: IdmListener? = null

    override fun onBind(intent: Intent?): IBinder? {
        return IdmBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager = NotificationManagerCompat.from(this@IdmService)
        notification = NotificationCompat.Builder(this@IdmService, App.CHANNEL_ID)
        notification.setProgress(100, 0, true)
        notification.setSmallIcon(android.R.drawable.stat_sys_download)
        notification.setOnlyAlertOnce(true)
        notification.color = ContextCompat.getColor(this@IdmService, R.color.colorAccent)

        startForeground(STICKY_NOTIFICATION_ID, notification.build())
        return START_NOT_STICKY
    }

    fun addQueue(snapshot: Snapshot) {
        queue.add(snapshot)
    }

    private fun calcSpeed() {
        isCalcSpeedRunning = true
        Handler().postDelayed({
            updateInfo(
                snapshot.fileName,
                snapshot.downloadedSize,
                snapshot.totalSize
            )
            if (prevDownloaded != 0L) {
                val downloadPerSec = snapshot.downloadedSize - prevDownloaded
                if (downloadPerSec != 0L) {
                    downloadSpeed = "${formatBytes(downloadPerSec, true)}/s"
                    remainingTime = secsToTime((snapshot.totalSize - snapshot.downloadedSize) / downloadPerSec)
                }
            }
            prevDownloaded = snapshot.downloadedSize

            if (!isDone) {
                calcSpeed()
            } else {
                Log.d("hello", "kill it")
                isCalcSpeedRunning = false
                notificationManager.cancel(STICKY_NOTIFICATION_ID)
            }
        }, 1000)
    }

    fun download() {
        if (queue.isEmpty()) {
            isDone = true
            idmListener?.onDone()
            Log.d("hello", "Done.")
        } else {
            prepare(queue.removeAt(0)) {
                snapshot = it
                if (!isCalcSpeedRunning) calcSpeed()
                idmListener?.onStart(snapshot)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    downloadQ()
                } else{
                    downloadLegacy()
                }
            }
        }
    }

    private fun prepare(snp: Snapshot, callback: (Snapshot) -> Unit) {
        if (snp.totalSize == 0L) {
            hiper.head(snp.url)
                .ifException {
                    Log.d("hello", "Error: ${it?.message}")
                }
                .ifFailed {
                    Log.d("hello", "Request failed on headers")
                }
                .finally { headerResponse ->
                    snp.headers = headerResponse.headers.toHashMap()
                    snp.totalSize = headerResponse.headers.get("content-length")!!.toLong()
                    val tmpHeaders: HashMap<String, Any?> = hashMapOf()
                    tmpHeaders["range"] = "bytes=0-0"
                    hiper.get(snp.url, headers = tmpHeaders)
                        .ifException {
                            Log.d("hello", "Error: ${it?.message}")
                        }
                        .ifFailed {
                            Log.d("hello", "Request failed on resume check")
                            snp.isResumable = false
                            onUiThread { callback(snp) }
                        }
                        .finally { resumeResponse ->
                            snp.isResumable = resumeResponse.statusCode == 206
                            onUiThread { callback(snp) }
                        }
                }
        } else {
            callback(snp)
        }
    }

    private fun downloadQ() {

    }

    private fun downloadLegacy() {
        val file = RandomAccessFile("${snapshot.destUri}${File.separator}${snapshot.fileName}", "rw")
        file.seek(snapshot.downloadedSize)
        val headers = HashMap<String, Any?>()

        if (snapshot.isResumable) {
            headers["range"] = "bytes=${snapshot.downloadedSize}-${snapshot.totalSize}"
        }

        caller = hiper.get(snapshot.url, headers = headers, isStream = true)
            .ifException {
                idmListener?.onInterrupt(snapshot)
                download()
            }
            .ifFailed {
                idmListener?.onFail(snapshot)
                download()
            }
            .ifStream { buffer, byteSize ->
                progress = (snapshot.downloadedSize / snapshot.totalSize.toFloat() * 100.0).toInt()
                if (byteSize == -1) {
                    file.close()
                    download()
                } else {
                    file.write(buffer!!, 0, byteSize)
                    snapshot.downloadedSize += byteSize
                }
            }
            .finally {}
    }


    fun pause() {
        caller?.cancel()
    }


    private fun updateInfo(title: String, downloadedSize: Long, totalSize: Long) {
        notification.setContentTitle(title)
        notification.setContentText("${formatBytes(downloadedSize)}/${formatBytes(totalSize)}")
        notification.setContentInfo(downloadSpeed)
        notification.setProgress(100, progress, false)
        updateNotification(notification)
        idmListener?.onProgress(snapshot, progress)
    }

    private fun updateNotification(notification: NotificationCompat.Builder) {
        notificationManager.notify(STICKY_NOTIFICATION_ID, notification.build())
    }



    inner class IdmBinder: Binder() {
        val service: IdmService
            get() = this@IdmService
    }
}