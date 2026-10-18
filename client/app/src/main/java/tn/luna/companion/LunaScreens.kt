package tn.luna.companion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Lilac = Color(0xFFA593C7)
private val Ink = Color(0xFF35313E)
private val CalendarRed = Color(0xFFE34C4C)
private enum class Screen { Welcome, Home, Profile }

internal fun calendarWeeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
    val first = month.atDay(1)
    val leadingDays = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cellCount = ((leadingDays + month.lengthOfMonth() + 6) / 7) * 7
    return (0 until cellCount).chunked(7).map { week ->
        week.map { index ->
            val day = index - leadingDays + 1
            if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunaApp(chat: ChatModel, onDictate: () -> Unit) {
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

    BackHandler(screen == Screen.Profile) { screen = Screen.Home }
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
                            Text(
                                if (screen == Screen.Profile) "Settings" else "Luna",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                screen = if (screen == Screen.Profile) Screen.Home else Screen.Profile
                            }) {
                                Text(if (screen == Screen.Profile) "Done" else "Settings")
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            when (screen) {
                                Screen.Home -> ChatScreen(
                                    chat = chat,
                                    exams = exams.value,
                                    onDictate = onDictate,
                                    onSettings = { screen = Screen.Profile },
                                    onAddExam = { title, date ->
                                        val exam = examStore.add(title, date)
                                        exams.value = examStore.all().sortedBy { it.date }
                                        ReminderScheduler.scheduleExam(context, exam)
                                    },
                                    onRemoveExam = { id ->
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
private fun ChatScreen(
    chat: ChatModel,
    exams: List<LunaExam>,
    onDictate: () -> Unit,
    onSettings: () -> Unit,
    onAddExam: (String, LocalDate) -> Unit,
    onRemoveExam: (String) -> Unit
) {
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
                ) else {
                    if (chat.draft.isBlank()) {
                        RoundButton(Icons.Rounded.Mic, "Dictate a message", onDictate)
                    } else {
                        RoundButton(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            "Send message",
                            { chat.send(chat.draft) },
                            accent = true
                        )
                    }
                }
            }
        }
        ExamCalendar(exams, onAddExam, onRemoveExam)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamCalendar(
    exams: List<LunaExam>,
    onAdd: (String, LocalDate) -> Unit,
    onRemove: (String) -> Unit
) {
    val today = LocalDate.now()
    var selectedDateText by rememberSaveable { mutableStateOf(today.toString()) }
    var displayedMonthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var eventDateText by rememberSaveable { mutableStateOf(today.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedDate = LocalDate.parse(selectedDateText)
    val displayedMonth = YearMonth.parse(displayedMonthText)
    val locale = Locale.getDefault()
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE, MMMM d", locale) }
    val firstDayOfWeek = remember(locale) { java.time.temporal.WeekFields.of(locale).firstDayOfWeek }
    val weeks = remember(displayedMonth, firstDayOfWeek) { calendarWeeks(displayedMonth, firstDayOfWeek) }
    val weekdayLabels = remember(firstDayOfWeek, locale) {
        (0..6).map { firstDayOfWeek.plus(it.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, locale) }
    }
    val dayExams = exams.filter { it.date == selectedDate.toString() }
    val cardColor = MaterialTheme.colorScheme.surface

    Column(Modifier.fillMaxWidth().heightIn(max = 385.dp).verticalScroll(rememberScrollState())) {
        Surface(
            shape = RoundedCornerShape(25.dp),
            color = cardColor,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .68f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Calendar", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "EXAMS",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            title = ""
                            eventDateText = selectedDate.toString()
                            error = null
                            showAddDialog = true
                        },
                        modifier = Modifier.size(38.dp).semantics { contentDescription = "Add exam" }
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = CalendarRed)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayedMonth.format(monthFormatter).replaceFirstChar { it.titlecase(locale) },
                        Modifier.weight(1f),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { displayedMonthText = displayedMonth.minusMonths(1).toString() },
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Previous month" }
                    ) { Icon(Icons.Rounded.ChevronLeft, null) }
                    IconButton(
                        onClick = { displayedMonthText = displayedMonth.plusMonths(1).toString() },
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Next month" }
                    ) { Icon(Icons.Rounded.ChevronRight, null) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 2.dp)) {
                    weekdayLabels.forEach { weekday ->
                        Text(
                            weekday,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            Box(
                                Modifier.weight(1f).height(34.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (date != null) {
                                    val isSelected = date == selectedDate
                                    val isToday = date == today
                                    val hasExam = exams.any { it.date == date.toString() }
                                    Column(
                                        Modifier.size(33.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) CalendarRed else Color.Transparent)
                                            .then(
                                                if (isToday && !isSelected) Modifier.border(1.dp, CalendarRed, CircleShape)
                                                else Modifier
                                            )
                                            .clickable {
                                                selectedDateText = date.toString()
                                                displayedMonthText = YearMonth.from(date).toString()
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> CalendarRed
                                                date.month == displayedMonth.month -> MaterialTheme.colorScheme.onSurface
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            lineHeight = 15.sp
                                        )
                                        Box(
                                            Modifier.size(4.dp).clip(CircleShape).background(
                                                if (hasExam && !isSelected) CalendarRed else Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(
                    Modifier.padding(vertical = 7.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedDate.format(dayFormatter).replaceFirstChar { it.titlecase(locale) },
                        Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (dayExams.isEmpty()) {
                        Text("No exams", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                dayExams.forEach { exam ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(CalendarRed))
                        Text(exam.title, Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp)
                        IconButton(
                            onClick = { onRemove(exam.id) },
                            modifier = Modifier.size(30.dp).semantics { contentDescription = "Remove ${exam.title}" }
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Text(
                    "An AI companion, not a therapist.",
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add an exam") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(80); error = null },
                        label = { Text("Exam or course name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null)
                        Spacer(Modifier.width(8.dp))
                        Text(LocalDate.parse(eventDateText).format(dayFormatter))
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        val date = LocalDate.parse(eventDateText)
                        if (date < LocalDate.now()) {
                            error = "Choose today or a future date."
                        } else {
                            onAdd(title.trim(), date)
                            selectedDateText = date.toString()
                            displayedMonthText = YearMonth.from(date).toString()
                            showAddDialog = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
    if (showDatePicker) {
        val initialDate = LocalDate.parse(eventDateText).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initialDate,
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
                    picker.selectedDateMillis?.let {
                        eventDateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
            modifier = Modifier
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
    val context = LocalContext.current
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
        if (remindersEnabled && !notificationsAllowed) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) {
                Text("Open notification settings")
            }
        }
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
