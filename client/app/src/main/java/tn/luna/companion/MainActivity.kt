package tn.luna.companion

import android.os.Bundle
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.Locale

data class ChatRequest(val message: String, val language: String = "en", val use_history: Boolean = false)
data class ChatReply(val reply: String)

interface LunaApi {
    @POST("v1/companion")
    suspend fun reply(@Body request: ChatRequest, @Header("Authorization") authorization: String? = null): ChatReply
}

class ChatModel : ViewModel() {
    private val api = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:8787/")
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(LunaApi::class.java)
    var reply by mutableStateOf("Hi, I'm Luna. How are you feeling today?")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var sessionToken by mutableStateOf("")
    var draft by mutableStateOf("")
    var language by mutableStateOf("en")
    var hasReply by mutableStateOf(false)
        private set
    var sentMessages by mutableIntStateOf(0)
        private set

    fun resetSession() {
        if (busy) return
        draft = ""
        sessionToken = ""
        reply = "Hi, I'm Luna. How are you feeling today?"
        error = null
        hasReply = false
        sentMessages = 0
    }

    fun send(message: String) {
        if (busy || message.isBlank() || message.length > 2000) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val authorization = sessionToken.trim().takeIf { it.isNotEmpty() }?.let { "Bearer $it" }
                val response = api.reply(ChatRequest(message.trim(), language), authorization)
                check(response.reply.isNotBlank())
                reply = response.reply
                hasReply = true
                sentMessages++
                if (draft == message) draft = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                error = when (e.code()) {
                    401 -> "Enter the session token from your Luna server, then retry."
                    503 -> "AI is not configured. Add OPENROUTER_API_KEY to server/.env and restart the backend."
                    429 -> "Please wait a moment before trying again."
                    else -> "The AI service could not reply. Check the backend configuration and retry."
                }
            } catch (e: Exception) {
                error = "Cannot reach Luna. Start the backend and run adb reverse tcp:8787 tcp:8787, then retry."
            } finally {
                busy = false
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private var speech: TextToSpeech? = null
    private var speechReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speech = TextToSpeech(this) { status -> speechReady = status == TextToSpeech.SUCCESS }
        setContent {
            val chat: ChatModel = viewModel()
            val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                        chat.draft = it.take(2000)
                    }
                }
            }
            LunaApp(chat,
                onDictate = {
                    try {
                        dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, chat.language)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Luna what’s on your mind")
                        })
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, "Voice input is unavailable. You can still type a message.", Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(this, "Voice input was not allowed. You can still type a message.", Toast.LENGTH_LONG).show()
                    }
                },
                onSpeak = {
                    val engine = speech
                    if (engine?.isSpeaking == true) engine.stop()
                    else if (!speechReady || engine == null || engine.setLanguage(Locale.forLanguageTag(chat.language)) < 0) {
                        Toast.makeText(this, "Speech for this language is unavailable on this device.", Toast.LENGTH_LONG).show()
                    } else if (engine.speak(chat.reply, TextToSpeech.QUEUE_FLUSH, null, "luna-reply") == TextToSpeech.ERROR) {
                        Toast.makeText(this, "Luna could not play this reply. Please try again.", Toast.LENGTH_LONG).show()
                    }
                },
                onStopSpeaking = { speech?.stop() }
            )
        }
    }

    override fun onStop() { speech?.stop(); super.onStop() }
    override fun onDestroy() { speech?.shutdown(); super.onDestroy() }
}
