package tn.luna.companion

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.MediaType
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.JsonParser

class ChatModelTest {
    @Test fun apiBaseUrlUsesEmulatorHostBridgeOnlyForEmulators() {
        assertEquals(
            "http://10.0.2.2:8787/",
            lunaApiBaseUrl("google/sdk_gphone64_x86_64/generic", "sdk_gphone64_x86_64", "ranchu")
        )
        assertEquals(
            "http://10.0.2.2:8787/",
            lunaApiBaseUrl("generic/sdk/generic", "Android SDK built for x86", "unknown")
        )
        assertEquals(
            "http://127.0.0.1:8787/",
            lunaApiBaseUrl("samsung/a54xx/a54", "SM-A546B", "exynos")
        )
    }

    @Test fun requestsIncludeTheSessionTokenOnlyWhenProvided() = runBlocking {
        val headers = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            headers.add(chain.request().header("Authorization"))
            val buffer = okio.Buffer()
            chain.request().body()!!.writeTo(buffer)
            val body = JsonParser.parseString(buffer.readUtf8()).asJsonObject
            assertEquals("Hello", body["message"].asString)
            assertEquals("fr", body["language"].asString)
            assertFalse(body["use_history"].asBoolean)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), "{\"reply\":\"Hello\"}"))
                .build()
        }.build()
        val api = Retrofit.Builder().baseUrl("http://127.0.0.1:8787/")
            .client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(LunaApi::class.java)

        assertEquals("Hello", api.reply(ChatRequest("Hello", "fr"), "Bearer test-session").reply)
        api.reply(ChatRequest("Hello", "fr"))
        assertEquals(listOf("Bearer test-session", null), headers)
    }

    @Test fun invalidMessagesDoNotStartRequests() {
        val model = ChatModel()
        for (message in listOf("", "   ", "x".repeat(2001))) {
            model.send(message)
            assertFalse(model.busy)
            assertNull(model.error)
        }
    }

    @Test fun loggingOutClearsPrivateSessionState() {
        val model = ChatModel()
        model.sessionToken = "private-session"
        model.draft = "A private thought"
        model.resetSession()
        assertEquals("", model.sessionToken)
        assertEquals("", model.draft)
        assertFalse(model.hasReply)
        assertEquals(0, model.sentMessages)
        assertNull(model.error)
    }
}
