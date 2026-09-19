package ovh.gabrielhuav.flasklogin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.Response

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionManager(app)
    private val api = Network.api(session)

    // Estados que observa la interfaz
    var loggedIn by mutableStateOf(session.token != null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)
    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set

    // Ejecuta una llamada a la API manejando carga, errores y sesión expirada
    private fun <T> call(block: suspend () -> Response<T>, onOk: (T?) -> Unit) =
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val r = block()
                when {
                    r.isSuccessful -> onOk(r.body())
                    r.code() == 401 && loggedIn -> {
                        logout()
                        error = "Sesión expirada, inicia sesión de nuevo"
                    }
                    else -> error = r.errorMsg()
                }
            } catch (e: Exception) {
                error = "No se pudo conectar con el servidor"
            }
            loading = false
        }

    fun login(u: String, p: String) = call({ api.login(AuthRequest(u, p)) }) {
        session.token = it?.token
        info = null
        loggedIn = true
        loadTasks()
    }

    fun register(u: String, p: String, onDone: () -> Unit) =
        call({ api.register(AuthRequest(u, p)) }) {
            info = "Registro exitoso, ahora inicia sesión"
            onDone()
        }

    fun logout() {
        session.token = null
        loggedIn = false
        tasks = emptyList()
    }

    fun loadTasks() = call({ api.getTasks() }) { tasks = it ?: emptyList() }
    fun create(t: Task) = call({ api.createTask(t) }) { loadTasks() }
    fun update(t: Task) = call({ api.updateTask(t.id!!, t) }) { loadTasks() }
    fun delete(id: Int) = call({ api.deleteTask(id) }) { loadTasks() }
}