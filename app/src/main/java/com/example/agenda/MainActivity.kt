package com.example.agenda

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: AppointmentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
        setContent {
            AgendaTheme {
                AgendaApp(viewModel = viewModel)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}

class AppointmentViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).appointmentDao()

    val appointments: LiveData<List<Appointment>> = dao.getUpcomingAppointments().asLiveData()
    val upcomingCount: LiveData<Int> = dao.getUpcomingCount().asLiveData()
    val nextAppointment: LiveData<Appointment?> = dao.getNextAppointment().asLiveData()

    val searchQuery = mutableStateOf("")
    val selectedCategory = mutableStateOf("Todos")

    val filteredAppointments: LiveData<List<Appointment>> = androidx.lifecycle.MediatorLiveData<List<Appointment>>().apply {
        addSource(appointments) { appointments ->
            value = filterAppointments(appointments, searchQuery.value, selectedCategory.value)
        }
        addSource(searchQuery) { query ->
            value = filterAppointments(appointments.value ?: emptyList(), query, selectedCategory.value)
        }
        addSource(selectedCategory) { category ->
            value = filterAppointments(appointments.value ?: emptyList(), searchQuery.value, category)
        }
    }

    private fun filterAppointments(appointments: List<Appointment>, query: String, category: String): List<Appointment> {
        return appointments.filter { appointment ->
            val matchesSearch = query.isEmpty() ||
                appointment.title.contains(query, ignoreCase = true) ||
                appointment.description.contains(query, ignoreCase = true)
            val matchesCategory = category == "Todos" || appointment.category == category
            matchesSearch && matchesCategory
        }
    }

    init {
        scheduleCleanup(application.applicationContext)
        cleanupExpired()
    }

    fun saveAppointment(title: String, description: String, category: String, dateTimeMs: Long, id: Long = 0L) {
        viewModelScope.launch {
            val appointment = Appointment(
                id = id,
                title = title.trim(),
                description = description.trim(),
                category = category,
                appointmentDateTimeMs = dateTimeMs,
                createdAt = if (id == 0L) System.currentTimeMillis() else appointments.value?.find { it.id == id }?.createdAt ?: System.currentTimeMillis()
            )
            if (appointment.id == 0L) {
                val newId = dao.insert(appointment)
                AppointmentAlarmScheduler.scheduleReminders(getApplication(), appointment.copy(id = newId))
            } else {
                dao.update(appointment)
                AppointmentAlarmScheduler.cancelReminders(getApplication(), appointment)
                AppointmentAlarmScheduler.scheduleReminders(getApplication(), appointment)
            }
            cleanupExpired()
        }
    }

    fun deleteAppointment(appointment: Appointment) {
        viewModelScope.launch {
            dao.delete(appointment)
            AppointmentAlarmScheduler.cancelReminders(getApplication(), appointment)
        }
    }

    private fun cleanupExpired() {
        viewModelScope.launch {
            dao.deleteExpired(System.currentTimeMillis())
        }
    }

    private fun scheduleCleanup(context: android.content.Context) {
        CleanupWorker.schedule(context)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaApp(viewModel: AppointmentViewModel) {
    val appointments = viewModel.filteredAppointments.observeAsState(emptyList())
    val upcomingCount = viewModel.upcomingCount.observeAsState(0)
    val nextAppointment = viewModel.nextAppointment.observeAsState()
    val showDialog = rememberSaveable { mutableStateOf(false) }
    val editingAppointment = remember { mutableStateOf<Appointment?>(null) }
    val isDarkTheme = rememberSaveable { mutableStateOf(false) }

    AgendaTheme(darkTheme = isDarkTheme.value) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(text = "Agenda Pro") },
                    actions = {
                        IconButton(onClick = { isDarkTheme.value = !isDarkTheme.value }) {
                            Icon(
                                imageVector = if (isDarkTheme.value) Icons.Default.Edit else Icons.Default.Add, // TODO: Use proper icons
                                contentDescription = "Toggle Theme"
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                // Dashboard
                DashboardSection(upcomingCount.value, nextAppointment.value)

                // Search and Filter Bar
                SearchFilterBar(
                    searchQuery = viewModel.searchQuery.value,
                    onSearchChange = { viewModel.searchQuery.value = it },
                    selectedCategory = viewModel.selectedCategory.value,
                    onCategoryChange = { viewModel.selectedCategory.value = it }
                )
            if (appointments.value.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum compromisso cadastrado. Toque no + para adicionar.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(appointments.value) { appointment ->
                        AppointmentCard(
                            appointment = appointment,
                            onEdit = {
                                editingAppointment.value = appointment
                                showDialog.value = true
                            },
                            onDelete = { viewModel.deleteAppointment(appointment) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                FloatingActionButton(
                    onClick = {
                        editingAppointment.value = null
                        showDialog.value = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar")
                }
            }

            if (showDialog.value) {
                AppointmentDialog(
                    appointment = editingAppointment.value,
                    onDismiss = { showDialog.value = false },
                    onSave = { title, description, category, datetimeMs, id ->
                        viewModel.saveAppointment(title, description, category, datetimeMs, id)
                        showDialog.value = false
                    }
                )
            }
        }
    }
}

@Composable
fun AppointmentCard(
    appointment: Appointment,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appointment.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (appointment.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = appointment.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatAppointmentDate(appointment.appointmentDateTimeMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = appointment.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AppointmentDialog(
    appointment: Appointment?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, category: String, dateTimeMs: Long, id: Long) -> Unit
) {
    val context = LocalContext.current
    val titleState = rememberSaveable { mutableStateOf(appointment?.title ?: "") }
    val descriptionState = rememberSaveable { mutableStateOf(appointment?.description ?: "") }
    val categoryState = rememberSaveable { mutableStateOf(appointment?.category ?: "Outro") }
    val dateTimeState = rememberSaveable { mutableStateOf(appointment?.appointmentDateTimeMs ?: System.currentTimeMillis()) }
    val visibleDate = remember { mutableStateOf(dateTimeState.value) }

    val categories = listOf("Trabalho", "Pessoal", "Saúde", "Educação", "Outro")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = if (appointment == null) "Novo compromisso" else "Editar compromisso", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = titleState.value,
                    onValueChange = { titleState.value = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = descriptionState.value,
                    onValueChange = { descriptionState.value = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = { /* TODO */ }
                ) {
                    OutlinedTextField(
                        value = categoryState.value,
                        onValueChange = { },
                        label = { Text("Categoria") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = false,
                        onDismissRequest = { }
                    ) {
                        categories.forEach { category ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(category) },
                                onClick = { categoryState.value = category }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Data e hora:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                DateTimePickerFields(
                    dateTimeMs = dateTimeState.value,
                    onDateTimeSelected = {
                        dateTimeState.value = it
                        visibleDate.value = it
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (titleState.value.isNotBlank()) {
                                onSave(titleState.value, descriptionState.value, categoryState.value, dateTimeState.value, appointment?.id ?: 0L)
                            }
                        }
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
fun DateTimePickerFields(dateTimeMs: Long, onDateTimeSelected: (Long) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { timeInMillis = dateTimeMs }
    val dateText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dateTimeMs))

    Column {
        Button(onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hour)
                            calendar.set(Calendar.MINUTE, minute)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            onDateTimeSelected(calendar.timeInMillis)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }) {
            Text(text = dateText)
        }
    }
}

fun formatAppointmentDate(dateTimeMs: Long): String {
    return SimpleDateFormat("EEE, dd MMM yyyy 'às' HH:mm", Locale.getDefault()).format(Date(dateTimeMs))
}

@Composable
fun DashboardSection(upcomingCount: Int, nextAppointment: Appointment?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = upcomingCount.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Compromissos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (nextAppointment != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatAppointmentDate(nextAppointment.appointmentDateTimeMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                        Text(
                            text = "Próximo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit
) {
    val categories = listOf("Todos", "Trabalho", "Pessoal", "Saúde", "Educação", "Outro")

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Buscar compromissos") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Buscar") // TODO: Use search icon
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                androidx.compose.material3.FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category) }
                )
            }
        }
    }
}
