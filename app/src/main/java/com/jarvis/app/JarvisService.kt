package com.jarvis.app

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.*

class JarvisService : Service() {
    private var pv: PorcupineManager? = null
    private var tts: TextToSpeech? = null
    private var sr: SpeechRecognizer? = null
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        prefs = getSharedPreferences("j", MODE_PRIVATE)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis").setContentTitle("Jarvis sun raha hai")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN"); tts?.setPitch(0.9f); tts?.setSpeechRate(0.95f)
            }
        }
        try {
            pv = PorcupineManager.Builder().setAccessKey(prefs.getString("pv", "") ?: "")
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .build(this, PorcupineManagerCallback { ui.post { wake() } })
            pv?.start()
        } catch (e: Exception) {
            ui.postDelayed({ speak("Wake word start nahi hua. AccessKey check karein.") }, 1500)
        }
        return START_STICKY
    }

    private fun speak(t: String, done: (() -> Unit)? = null) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) { ui.post { done?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(u: String?) { ui.post { done?.invoke() } }
        })
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    private fun resume() { try { pv?.start() } catch (e: Exception) {} }

    private fun wake() {
        pv?.stop()
        speak("जी सर?") { listen() }
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { speak("Speech service nahi mili.") { resume() }; return }
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                handle(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: "")
            }
            override fun onError(e: Int) { speak("सर, मैं सुन नहीं पाया.") { resume() } }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        sr?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN"))
    }

    private fun handle(h: String) {
        val t = h.lowercase()
        when {
            t.contains("play") || t.contains("गाना") -> play(t)
            t.contains("office") || t.contains("ऑफिस") || t.contains("ऑफ़िस") -> brief("office", h)
            t.contains("morning") || t.contains("briefing") || t.contains("मॉर्निंग") || t.contains("सुबह") ||
                t.contains("lead") || t.contains("लीड") -> brief("morning", h)
            else -> speak("सर, अभी मैं briefing, office mode और गाने समझता हूँ.") { resume() }
        }
    }

    private fun play(t: String) {
        val q = t.substringAfter("play").replace("on spotify", "").replace("स्पॉटिफाई पर", "").trim()
        if (q.isBlank()) { speak("कौन सा गाना, सर?") { resume() }; return }
        try {
            startActivity(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage("com.spotify.music")
                .putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log(t, "spotify: $q")
            speak("ठीक है सर.") { resume() }
        } catch (e: Exception) { speak("Spotify नहीं खुल पाया.") { resume() } }
    }

    private fun brief(mode: String, heard: String) {
        Thread {
            val said = try {
                val u = prefs.getString("url", "") + "?mode=$mode&key=" + URLEncoder.encode(prefs.getString("key", ""), "UTF-8")
                org.json.JSONObject(URL(u).readText()).getString("say")
            } catch (e: Exception) { "सर, leads लोड नहीं हुए. Internet या link check करिए." }
            log(heard, said)
            ui.post { speak(said) { resume() } }
        }.start()
    }

    private fun log(heard: String, said: String) {
        val f = File(filesDir, "history.txt")
        val l = (if (f.exists()) f.readLines() else listOf()) + "${Date()} | $heard | ${said.take(120)}"
        f.writeText(l.takeLast(50).joinToString("\n"))
    }

    override fun onDestroy() {
        try { pv?.stop(); pv?.delete() } catch (e: Exception) {}
        sr?.destroy(); tts?.shutdown(); super.onDestroy()
    }
}
