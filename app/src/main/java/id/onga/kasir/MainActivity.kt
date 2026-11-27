package id.onga.kasir

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kasir Onga V.1 — aplikasi Android untuk Kasir Onga versi web.
 *
 * Fitur:
 * - WebView layar penuh (JavaScript + DOM storage aktif, supaya data & tema tersimpan)
 * - Tombol Cetak: mencetak halaman/struk lewat Android Print Framework
 *   (bisa memilih printer Bluetooth / WiFi yang terhubung ke HP)
 * - Bridge JavaScript: halaman web bisa memanggil window.OngaAndroid.print()
 * - Tautan WhatsApp / telepon / SMS dibuka di aplikasi aslinya
 * - Upload file (mis. impor produk) didukung
 * - Tombol Kembali menelusuri riwayat WebView dulu
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris: Array<Uri>? = when {
            result.resultCode != Activity.RESULT_OK -> null
            data?.clipData != null -> Array(data.clipData!!.itemCount) { i ->
                data.clipData!!.getItemAt(i).uri
            }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    /** Dipanggil dari JavaScript di halaman web: window.OngaAndroid.print() */
    inner class PrintBridge(private val context: Context) {
        @JavascriptInterface
        fun print() {
            (context as? Activity)?.runOnUiThread { printCurrentPage() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btnPrint).setOnClickListener { printCurrentPage() }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
        }
        webView.addJavascriptInterface(PrintBridge(this), "OngaAndroid")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                val host = url.host ?: ""
                val scheme = url.scheme ?: ""
                val external = scheme == "tel" || scheme == "sms" || scheme == "mailto" ||
                    host.contains("wa.me") || host.contains("api.whatsapp.com") ||
                    host.contains("whatsapp.com")
                return if (external) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Aplikasi tujuan tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                    true
                } else {
                    false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileUploadCallback = null
                    false
                }
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(AppConfig.WEB_APP_URL)
        }
        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        checkForUpdate()
    }

    /* ---------- force update: cek version.json di server ---------- */
    private var updateDialogShowing = false

    private fun checkForUpdate() {
        Thread {
            try {
                val conn = URL(AppConfig.VERSION_JSON_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                val minCode = json.optInt("minVersionCode", 0)
                val apkUrl = json.optString("apkUrl", AppConfig.APK_DOWNLOAD_URL)
                val msg = json.optString(
                    "message",
                    "Ada versi baru Kasir Onga. Perbarui untuk melanjutkan."
                )
                val info = packageManager.getPackageInfo(packageName, 0)
                val installed = if (Build.VERSION.SDK_INT >= 28) {
                    info.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION") info.versionCode
                }
                if (installed < minCode) {
                    runOnUiThread { showUpdateDialog(msg, apkUrl) }
                }
            } catch (e: Exception) {
                /* gagal cek (mis. offline) tidak mengganggu pemakaian */
            }
        }.start()
    }

    private fun showUpdateDialog(msg: String, apkUrl: String) {
        if (updateDialogShowing || isFinishing) return
        updateDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle("Pembaruan Diperlukan")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("Perbarui Sekarang") { _, _ ->
                updateDialogShowing = false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Tidak bisa membuka tautan unduhan", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun printCurrentPage() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "${AppConfig.APP_TITLE} - struk"
        val adapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(
            jobName,
            adapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                .build()
        )
    }

    @Deprecated("Masih dipakai untuk tombol Kembali WebView")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }
}
