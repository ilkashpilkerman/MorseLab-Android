package sh.klattergetz.morzelab

import android.os.Bundle
import androidx.compose.ui.platform.LocalContext
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Background = Color(0xFF080E1D)
private val Panel = Color(0xFF111C32)
private val Mint = Color(0xFF55F2AD)
private val Coral = Color(0xFFFF795F)
private val Gold = Color(0xFFFFD186)
private val Muted = Color(0xFF93A7C1)
private val White = Color(0xFFF4F8FF)

private enum class Alphabet(val label: String) { RU("Русский"), EN("English") }

private enum class Mode(val label: String) {
    FREE("Свободно"), TARGET("Кодируй"), LISTEN("Слушай"), BLIND("Без схемы"), SPEED("На скорость")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MorseApp() }
    }
}

@Composable
private fun MorseApp() {
    val tone = remember { MorseTone() }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("morse_settings", android.content.Context.MODE_PRIVATE) }
    var alphabet by remember { mutableStateOf(if (prefs.getString("alphabet", "RU") == "EN") Alphabet.EN else Alphabet.RU) }
    val activeCodes = if (alphabet == Alphabet.RU) MorseEngine.russianCodes else MorseEngine.codes
    val trainingLetters = activeCodes.keys.filter { it.isLetter() && it != 'Ё' }
    var mode by remember { mutableStateOf(Mode.FREE) }
    var wpm by remember { mutableIntStateOf(15) }
    var frequency by remember { mutableFloatStateOf(700f) }
    var code by remember { mutableStateOf("") }
    var lastCompletedCode by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("—") }
    var transcript by remember { mutableStateOf("") }
    var score by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(trainingLetters.random()) }
    var held by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var lastSignal by remember { mutableLongStateOf(0L) }
    var settingsOpen by remember { mutableStateOf(false) }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var showDigits by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(60) }
    var playback by remember { mutableStateOf(false) }
    // Standard PARIS timing: dot=1200/WPM ms, dash=3 dots, character gap=3 dots.
    val dotMs = 1200L / wpm
    tone.frequencyHz = frequency.toInt()

    fun finishLetter() {
        if (code.isEmpty()) return
        val letter = MorseEngine.decode(code, alphabet == Alphabet.RU)
        result = letter?.toString() ?: "?"
        transcript += result
        lastCompletedCode = code
        if (mode == Mode.TARGET || mode == Mode.BLIND || mode == Mode.SPEED) {
            attempts++
            if (letter == target) score++
            target = trainingLetters.random()
        }
        code = ""
    }

    fun addSignal(signal: Char) {
        if (playback) return
        val next = code + signal
        if (next.length > 5 || !activeCodes.values.any { it.startsWith(next) }) {
            result = "Неверная ветка"
            code = ""
        } else {
            code = next
            result = MorseEngine.decode(code, alphabet == Alphabet.RU)?.toString() ?: "…"
        }
        lastSignal = SystemClock.elapsedRealtime()
    }

    LaunchedEffect(code, lastSignal, wpm, playback, held) {
        if (code.isNotEmpty() && !playback) {
            val stamp = lastSignal
            delay(dotMs * 3)
            if (stamp == lastSignal && !held) finishLetter()
        }
    }
    // Held-key race guard: after releasing, the timeout above is launched by lastSignal change.
    LaunchedEffect(mode, alphabet) {
        code = ""; lastCompletedCode = ""; result = "—"; transcript = ""; score = 0; attempts = 0
        secondsRemaining = 60; target = trainingLetters.random()
    }
    LaunchedEffect(mode) {
        if (mode == Mode.SPEED) {
            while (secondsRemaining > 0) { delay(1000); secondsRemaining-- }
        }
    }

    suspend fun playTarget() {
        if (playback || held) return
        playback = true
        tone.stop()
        delay(80)
        val targetCode = activeCodes[target] ?: ""
        for (signal in targetCode) {
            tone.start()
            delay(dotMs * if (signal == '.') 1 else 3)
            tone.stop()
            delay(dotMs)
        }
        playback = false
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, surface = Panel, background = Background)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Background)
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact header: last signal, decoded letter, mode and settings.
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (mode == Mode.FREE) "СИГНАЛ" else if (mode == Mode.LISTEN) "СЛУШАЙ" else "ЗАДАНИЕ",
                        color = Muted, fontSize = 10.sp
                    )
                    Text(
                        if (mode == Mode.FREE) code.ifEmpty { lastCompletedCode.ifEmpty { "···" } }
                        else if (mode == Mode.LISTEN) "?" else target.toString(),
                        color = Gold, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                        maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("БУКВА", color = Muted, fontSize = 10.sp)
                    Text(result, color = Mint, fontWeight = FontWeight.Bold,
                        fontSize = 24.sp, maxLines = 1)
                }
                Spacer(Modifier.width(6.dp))
                Box {
                    TextButton(onClick = { modeMenuOpen = true }) {
                        Text("${mode.label} ▾", color = Mint, fontSize = 12.sp,
                            maxLines = 1)
                    }
                    DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                        Mode.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = { mode = item; modeMenuOpen = false }
                            )
                        }
                    }
                }
                IconButton(onClick = { settingsOpen = true }, modifier = Modifier.size(40.dp)) {
                    Text("⚙", color = White, fontSize = 23.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ТЕКСТ:", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                Text(transcript.takeLast(40).ifEmpty { "—" },
                    color = White, fontSize = 13.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (mode == Mode.SPEED) Text("${secondsRemaining}с · $score/$attempts", color = Gold, fontSize = 11.sp)
                if (mode == Mode.TARGET || mode == Mode.BLIND) Text("$score/$attempts", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(5.dp))
            if (mode != Mode.BLIND && mode != Mode.LISTEN) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(6.dp)) {
                        // Diagram takes all remaining space and never scrolls.
                        MorseBoard(code = code, showDigits = showDigits, alphabet = alphabet,
                            modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(if (mode == Mode.LISTEN) "♫  СЛУШАЙ СИГНАЛ" else "◉  БЕЗ ПОДСКАЗОК",
                        color = Muted, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(7.dp))
            if (mode == Mode.LISTEN) {
                Button(onClick = { scope.launch { playTarget() } }, enabled = !playback,
                    modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("▶ ПРОСЛУШАТЬ") }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val choices = remember(target) {
                        (listOf(target) + trainingLetters.filter { it != target }
                            .shuffled().take(3)).shuffled()
                    }
                    choices.forEach { ch ->
                        OutlinedButton(onClick = {
                            attempts++
                            if (ch == target) score++
                            result = if (ch == target) "Верно!" else "Было $target"
                            target = trainingLetters.random()
                        }, modifier = Modifier.weight(1f)) { Text(ch.toString()) }
                    }
                }
                Text("Правильно: $score из $attempts", color = Muted, fontSize = 12.sp)
            } else {
                val enabled = mode != Mode.SPEED || secondsRemaining > 0
                Row(
                    modifier = Modifier.fillMaxWidth().height(116.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TextButton(onClick = {
                            code = ""; lastCompletedCode = ""; result = "—"; transcript = ""
                        }) { Text("Сброс", fontSize = 12.sp, maxLines = 1) }
                    }
                    Box(
                        modifier = Modifier.size(112.dp)
                            .background(if (held) Coral else Mint, RoundedCornerShape(60.dp))
                            .pointerInput(enabled, dotMs, playback) {
                                if (enabled && !playback) detectTapGestures(onPress = {
                                    held = true
                                    startTime = SystemClock.elapsedRealtime()
                                    tone.start()
                                    val released = tryAwaitRelease()
                                    tone.stop()
                                    val duration = SystemClock.elapsedRealtime() - startTime
                                    held = false
                                    if (released) addSignal(if (duration < dotMs * 2) '.' else '-')
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (held) "●" else "KEY", fontSize = 27.sp,
                            fontWeight = FontWeight.Black, color = Background)
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { finishLetter() }) {
                                Text("Готово", fontSize = 12.sp, maxLines = 1)
                            }
                            if (mode == Mode.TARGET || mode == Mode.BLIND) {
                                TextButton(onClick = { target = trainingLetters.random(); code = "" }) {
                                    Text("Пропуск", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

        }
        if (settingsOpen) {
            AlertDialog(
                onDismissRequest = { settingsOpen = false },
                title = { Text("Настройки тренажёра") },
                text = {
                    Column {
                        Text("Алфавит", color = Muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Alphabet.entries.forEach { language ->
                                FilterChip(
                                    selected = alphabet == language,
                                    onClick = {
                                        alphabet = language
                                        prefs.edit().putString("alphabet", language.name).apply()
                                    },
                                    label = { Text(language.label) }
                                )
                            }
                        }
                        Text("Скорость: $wpm WPM")
                        Slider(value = wpm.toFloat(), onValueChange = { wpm = it.toInt() },
                            valueRange = 5f..35f)
                        Text("Частота тона: ${frequency.toInt()} Hz")
                        Slider(value = frequency, onValueChange = { frequency = it },
                            valueRange = 400f..1000f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Показывать цифры", modifier = Modifier.weight(1f))
                            Switch(checked = showDigits, onCheckedChange = { showDigits = it })
                        }
                        Text("Точка = 1200 / WPM мс. Тире = 3 точки.",
                            color = Muted, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { settingsOpen = false }) { Text("Готово") }
                }
            )
        }
    }
}

/** Compact board geometry is inspired by the physical Morse card, not a wide binary tree. */
private val boardPoints = mapOf(
    "" to Pair(.46f, .10f),
    "." to Pair(.57f, .10f), "-" to Pair(.35f, .10f),
    ".." to Pair(.69f, .10f), ".-" to Pair(.57f, .56f),
    "-." to Pair(.35f, .49f), "--" to Pair(.22f, .10f),
    "..." to Pair(.81f, .10f), "..-" to Pair(.69f, .23f),
    ".-." to Pair(.70f, .56f), ".--" to Pair(.57f, .75f),
    "-.." to Pair(.35f, .75f), "-.-" to Pair(.22f, .49f),
    "--." to Pair(.22f, .29f), "---" to Pair(.09f, .10f),
    "...." to Pair(.93f, .10f), "...-" to Pair(.81f, .29f),
    "..-." to Pair(.69f, .37f), ".-.." to Pair(.84f, .56f),
    ".--." to Pair(.69f, .75f), ".---" to Pair(.57f, .90f),
    "-..." to Pair(.35f, .90f), "-..-" to Pair(.22f, .75f),
    "-.-." to Pair(.22f, .64f), "-.--" to Pair(.09f, .49f),
    "--.." to Pair(.22f, .39f),
    "....." to Pair(.92f, .22f), "....-" to Pair(.90f, .32f),
    "...--" to Pair(.85f, .40f), "..---" to Pair(.80f, .45f),
    ".----" to Pair(.70f, .91f), "-...." to Pair(.42f, .95f),
    "--..." to Pair(.16f, .95f), "---.." to Pair(.07f, .75f),
    "----." to Pair(.07f, .85f), "-----" to Pair(.07f, .95f),
    // Additional nodes used by the Russian alphabet.
    "---." to Pair(.09f, .21f), "----" to Pair(.055f, .35f),
    "..--" to Pair(.90f, .37f), ".-.-" to Pair(.70f, .68f),
    "..-.." to Pair(.69f, .45f), "--.-" to Pair(.105f, .29f), "--.--" to Pair(.105f, .42f)
)

@Composable
private fun MorseBoard(code: String, showDigits: Boolean, alphabet: Alphabet, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val scale = minOf(w, h * .78f)
        val radius = (scale * .029f).coerceAtLeast(6.dp.toPx())
        fun pos(path: String): Offset {
            val p = boardPoints.getValue(path)
            return Offset(p.first * w, p.second * h)
        }
        val codes = if (alphabet == Alphabet.RU) MorseEngine.russianCodes else MorseEngine.codes
        val paths = codes.filterKeys { showDigits || it.isLetter() }
            .entries.map { it.value }.toSet()
        val digitPaths = if (showDigits) codes.filterKeys { it.isDigit() }
            .values.toSet() else emptySet()
        val baseWire = Color(0xFF435777)
        val digitWire = Color(0xFFA99ADA) // purple: digit-only continuations
        val digitNode = Color(0xFF493D67)

        paths.forEach { path ->
            if (path == "..--") return@forEach // special У -> Ю routing below
            val parentPath = path.dropLast(1)
            if (parentPath.isNotEmpty() && parentPath !in paths) return@forEach
            val parent = pos(parentPath)
            val child = pos(path)
            val active = code.startsWith(path)
            val isDigitOnly = path in digitPaths
            val ink = if (active) Mint else if (isDigitOnly) digitWire else baseWire
            val stroke = if (active) 3.dp.toPx() else if (isDigitOnly) 2.dp.toPx() else 1.5.dp.toPx()

            when (path) {
                // Ч (---.) -> Ш (----): goes LEFT, then DOWN,
                // completely bypassing Г -> Щ and Щ -> Ъ.
                "----" -> {
                    val track = Path().apply {
                        moveTo(parent.x, parent.y)
                        lineTo(child.x, parent.y)
                        lineTo(child.x, child.y)
                    }
                    drawPath(track, ink, style = Stroke(width = stroke, cap = StrokeCap.Round))
                }
                // Щ (--.-) -> Ъ (--.--): a separate vertical lane,
                // not shared with Ч -> Ш.
                "--.--" -> drawLine(ink, parent, child, strokeWidth = stroke)
                else -> {
                    if (kotlin.math.abs(parent.y - child.y) < h * .025f ||
                        kotlin.math.abs(parent.x - child.x) < w * .025f) {
                        drawLine(ink, parent, child, strokeWidth = stroke)
                    } else {
                        val junction = Offset(parent.x, child.y)
                        drawLine(ink, parent, junction, strokeWidth = stroke)
                        drawLine(ink, junction, child, strokeWidth = stroke)
                    }
                }
            }
        }
        // Russian Ю (..--): run horizontally from У, then vertically down to Ю.
        // A small break in the crossed С → Ж wire keeps the paths unambiguous.
        if ("..--" in paths) {
            val from = pos("..-")   // У
            val to = pos("..--")    // Ю
            val crossingX = pos("...").x // С → Ж vertical line
            val active = code.startsWith("..--")
            val trackColor = if (active) Mint else Color(0xFF435777)
            val trackWidth = if (active) 3.dp.toPx() else 1.5.dp.toPx()
            val gap = 5.dp.toPx()

            // Visually interrupt only the crossed line, not the У → Ю connection.
            drawLine(
                Panel,
                Offset(crossingX, from.y - gap),
                Offset(crossingX, from.y + gap),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            val track = Path().apply {
                moveTo(from.x, from.y)
                lineTo(to.x, from.y)
                lineTo(to.x, to.y)
            }
            drawPath(
                track,
                trackColor,
                style = Stroke(
                    width = trackWidth,
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
        paths.forEach { path ->
            val center = pos(path)
            val active = code.startsWith(path)
            val dot = path.last() == '.'
            val fill = if (active) (if (dot) Mint else Coral) else if (path in digitPaths) digitNode else Color(0xFF26384F)
            if (active) drawCircle(fill.copy(alpha = .20f), radius * 1.60f, center)
            if (dot) {
                drawCircle(fill, radius, center)
                drawCircle(if (active) White else if (path in digitPaths) digitWire else Muted, radius, center,
                    style = Stroke(1.dp.toPx()))
            } else {
                drawRect(fill, Offset(center.x - radius, center.y - radius * .77f),
                    Size(radius * 2f, radius * 1.54f))
                drawRect(if (active) White else if (path in digitPaths) digitWire else Muted,
                    Offset(center.x - radius, center.y - radius * .77f),
                    Size(radius * 2f, radius * 1.54f), style = Stroke(1.dp.toPx()))
            }
            val letter = MorseEngine.decode(path, alphabet == Alphabet.RU) ?: return@forEach
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (path in digitPaths && !active) android.graphics.Color.rgb(209, 195, 255) else android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = (radius * .89f).coerceIn(9.sp.toPx(), 15.sp.toPx())
                    isFakeBoldText = active
                }
                drawText(letter.toString(), center.x, center.y - radius - 4.dp.toPx(), paint)
            }
        }
        val root = pos("")
        drawCircle(Gold, radius * .30f, root)
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.YELLOW
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = radius * 1.5f
            }
            drawText("▼", root.x, root.y - radius, paint)
        }
    }
}
