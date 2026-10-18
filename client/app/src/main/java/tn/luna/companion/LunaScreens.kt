package tn.luna.companion

import android.animation.ValueAnimator
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Help
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
import androidx.compose.ui.platform.LocalDensity
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Lilac = Color(0xFFA593C7)
private val Ink = Color(0xFF35313E)
private val Rose = Color(0xFFD05A87)
private enum class Screen {
    Welcome,
    Home,
    Moments,
    Profile
}
private enum class Sheet {
    Settings,
    Goals,
    Statistics,
    Help,
    Reminder,
    Logout
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunaApp(chat: ChatModel, onDictate: () -> Unit, onSpeak: () -> Unit, onStopSpeaking: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var calm by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("Youssef") }
    // shortcut: goals and check-ins are session-only; add local storage when persistence is needed.
    var goals by rememberSaveable { mutableStateOf(listOf("Drink a glass of water", "Take a little screen break", "Make time for one small step")) }
    var completed by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var checkIns by rememberSaveable { mutableIntStateOf(0) }
    var breaths by rememberSaveable { mutableIntStateOf(0) }
    var mood by rememberSaveable { mutableStateOf("") }
    var newGoal by rememberSaveable { mutableStateOf("") }
    val pager = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val reduceMotion = calm || !ValueAnimator.areAnimatorsEnabled()
    val colors = if (dark) darkColorScheme(primary = Color(0xFFC2ADE7), onPrimary = Ink, background = Color(0xFF211E2B), surface = Color(0xFF383146), onSurface = Color(0xFFF5F0FC), onSurfaceVariant = Color(0xFFC4BACF), secondaryContainer = Color(0xFF4B405F))
        else lightColorScheme(primary = Color(0xFF8C75B8), onPrimary = Color.White, background = Color(0xFFFAF8F6), surface = Color.White.copy(alpha = .86f), onSurface = Ink, onSurfaceVariant = Color(0xFF827A8D), secondaryContainer = Color(0xFFEAE5F3))

    BackHandler(screen != Screen.Welcome && screen != Screen.Home && sheet == null) { screen = Screen.Home }
    MaterialTheme(colorScheme = colors) {
      CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            PastelBackdrop(dark)
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().widthIn(max = 560.dp).align(Alignment.TopCenter)) {
                if (screen == Screen.Welcome) {
                    WelcomeScreen(reduceMotion) { screen = Screen.Home }
                } else {
                    if (screen != Screen.Moments) Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (screen == Screen.Profile) RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Luna", { screen = Screen.Home })
                        Spacer(Modifier.weight(1f))
                        RoundButton(Icons.Rounded.Notifications, "Gentle reminder", { sheet = Sheet.Reminder })
                        Spacer(Modifier.width(10.dp))
                        RoundButton(if (screen == Screen.Profile) Icons.Rounded.Tune else Icons.Rounded.Person, if (screen == Screen.Profile) "Settings" else "Profile", {
                            if (screen == Screen.Profile) sheet = Sheet.Settings else screen = Screen.Profile
                        })
                    }
                    Box(Modifier.weight(1f)) {
                        when (screen) {
                            Screen.Home -> ChatScreen(chat, reduceMotion, onDictate, onSpeak, onStopSpeaking) { sheet = Sheet.Settings }
                            Screen.Profile -> ProfileScreen(name, reduceMotion, { sheet = it })
                            Screen.Moments -> {
                                Column {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        repeat(5) { index -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = if (index <= pager.currentPage) .55f else .14f))) }
                                    }
                                    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                                        MomentPage(page, pager.currentPage == page, reduceMotion, mood,
                                            onMood = { mood = it; checkIns++ }, onBreath = { breaths++ })
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                    Surface(Modifier.padding(horizontal = 23.dp, vertical = 10.dp).fillMaxWidth(), shape = CircleShape, color = colors.surface, shadowElevation = 9.dp) {
                        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                            NavButton(Icons.Rounded.Home, "Home", screen == Screen.Home) { screen = Screen.Home }
                            FilledIconButton(onClick = {
                                if (screen != Screen.Moments) screen = Screen.Moments
                                else scope.launch { if (reduceMotion) pager.scrollToPage((pager.currentPage + 1) % 5) else pager.animateScrollToPage((pager.currentPage + 1) % 5) }
                            }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lilac), modifier = Modifier.size(width = 60.dp, height = 44.dp)) {
                                Icon(Icons.Rounded.ArrowDownward, if (screen == Screen.Moments) "Next moment" else "Explore calming moments", tint = Color.White)
                            }
                            NavButton(Icons.Rounded.Person, "Profile", screen == Screen.Profile) { screen = Screen.Profile }
                        }
                    }
                }
            }
        }
        sheet?.let { current ->
            ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = if (dark) colors.surface else Color(0xFFFCFAFF), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(when (current) { Sheet.Settings -> "Calm mode"; Sheet.Goals -> "My Goals"; Sheet.Statistics -> "Your little wins"; Sheet.Help -> "Help & Support"; Sheet.Reminder -> "A gentle reminder"; Sheet.Logout -> "Start fresh?" }, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    when (current) {
                        Sheet.Settings -> {
                            SettingSwitch("Reduce motion", calm) { calm = it }
                            SettingSwitch("Evening colors", dark) { dark = it }
                            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("Luna’s reply language", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("en" to "English", "fr" to "Français", "ar-TN" to "Derja").forEach { (code, label) -> FilterChip(selected = chat.language == code, onClick = { chat.language = code }, label = { Text(label) }, enabled = !chat.busy) }
                            }
                            Caption("Derja is experimental. Menus are in English.")
                            OutlinedTextField(chat.sessionToken, { chat.sessionToken = it }, label = { Text("Session token") }, supportingText = { Text("From your Luna server. Never enter your OpenRouter key here.") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !chat.busy, modifier = Modifier.fillMaxWidth())
                            Caption("Settings and activity stay in this session. They reset when the app process restarts.")
                        }
                        Sheet.Goals -> {
                            Caption("Small steps count. Pick something kind and manageable.")
                            goals.forEachIndexed { index, goal ->
                                Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox, onClickLabel = "Toggle goal") { completed = if (index in completed) completed - index else completed + index }.padding(vertical = 9.dp).semantics { stateDescription = if (index in completed) "Completed" else "Not completed" }, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (index in completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = colors.primary)
                                    Text(goal, Modifier.padding(start = 13.dp), color = colors.onSurface)
                                }
                            }
                            OutlinedTextField(newGoal, { newGoal = it.take(120) }, label = { Text("One small thing…") }, modifier = Modifier.fillMaxWidth())
                            LunaButton("Add a little goal", enabled = newGoal.isNotBlank() && goals.size < 12) { goals = goals + newGoal.trim(); newGoal = "" }
                        }
                        Sheet.Statistics -> {
                            Caption("This session, at your own pace. No streaks to keep up with.")
                            listOf("Messages shared" to chat.sentMessages, "Mood check-ins" to checkIns, "Breathing moments" to breaths, "Goals completed" to completed.size).forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Text("$value", color = colors.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Sheet.Help -> {
                            Text("A little company for your everyday.", fontWeight = FontWeight.SemiBold)
                            Caption("Luna is an AI well-being companion, not a therapist or emergency service. If you’re struggling, consider reaching out to someone you trust.")
                            Caption("Messages go individually to your Luna server and its AI provider. Previous messages are not sent. Voice dictation uses your device’s speech provider and may use its online service.")
                            Caption("If you’re in immediate danger, contact local emergency services or a trusted person nearby.")
                        }
                        Sheet.Reminder -> {
                            Text("Unclench your shoulders.\nHave a sip of water.\nYou can take a pause.", fontSize = 21.sp, lineHeight = 33.sp)
                            LunaButton("Take a breathing moment") { sheet = null; screen = Screen.Moments; scope.launch { pager.scrollToPage(0) } }
                            Caption("An in-app reminder. No push notifications are scheduled.")
                        }
                        Sheet.Logout -> {
                            Caption("This clears your messages, goals, check-ins and session token from this app session. There is no online account to sign out of.")
                            LunaButton(if (chat.busy) "Wait for Luna’s reply…" else "Log out & start fresh", enabled = !chat.busy) {
                                onStopSpeaking(); chat.resetSession(); completed = emptyList(); goals = listOf("Drink a glass of water", "Take a little screen break", "Make time for one small step"); newGoal = ""; mood = ""; checkIns = 0; breaths = 0; sheet = null; screen = Screen.Welcome
                            }
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
        listOf(Triple(.81f, .3f, Color(0xFFF1D17B)), Triple(.10f, .53f, Color(0xFFE9A6C1))).forEach { (x, y, color) ->
            val cx = size.width * x; val cy = size.height * y; val r = 13.dp.toPx()
            drawPath(Path().apply { moveTo(cx, cy-r); quadraticTo(cx+3, cy-3, cx+r, cy); quadraticTo(cx+3, cy+3, cx, cy+r); quadraticTo(cx-3, cy+3, cx-r, cy); quadraticTo(cx-3, cy-3, cx, cy-r); close() }, color)
        }
    }
}

@Composable
private fun Kitten(modifier: Modifier = Modifier, calm: Boolean = false, scale: Float = 1f, image: Int = R.drawable.luna) {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(calm) {
        if (calm) offset.snapTo(0f)
        else offset.animateTo(-8f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    }
    Image(painterResource(image), "Luna, your lilac kitten companion", modifier.graphicsLayer { translationY = offset.value.dp.toPx(); scaleX = scale; scaleY = scale })
}

@Composable
private fun WelcomeScreen(calm: Boolean, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(55.dp))
        Kitten(Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(1f), calm)
        Spacer(Modifier.height(45.dp))
        Text("Hi, I’m Luna", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Caption("A little company for your everyday.", centered = true)
        Spacer(Modifier.height(30.dp))
        LunaButton("Continue", onClick = onContinue)
        Spacer(Modifier.height(15.dp))
        Caption("Your AI companion, one small moment at a time.", centered = true)
    }
}

@Composable
private fun ChatScreen(chat: ChatModel, calm: Boolean, onDictate: () -> Unit, onSpeak: () -> Unit, onStopSpeaking: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Kitten(Modifier.fillMaxWidth().heightIn(max = if (chat.hasReply || chat.error != null) 255.dp else 390.dp).aspectRatio(1f), calm)
            if (chat.error != null) {
                Text("Luna couldn’t reply", fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Caption(chat.error!!, centered = true)
                LunaButton("Try again", enabled = !chat.busy && chat.draft.isNotBlank()) { chat.send(chat.draft) }
                TextButton(onClick = onSettings) { Text("Connection settings") }
            } else if (chat.hasReply) {
                Surface(shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    Text(chat.reply, Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 16.sp, lineHeight = 25.sp)
                }
            }
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 14.dp, border = BorderStroke(1.dp, Color.White.copy(alpha = .65f)), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(Modifier.padding(8.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(chat.draft, { if (it.length <= 2000) chat.draft = it }, enabled = !chat.busy, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), maxLines = 4, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Message Luna" }, decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 12.dp)) { if (chat.draft.isEmpty()) Text("Message…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 21.sp); inner() }
                })
                if (chat.busy) CircularProgressIndicator(Modifier.padding(12.dp).size(25.dp).semantics { contentDescription = "Luna is thinking" }, strokeWidth = 2.dp)
                else if (chat.draft.isNotBlank()) RoundButton(Icons.AutoMirrored.Rounded.ArrowForward, "Send message", { onStopSpeaking(); chat.send(chat.draft) }, accent = true)
                else {
                    RoundButton(Icons.Rounded.GraphicEq, "Read Luna’s reply aloud or stop playback", onSpeak, accent = true)
                    Spacer(Modifier.width(6.dp))
                    RoundButton(Icons.Rounded.Mic, "Dictate a message", onDictate, accent = true)
                }
            }
        }
        Text("An AI companion, not a therapist.", Modifier.fillMaxWidth().padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfileScreen(name: String, calm: Boolean, onSheet: (Sheet) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 23.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 16.dp).size(155.dp).background(Color.White.copy(alpha = .22f), CircleShape).padding(13.dp).border(5.dp, Color.White.copy(alpha = .85f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, null, Modifier.size(65.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(name.ifBlank { "Friend" }, Modifier.padding(top = 16.dp), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Keep going!", Modifier.padding(top = 6.dp, bottom = 35.dp), fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 7.dp) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                ProfileRow(Icons.Rounded.TrackChanges, "My Goals") { onSheet(Sheet.Goals) }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
                ProfileRow(Icons.Rounded.BarChart, "Statistics") { onSheet(Sheet.Statistics) }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
                ProfileRow(Icons.Rounded.LightMode, "Settings") { onSheet(Sheet.Settings) }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
                ProfileRow(Icons.AutoMirrored.Rounded.Help, "Help & Support") { onSheet(Sheet.Help) }
            }
        }
        Box(Modifier.fillMaxWidth().height(102.dp).clip(RoundedCornerShape(0.dp))) { Kitten(Modifier.padding(start = 30.dp, top = 6.dp).size(175.dp), calm) }
        Surface(onClick = { onSheet(Sheet.Logout) }, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(19.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = Rose); Spacer(Modifier.width(10.dp)); Text("Log out", color = Rose, fontWeight = FontWeight.Bold, fontSize = 21.sp) }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick, role = Role.Button).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Text(label, Modifier.weight(1f).padding(start = 16.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MomentPage(page: Int, active: Boolean, calm: Boolean, mood: String, onMood: (String) -> Unit, onBreath: () -> Unit) {
    var breathing by rememberSaveable { mutableStateOf(false) }
    var breathStep by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { breathing = false }
    LaunchedEffect(breathing, active) {
        if (!active) { breathing = false; return@LaunchedEffect }
        if (breathing) {
            repeat(6) { breathStep = it; delay(4000) }
            breathing = false; onBreath()
        }
    }
    val scale by animateFloatAsState(if (breathing && breathStep % 2 == 0 && !calm) 1.08f else 1f, tween(if (calm) 0 else 3900), label = "Breathing")
    Column(Modifier.fillMaxSize().padding(horizontal = 25.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(45.dp))
        Box(contentAlignment = Alignment.Center) {
            if (page == 0) Box(Modifier.size(245.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f), CircleShape))
            Kitten(Modifier.fillMaxWidth().heightIn(max = 320.dp).aspectRatio(1f), calm || !active, if (page == 0) scale else 1f,
                image = when (page) { 0 -> R.drawable.luna_calm; 4 -> R.drawable.luna_love; else -> R.drawable.luna })
        }
        Spacer(Modifier.height(22.dp))
        when (page) {
            0 -> LunaButton(if (breathing) "Finish for now" else "Take three gentle breaths") { breathStep = 0; breathing = !breathing }
            1 -> SlideHello()
            2 -> DragMoment(calm)
            3 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    listOf(Triple(Icons.Rounded.Check, "Okay", "Feeling okay"), Triple(Icons.Rounded.Close, "Low", "Feeling low"), Triple(Icons.Rounded.Favorite, "Good", "Feeling good")).forEach { (icon, value, label) -> RoundButton(icon, label, { onMood(value) }, accent = mood == value) }
                }
                if (mood.isNotBlank()) Caption(if (mood == "Low") "You don’t have to figure it all out today." else "Thank you for checking in with yourself.", centered = true)
            }
            4 -> Caption("There’s room for you, exactly as you are.", centered = true)
        }
        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth()) {
            Text("@luna", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(when (page) { 0 -> if (!breathing || breathStep % 2 == 0) "Breathe in." else "Breathe out."; 1 -> "Slide to say hi."; 2 -> "Drag it over."; 3 -> "How do you feel?"; else -> "You’re loved." }, fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text(if (page == 0) "Gently. Four seconds in, four seconds out." else "Swipe up for a little more kindness", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        }
    }
}

@Composable
private fun SlideHello() {
    var progress by remember { mutableFloatStateOf(0f) }
    var greeted by rememberSaveable { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 260.dp)) {
            Slider(value = progress, onValueChange = { progress = it; greeted = false }, onValueChangeFinished = { greeted = progress >= .8f; progress = 0f }, modifier = Modifier.padding(horizontal = 12.dp).semantics { contentDescription = "Slide to say hello to Luna" }, colors = SliderDefaults.colors(thumbColor = Lilac, activeTrackColor = Lilac, inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer))
        }
        TextButton(onClick = { greeted = true }) { Text(if (greeted) "Hi, friend! A little wave back ♡" else "Or tap to say hi") }
    }
}

@Composable
private fun DragMoment(calm: Boolean) {
    val density = LocalDensity.current
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var done by rememberSaveable { mutableStateOf(false) }
    val animated by animateFloatAsState(offset, if (dragging || calm) snap() else spring(dampingRatio = .65f), label = "Drag spring")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.width(260.dp).height(64.dp)) {
            val travel = with(LocalDensity.current) { (maxWidth - 48.dp).toPx() }
            Box(Modifier.align(Alignment.CenterEnd).size(48.dp).border(1.dp, Lilac, RoundedCornerShape(15.dp)))
            Box(Modifier.align(Alignment.CenterStart).offset { androidx.compose.ui.unit.IntOffset(animated.roundToInt(), 0) }.size(48.dp).shadow(8.dp, RoundedCornerShape(15.dp)).background(Lilac, RoundedCornerShape(15.dp)).draggable(
                state = rememberDraggableState { delta -> offset = (offset + delta).coerceIn(0f, travel) }, orientation = Orientation.Horizontal,
                onDragStarted = { dragging = true; done = false }, onDragStopped = { dragging = false; done = offset >= travel * .8f; offset = if (done) travel else 0f }
            ).semantics { contentDescription = "Drag the lilac block into the outline" }, contentAlignment = Alignment.Center) { if (done) Icon(Icons.Rounded.Check, null, tint = Color.White) }
        }
        TextButton(onClick = { done = !done; offset = if (done) with(density) { 212.dp.toPx() } else 0f }) { Text(if (done) "You did it. Try again?" else "Or tap to place it") }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit, accent: Boolean = false) {
    Surface(onClick = onClick, shape = CircleShape, shadowElevation = 8.dp, color = if (accent) Lilac else MaterialTheme.colorScheme.surface, border = if (accent) null else BorderStroke(1.dp, Color.White.copy(alpha = .7f)), modifier = Modifier.size(51.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(25.dp), tint = if (accent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun NavButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp).semantics { this.selected = selected; role = Role.Tab }) { Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)) }
}

@Composable
private fun LunaButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Lilac, contentColor = Color.White), shape = CircleShape, elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}

@Composable
private fun Caption(text: String, centered: Boolean = false) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp, textAlign = if (centered) TextAlign.Center else TextAlign.Start) }

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange, Modifier.semantics { contentDescription = label }) }
}
