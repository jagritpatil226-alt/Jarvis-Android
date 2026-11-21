package com.jarvis.app
import java.io.File
import java.io.fileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("j", MODE_PRIVATE)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        fun field(h: String, k: String) = EditText(this).apply {
            hint = h; setText(p.getString(k, "")); inputType = InputType.TYPE_CLASS_TEXT; box.addView(this)
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() }; box.addView(this) }
        val a = field("Picovoice AccessKey", "pv")
        val u = field("Apps Script web app URL (ends with /exec)", "url")
        val s = field("Secret key", "key")
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
        btn("1. Allow background running") {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName))
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        btn("2. Allow Spotify start (display over apps)") {
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("3. Start Jarvis") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Pehle microphone permission allow karein", Toast.LENGTH_LONG).show()
            } else {
                p.edit().putString("pv", a.text.toString().trim()).putString("url", u.text.toString().trim())
                    .putString("key", s.text.toString().trim()).apply()
                startForegroundService(Intent(this, JarvisService::class.java))
                Toast.makeText(this, "Jarvis chalu hai. Bolo: Jarvis", Toast.LENGTH_LONG).show()
            }
        }
        setContentView(ScrollView(this).apply { addView(box) })
    }
    fun downloadAndUnzip(urlString: String, targetDirectory: File) {
Thread {
try {
val url = URL(urlString)
val connection = url.openConnection()
connection.connect()
val input = connection.getInputStream()
val zipFile = File(targetDirectory, "model.zip")
val output = FileOutputStream(zipFile)
val data = ByteArray(1024)
var count: Int
while (input.read(data).also { count = it } != -1) {
output.write(data, 0, count)
}
output.flush()
output.close()
input.close()
val zin = ZipInputStream(java.io.FileInputStream(zipFile))
var ze = zin.nextEntry
while (ze != null) {
val extractedFile = File(targetDirectory, ze.name)
if (ze.isDirectory) {
extractedFile.mkdirs()
} else {
extractedFile.parentFile?.mkdirs()
val fout = FileOutputStream(extractedFile)
val buffer = ByteArray(1024)
var length: Int
while (zin.read(buffer).also { length = it } != -1) {
fout.write(buffer, 0, length)
}
fout.close()
}
zin.closeEntry()
ze = zin.nextEntry
}
zin.close()
zipFile.delete()
} catch (e: Exception) {
e.printStackTrace()
}
}.start()
}
}
