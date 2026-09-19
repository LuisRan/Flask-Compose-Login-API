package ovh.gabrielhuav.flasklogin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

// Formulario reutilizado por Login y Registro
@Composable
fun AuthForm(
    title: String,
    buttonText: String,
    vm: MainViewModel,
    onSubmit: (String, String) -> Unit
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.error = null }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Usuario") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        vm.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(user.trim(), pass) },
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(buttonText) }
    }
}

@Composable
fun LoginScreen(vm: MainViewModel, onLoggedIn: () -> Unit) {
    LaunchedEffect(vm.loggedIn) { if (vm.loggedIn) onLoggedIn() }
    AuthForm("Iniciar sesión", "Entrar", vm) { u, p -> vm.login(u, p) }
}

@Composable
fun RegisterScreen(vm: MainViewModel, onRegistered: () -> Unit) {
    LaunchedEffect(Unit) { vm.info = null }
    AuthForm("Crear cuenta", "Registrarme", vm) { u, p -> vm.register(u, p, onRegistered) }
}

// Pantalla del CRUD: crear, leer, actualizar y borrar tareas
@Composable
fun TasksScreen(vm: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    LaunchedEffect(Unit) { vm.loadTasks() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            vm.error?.let {
                Text(
                    it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            if (vm.tasks.isEmpty() && !vm.loading) {
                Text(
                    "No hay tareas. Toca + para crear una.",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(vm.tasks, key = { it.id ?: 0 }) { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = t.done,
                                onCheckedChange = { vm.update(t.copy(done = it)) }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                                )
                                if (t.description.isNotBlank()) {
                                    Text(t.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { editing = t; showDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar")
                            }
                            IconButton(onClick = { t.id?.let { vm.delete(it) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar")
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Crear") }
    }

    if (showDialog) {
        TaskDialog(editing, onDismiss = { showDialog = false }) { t ->
            if (t.id == null) vm.create(t) else vm.update(t)
            showDialog = false
        }
    }
}

@Composable
fun TaskDialog(task: Task?, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var desc by remember { mutableStateOf(task?.description ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Nueva tarea" else "Editar tarea") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Título") }, singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Descripción") }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        Task(
                            id = task?.id,
                            title = title.trim(),
                            description = desc.trim(),
                            done = task?.done ?: false
                        )
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}