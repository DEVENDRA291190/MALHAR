package com.printbridge.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Receives a PDF (Share / Open with / picker) and prints it page by page. */
@Suppress("DEPRECATION")
class PrintActivity : ComponentActivity() {
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = getString(R.string.st_reading)
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    if (busy) CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(status)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { finish() }) { Text(stringResource(R.string.close)) }
                }
            }
        }
        lifecycleScope.launch { runJob() }
    }

    private fun fail(msg: String) {
        status = getString(R.string.st_error, msg)
        busy = false
    }

    private suspend fun runJob() {
        val s = SettingsStore(this).load()
        val uri: Uri? = if (intent.action == Intent.ACTION_SEND) {
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } else intent.data
        if (uri == null) return fail(getString(R.string.st_pdf_none))

        val file = File(cacheDir, "job.pdf")
        val copied = withContext(Dispatchers.IO) { runCatching { copyTo(uri, file) } }
        if (copied.isFailure) return fail(copied.exceptionOrNull()?.message ?: "?")
        val isPdf = withContext(Dispatchers.IO) {
            file.inputStream().use { ins ->
                val b = ByteArray(5)
                ins.read(b) == 5 && String(b, Charsets.US_ASCII) == "%PDF-"
            }
        }
        if (!isPdf) return fail(getString(R.string.st_not_pdf))

        PrintEngine.printPdf(this, file, s) { p, n -> status = getString(R.string.st_page, p, n) }
            .onSuccess { status = getString(R.string.st_sent); busy = false }
            .onFailure { fail(it.message ?: "?") }
    }

    private fun copyTo(uri: Uri, out: File) {
        val scheme = uri.scheme
        if (scheme == "http" || scheme == "https") {
            val c = URL(uri.toString()).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "PrintBridge/1.0 (Android)")
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        } else {
            val ins = contentResolver.openInputStream(uri) ?: error("Cannot open file")
            ins.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }
    }
}
