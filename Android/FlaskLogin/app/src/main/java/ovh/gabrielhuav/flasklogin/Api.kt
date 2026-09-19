package ovh.gabrielhuav.flasklogin

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

data class AuthRequest(val username: String, val password: String)

data class AuthResponse(
    val token: String?,
    val username: String?,
    val message: String?,
    val error: String?
)

data class Task(
    val id: Int? = null,
    val title: String,
    val description: String = "",
    val done: Boolean = false
)

interface ApiService {
    @POST("register") suspend fun register(@Body b: AuthRequest): Response<AuthResponse>
    @POST("login") suspend fun login(@Body b: AuthRequest): Response<AuthResponse>
    @GET("tasks") suspend fun getTasks(): Response<List<Task>>
    @POST("tasks") suspend fun createTask(@Body t: Task): Response<Task>
    @PUT("tasks/{id}") suspend fun updateTask(@Path("id") id: Int, @Body t: Task): Response<Task>
    @DELETE("tasks/{id}") suspend fun deleteTask(@Path("id") id: Int): Response<Unit>
}

// Guarda el token JWT cifrado en el teléfono
class SessionManager(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().apply {
            if (v == null) remove("token") else putString("token", v)
        }.apply()
}

// Cliente HTTP: agrega el token a cada petición automáticamente
object Network {
    fun api(session: SessionManager): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    session.token?.let { addHeader("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(req)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// Saca el mensaje de error que manda el backend
fun Response<*>.errorMsg(): String =
    try {
        Gson().fromJson(errorBody()?.string(), AuthResponse::class.java).error ?: "Error ${code()}"
    } catch (e: Exception) {
        "Error ${code()}"
    }