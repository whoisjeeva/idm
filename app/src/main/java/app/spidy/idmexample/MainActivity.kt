package app.spidy.idmexample

import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.spidy.hiper.controllers.Hiper
import app.spidy.idm.controllers.Idm
import app.spidy.idm.data.Snapshot
import app.spidy.idm.interfaces.IdmListener
import app.spidy.idm.services.IdmService
import app.spidy.kotlinutils.DEBUG_MODE
import app.spidy.kotlinutils.debug
import java.lang.Exception
import java.net.URI
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    init {
        DEBUG_MODE = true
    }

    private lateinit var downloadBtn: Button
    private lateinit var urlField: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var fileNameView: TextView
    private lateinit var fileNameField: EditText
    private lateinit var pauseBtn: Button
    private lateinit var resumeBtn: Button
    private lateinit var idm: Idm

    private val hiper = Hiper()
    private val hiperLegacy = hiper.Legacy()

    private val snaps = ArrayList<Snapshot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PermissionHandler.requestStorage(this, "need storage permission") {}

        idm = Idm(this)

        downloadBtn = findViewById(R.id.download_button)
        urlField = findViewById(R.id.url_field)
        progressBar = findViewById(R.id.progress_bar)
        fileNameView = findViewById(R.id.filename_text_view)
        fileNameField = findViewById(R.id.filename_field)
        pauseBtn = findViewById(R.id.pause_button)
        resumeBtn = findViewById(R.id.resume_button)


        val snapshot = Snapshot(
            uId = UUID.randomUUID().toString(),
            url = "https://vodhls-vh.akamaihd.net/i/songs/41/2742841/28236538/28236538_64.mp4/index_0_a.m3u8?set-akamai-hls-revision=5&hdntl=exp=1581795721~acl=%2fi%2fsongs%2f41%2f2742841%2f28236538%2f28236538_64.mp4%2f*~data=hdntl~hmac=fe07c10c29f5e48965db2f06bb85edd94a8820a543245216aae623588cc58636",
            isStream = true
        )

        downloadBtn.setOnClickListener {
            prepareStream(snapshot) { snp ->
                idm.download(snp)
            }
        }
    }

    private fun parseStream(baseUrl: String, stream: String): ArrayList<String> {
        val links = ArrayList<String>()
        val lines = stream.split("\n")
        for (line in lines) {
            if (line.trim() != "" && !line.startsWith("#")) {
                if (line.startsWith("https://") || line.startsWith("http://")) {
                    links.add(line)
                } else {
                    val nodes = baseUrl.split("?")[0].split("#")[0].split("/").toMutableList()
                    nodes.removeAt(nodes.size - 1)
                    val uri = Uri.parse(nodes.joinToString("/") + "/")
                        .buildUpon()   // Creates a "Builder"
                        .appendEncodedPath(line)
                        .build()
                    links.add(uri.toString())
                }
            }
        }
        return links
    }

    private fun prepareStream(snapshot: Snapshot, callback: (Snapshot) -> Unit) {
        hiper.get(snapshot.url)
            .ifFailedOrException {
                debug("Failed")
            }
            .finally {
                it.text?.also { streams ->
                    snapshot.streamUrls = parseStream(snapshot.url, streams).toList()
                    callback(snapshot)
                }
            }
    }


    override fun onDestroy() {
        super.onDestroy()
        idm.unbind()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PermissionHandler.STORAGE_PERMISSION_CODE ||
            requestCode == PermissionHandler.LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                PermissionHandler.execute()
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
