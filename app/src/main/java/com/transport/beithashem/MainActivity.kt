package com.transport.beithashem

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TransportAppTheme {
                AppScreen()
            }
        }
    }
}

// ---------- Theme ----------

@Composable
fun TransportAppTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = Color(0xFF1565C0),
        secondary = Color(0xFF00897B),
        background = Color(0xFFF5F7FA),
        surface = Color.White
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private val extensions = listOf(2, 3, 4, 5)

// ---------- Main Screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialogExtension by remember { mutableStateOf<Int?>(null) }
    var downloadingExtension by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ניהול קו הרישום להסעות בית השם",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(extensions) { ext ->
                ExtensionCard(
                    extension = ext,
                    isDownloading = downloadingExtension == ext,
                    onOpenClick = { dialogExtension = ext },
                    onDownloadClick = {
                        downloadingExtension = ext
                        scope.launch {
                            val bytes = ApiClient.downloadReport(ext)
                            downloadingExtension = null
                            if (bytes != null) {
                                val saved = saveCsvToDownloads(context, "דוח_שלוחה_$ext.csv", bytes)
                                snackbarHostState.showSnackbar(
                                    if (saved) "הדוח נשמר בהצלחה בתיקיית ההורדות"
                                    else "שגיאה בשמירת הדוח"
                                )
                            } else {
                                snackbarHostState.showSnackbar("שגיאה בהורדת הדוח")
                            }
                        }
                    }
                )
            }
        }
    }

    dialogExtension?.let { ext ->
        OpenExtensionDialog(
            extension = ext,
            onDismiss = { dialogExtension = null },
            onFinished = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        )
    }
}

// ---------- Extension Card ----------

@Composable
fun ExtensionCard(
    extension: Int,
    isDownloading: Boolean,
    onOpenClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "שלוחה $extension",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("לפתיחת השלוחה")
                }
                OutlinedButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.weight(1f),
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("להורדת הדוח")
                    }
                }
            }
        }
    }
}

// ---------- Open Extension Dialog ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenExtensionDialog(
    extension: Int,
    onDismiss: () -> Unit,
    onFinished: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var places by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(0) }
    var minute by remember { mutableStateOf(0) }
    var second by remember { mutableStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun setNextFriday() {
        val today = LocalDate.now()
        var daysAhead = (DayOfWeek.FRIDAY.value - today.dayOfWeek.value).toLong()
        if (daysAhead <= 0) daysAhead += 7
        val nextFriday = today.plusDays(daysAhead)
        dateText = nextFriday.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                dateText = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("הגדרות שלוחה $extension", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = places,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) places = it },
                    label = { Text("מספר מקומות") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Text("תאריך סגירה", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        placeholder = { Text("dd/mm/yyyy") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { openDatePicker() }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "בחירת תאריך")
                    }
                }
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = { setNextFriday() },
                    label = { Text("יום שישי הקרוב") },
                    leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )

                Spacer(Modifier.height(16.dp))

                Text("שעת סגירה", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NumberStepper(label = "שעה", value = hour, range = 0..23, onValueChange = { hour = it })
                    NumberStepper(label = "דקה", value = minute, range = 0..59, onValueChange = { minute = it })
                    NumberStepper(label = "שנייה", value = second, range = 0..59, onValueChange = { second = it })
                }

                errorText?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    errorText = null
                    if (places.isBlank()) {
                        errorText = "נא למלא מספר מקומות"
                        return@Button
                    }

                    var formattedDateTime: String? = null
                    if (dateText.isNotBlank()) {
                        try {
                            val parts = dateText.split("/")
                            if (parts.size != 3) throw IllegalArgumentException()
                            val day = parts[0].toInt()
                            val month = parts[1].toInt()
                            val year = parts[2].toInt()
                            val date = LocalDate.of(year, month, day)
                            formattedDateTime = String.format(
                                "%04d-%02d-%02d-%02d-%02d-%02d",
                                date.year, date.monthValue, date.dayOfMonth, hour, minute, second
                            )
                        } catch (e: Exception) {
                            errorText = "תאריך לא תקין, יש להזין בפורמט dd/mm/yyyy"
                            return@Button
                        }
                    }

                    isSubmitting = true
                    scope.launch {
                        val result = ApiClient.openExtension(extension, places, formattedDateTime)
                        isSubmitting = false
                        when (result) {
                            is ApiClient.OpenResult.Success -> {
                                val msg = buildString {
                                    append("השלוחה נפתחה בהצלחה. מספר מקומות: $places")
                                    if (dateText.isNotBlank()) append(", עד לתאריך: $dateText")
                                }
                                onFinished(msg)
                                onDismiss()
                            }
                            is ApiClient.OpenResult.Failure -> {
                                errorText = result.stage
                            }
                        }
                    }
                }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("אישור")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("ביטול")
            }
        }
    )
}

// ---------- Number Stepper ----------

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "הגדל")
        }
        Text(
            text = String.format("%02d", value),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp)
        )
        IconButton(
            onClick = { if (value > range.first) onValueChange(value - 1) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "הקטן")
        }
    }
}

// ---------- File Saving ----------

/**
 * Saves CSV bytes to the public Downloads folder using MediaStore (no legacy storage permission needed).
 */
fun saveCsvToDownloads(context: Context, fileName: String, bytes: ByteArray): Boolean {
    return try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return false

        val outputStream: OutputStream? = resolver.openOutputStream(uri)
        outputStream?.use { it.write(bytes) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) {
        false
    }
}
