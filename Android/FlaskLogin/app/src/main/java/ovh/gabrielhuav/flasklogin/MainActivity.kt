package ovh.gabrielhuav.flasklogin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import ovh.gabrielhuav.flasklogin.ui.theme.FlaskLoginTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlaskLoginTheme {
                AppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun go(route: String) {
        scope.launch { drawer.close() }
        nav.navigate(route) { launchSingleTop = true }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Mis Tareas",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                if (!vm.loggedIn) {
                    NavigationDrawerItem(
                        label = { Text("Iniciar sesión") },
                        selected = false,
                        onClick = { go("login") }
                    )
                    NavigationDrawerItem(
                        label = { Text("Registro") },
                        selected = false,
                        onClick = { go("register") }
                    )
                } else {
                    NavigationDrawerItem(
                        label = { Text("Operaciones CRUD") },
                        selected = false,
                        onClick = { go("tasks") }
                    )
                    NavigationDrawerItem(
                        label = { Text("Cerrar sesión") },
                        selected = false,
                        onClick = {
                            vm.logout()
                            go("login")
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (vm.loggedIn) "Sesión iniciada" else "Mis Tareas") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    }
                )
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                NavHost(
                    navController = nav,
                    startDestination = if (vm.loggedIn) "tasks" else "login"
                ) {
                    composable("login") {
                        LoginScreen(vm) {
                            nav.navigate("tasks") { popUpTo("login") { inclusive = true } }
                        }
                    }
                    composable("register") {
                        RegisterScreen(vm) { nav.navigate("login") }
                    }
                    composable("tasks") { TasksScreen(vm) }
                }
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}