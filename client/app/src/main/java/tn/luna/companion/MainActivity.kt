package tn.luna.companion

import android.os.Bundle
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
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

data class ChatRequest(val message: String, val language: String = "en", val use_history: Boolean = false)
data class ChatReply(val reply: String)

interface LunaApi {
    @POST("v1/companion")
    suspend fun reply(@Body request: ChatRequest, @Header("Authorization") authorization: String? = null): ChatReply
}

internal fun lunaApiBaseUrl(fingerprint: String, model: String, hardware: String): String {
    val isEmulator = fingerprint.startsWith("generic", ignoreCase = true) ||
        fingerprint.startsWith("unknown", ignoreCase = true) ||
        model.contains("emulator", ignoreCase = true) ||
        model.contains("Android SDK built for", ignoreCase = true) ||
        model.contains("sdk_gphone", ignoreCase = true) ||
        hardware in setOf("goldfish", "ranchu", "vbox86")
    return if (isEmulator) "http://10.0.2.2:8787/" else "http://127.0.0.1:8787/"
}

class ChatModel : ViewModel() {
    private val api = Retrofit.Builder()
        .baseUrl(
            lunaApiBaseUrl(
                android.os.Build.FINGERPRINT.orEmpty(),
                android.os.Build.MODEL.orEmpty(),
                android.os.Build.HARDWARE.orEmpty()
            )
        )
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.sync(this)
        setContent {
            val chat: ChatModel = viewModel()
            val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                        chat.draft = it.take(2000)
                    }
                }
            }
            LunaApp(chat, onDictate = {
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
            })
        }
    }

    override fun onResume() {
        super.onResume()
        ReminderPreferences.recordActivity(this)
    }
}
