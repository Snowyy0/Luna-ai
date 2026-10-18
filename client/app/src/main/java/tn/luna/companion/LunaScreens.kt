package tn.luna.companion

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Lilac = Color(0xFFA593C7)
private val Ink = Color(0xFF35313E)
private enum class Screen { Welcome, Home, Calendar, Profile }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunaApp(chat: ChatModel) {
    val context = LocalContext.current.applicationContext
    val examStore = remember { ExamStore(context) }
    val exams = remember { mutableStateOf(examStore.all().sortedBy { it.date }) }
    var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("Youssef") }
    val remindersEnabled = remember { mutableStateOf(ReminderPreferences.enabled(context)) }
    val view = LocalView.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remindersEnabled.value = granted
        ReminderPreferences.setEnabled(context, granted)
        ReminderScheduler.sync(context)
    }
    val updateReminders: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            remindersEnabled.value = enabled
            ReminderPreferences.setEnabled(context, enabled)
            ReminderScheduler.sync(context)
        }
    }
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFC2ADE7), onPrimary = Ink, background = Color(0xFF211E2B),
        surface = Color(0xFF383146), onSurface = Color(0xFFF5F0FC),
        onSurfaceVariant = Color(0xFFC4BACF), secondaryContainer = Color(0xFF4B405F)
    ) else lightColorScheme(
        primary = Color(0xFF8C75B8), onPrimary = Color.White, background = Color(0xFFFAF8F6),
        surface = Color.White.copy(alpha = .86f), onSurface = Ink,
        onSurfaceVariant = Color(0xFF827A8D), secondaryContainer = Color(0xFFEAE5F3)
    )

    BackHandler(screen != Screen.Welcome && screen != Screen.Home) { screen = Screen.Home }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                PastelBackdrop(dark)
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().imePadding().widthIn(max = 560.dp)
                        .align(Alignment.TopCenter)
                ) {
                    if (screen == Screen.Welcome) {
                        WelcomeScreen { screen = Screen.Home }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Luna", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { screen = Screen.Profile }) { Text("Settings") }
                        }
                        Box(Modifier.weight(1f)) {
                            when (screen) {
                                Screen.Home -> ChatScreen(chat) { screen = Screen.Profile }
                                Screen.Calendar -> CalendarScreen(
                                    exams = exams.value,
                                    onAdd = { title, date ->
                                        val exam = examStore.add(title, date)
                                        exams.value = examStore.all().sortedBy { it.date }
                                        ReminderScheduler.scheduleExam(context, exam)
                                    },
                                    onRemove = { id ->
                                        examStore.remove(id)
                                        ReminderScheduler.cancelExam(context, id)
                                        exams.value = examStore.all().sortedBy { it.date }
                                    }
                                )
                                Screen.Profile -> ProfileScreen(
                                    name = name,
                                    remindersEnabled = remindersEnabled.value,
                                    notificationsAllowed = notificationsAllowed(context),
                                    dark = dark,
                                    language = chat.language,
                                    sessionToken = chat.sessionToken,
                                    onNameChange = { name = it },
                                    onRemindersChange = updateReminders,
                                    onDarkChange = { dark = it },
                                    onLanguageChange = { chat.language = it },
                                    onTokenChange = { chat.sessionToken = it }
                                )
                                Screen.Welcome -> Unit
                            }
                        }
                        Surface(
                            Modifier.padding(horizontal = 23.dp, vertical = 10.dp).fillMaxWidth(),
                            shape = CircleShape, color = colors.surface, shadowElevation = 9.dp
                        ) {
                            Row(
                                Modifier.padding(5.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NavButton(Icons.Rounded.Home, "Home", screen == Screen.Home) { screen = Screen.Home }
                                NavButton(Icons.Rounded.CalendarMonth, "Exam calendar", screen == Screen.Calendar) { screen = Screen.Calendar }
                                NavButton(Icons.Rounded.Person, "Profile", screen == Screen.Profile) { screen = Screen.Profile }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastelBackdrop(dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(if (dark) listOf(Color(0xFF403752), Color(0xFF1C1A25)) else listOf(Color(0xFFEDE6F6), Color(0xFFFAF8F4))))
        if (!dark) {
            drawRect(Brush.radialGradient(listOf(Color(0xFFD7C6F0).copy(alpha = .8f), Color.Transparent), Offset(0f, size.height * .08f), size.width * .95f))
            drawRect(Brush.radialGradient(listOf(Color(0xFFDBEDF7), Color.Transparent), Offset(size.width, size.height * .1f), size.width * .85f))
            drawRect(Brush.radialGradient(listOf(Color(0xFFF7DCE8).copy(alpha = .75f), Color.Transparent), Offset(size.width, size.height * .53f), size.width * .7f))
            drawRect(Brush.radialGradient(listOf(Color(0xFFD9EEE6), Color.Transparent), Offset(0f, size.height * .8f), size.width * .9f))
        }
        listOf(Triple(.15f, .31f, 26f), Triple(.84f, .57f, 18f), Triple(.19f, .75f, 10f)).forEach { (x, y, radius) ->
            val center = Offset(size.width * x, size.height * y)
            drawCircle(Color.White.copy(alpha = if (dark) .05f else .35f), radius.dp.toPx(), center)
            drawCircle(Color.White.copy(alpha = .65f), radius.dp.toPx(), center, style = Stroke(1.2.dp.toPx()))
        }
    }
}

@Composable
private fun Kitten(modifier: Modifier = Modifier) {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        offset.animateTo(-8f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    }
    Image(
        painterResource(R.drawable.luna),
        "Luna, your lilac kitten companion",
        modifier.graphicsLayer { translationY = offset.value.dp.toPx() }
    )
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(55.dp))
        Kitten(Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(1f))
        Spacer(Modifier.height(36.dp))
        Text("Hi, I’m Luna", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Caption("A little company for everyday chats and the moments ahead.", centered = true)
        Spacer(Modifier.height(30.dp))
        LunaButton("Continue", onClick = onContinue)
        Spacer(Modifier.height(15.dp))
        Caption("Chat, keep track of exams, and get gentle reminders.", centered = true)
    }
}

@Composable
private fun ChatScreen(chat: ChatModel, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Kitten(Modifier.fillMaxWidth().heightIn(max = if (chat.hasReply || chat.error != null) 255.dp else 390.dp).aspectRatio(1f))
            if (chat.error != null) {
                Text("Luna couldn’t reply", fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Caption(chat.error!!, centered = true)
                LunaButton("Try again", enabled = !chat.busy && chat.draft.isNotBlank()) { chat.send(chat.draft) }
                TextButton(onClick = onSettings) { Text("Connection settings") }
            } else if (chat.hasReply) {
                Surface(
                    shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Text(chat.reply, Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 16.sp, lineHeight = 25.sp)
                }
            }
        }
        Surface(
            shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 14.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .65f)),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Row(Modifier.padding(8.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    chat.draft,
                    { if (it.length <= 2000) chat.draft = it },
                    enabled = !chat.busy,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Message Luna" },
                    decorationBox = { inner ->
                        Box(Modifier.padding(vertical = 12.dp)) {
                            if (chat.draft.isEmpty()) Text("Message…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 21.sp)
                            inner()
                        }
                    }
                )
                if (chat.busy) CircularProgressIndicator(
                    Modifier.padding(12.dp).size(25.dp).semantics { contentDescription = "Luna is thinking" },
                    strokeWidth = 2.dp
                ) else RoundButton(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    "Send message",
                    { chat.send(chat.draft) },
                    accent = true
                )
            }
        }
        Text(
            "An AI companion, not a therapist.",
            Modifier.fillMaxWidth().padding(top = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreen(
    exams: List<LunaExam>,
    onAdd: (String, LocalDate) -> Unit,
    onRemove: (String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var dateMillis by rememberSaveable {
        mutableStateOf(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()) }
    val selectedDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Exam calendar", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Caption("Add your exam dates. Luna will remind you each morning during the final 7 days.")
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80); error = null },
                    label = { Text("Exam name or course") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedDate.format(dateFormatter))
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                LunaButton("Add exam", enabled = title.isNotBlank()) {
                    if (selectedDate < LocalDate.now()) {
                        error = "Choose today or a future date."
                    } else {
                        onAdd(title, selectedDate)
                        title = ""
                        error = null
                    }
                }
            }
        }
        if (exams.isEmpty()) {
            Caption("Your calendar is empty. Add an exam above to keep its date close.")
        } else {
            exams.forEach { exam ->
                val date = LocalDate.parse(exam.date)
                val daysLeft = exam.daysUntil()
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(exam.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text(date.format(dateFormatter), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                when {
                                    daysLeft < 0 -> "Past exam"
                                    daysLeft == 0L -> "Today"
                                    daysLeft == 1L -> "Tomorrow"
                                    else -> "In $daysLeft days"
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = { onRemove(exam.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, "Remove ${exam.title}")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = dateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() >= LocalDate.now()

                override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text("Choose date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun ProfileScreen(
    name: String,
    remindersEnabled: Boolean,
    notificationsAllowed: Boolean,
    dark: Boolean,
    language: String,
    sessionToken: String,
    onNameChange: (String) -> Unit,
    onRemindersChange: (Boolean) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onTokenChange: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 23.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Your settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, onNameChange, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SettingSwitch("Exam and chat reminders", remindersEnabled, onRemindersChange)
        Caption(
            if (notificationsAllowed) {
                "Exam reminders arrive at 9 AM during the final 7 days. A gentle chat reminder is sent at most once a day when you haven’t opened Luna."
            } else {
                "Allow notifications to receive exam reminders at 9 AM during the final 7 days and an occasional gentle chat reminder."
            }
        )
        SettingSwitch("Evening colors", dark, onDarkChange)
        Text("Luna’s reply language", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en" to "English", "fr" to "Français", "ar-TN" to "Derja").forEach { (code, label) ->
                FilterChip(selected = language == code, onClick = { onLanguageChange(code) }, label = { Text(label) })
            }
        }
        Caption("Derja is experimental. Menus are in English.")
        OutlinedTextField(
            sessionToken,
            onTokenChange,
            label = { Text("Session token") },
            supportingText = { Text("From your Luna server. Never enter your OpenRouter key here.") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Caption("Your exam calendar is saved on this device. Chat messages are sent individually to your Luna server.")
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit, accent: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        shadowElevation = 8.dp,
        color = if (accent) Lilac else MaterialTheme.colorScheme.surface,
        border = if (accent) null else BorderStroke(1.dp, Color.White.copy(alpha = .7f)),
        modifier = Modifier.size(51.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(25.dp), tint = if (accent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp).semantics { this.selected = selected; role = Role.Tab }) {
        Icon(
            icon, label,
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
        )
    }
}

@Composable
private fun LunaButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Lilac, contentColor = Color.White),
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Caption(text: String, centered: Boolean = false) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, Modifier.semantics { contentDescription = label })
    }
}
