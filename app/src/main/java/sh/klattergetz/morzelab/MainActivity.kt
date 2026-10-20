package sh.klattergetz.morzelab

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
    var mode by remember { mutableStateOf(Mode.FREE) }
    var wpm by remember { mutableIntStateOf(15) }
    var frequency by remember { mutableFloatStateOf(700f) }
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("—") }
    var transcript by remember { mutableStateOf("") }
    var score by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(MorseEngine.codes.keys.random()) }
    var held by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var lastSignal by remember { mutableLongStateOf(0L) }
    var clock by remember { mutableLongStateOf(0L) }
    var secondsRemaining by remember { mutableIntStateOf(60) }
    var playback by remember { mutableStateOf(false) }
    // Standard PARIS timing: dot=1200/WPM ms, dash=3 dots, character gap=3 dots.
    val dotMs = 1200L / wpm
    tone.frequencyHz = frequency.toInt()

    fun finishLetter() {
        if (code.isEmpty()) return
        val letter = MorseEngine.decode(code)
        result = letter?.toString() ?: "?"
        transcript += result
        if (mode == Mode.TARGET || mode == Mode.BLIND || mode == Mode.SPEED) {
            attempts++
            if (letter == target) score++
            target = MorseEngine.codes.keys.random()
        }
        code = ""
    }

    fun addSignal(signal: Char) {
        if (playback) return
        val next = code + signal
        if (next.length > 5 || !MorseEngine.codes.values.any { it.startsWith(next) }) {
            result = "Неверная ветка"
            code = ""
        } else {
            code = next
            result = MorseEngine.decode(code)?.toString() ?: "…"
        }
        lastSignal = SystemClock.elapsedRealtime()
    }

    LaunchedEffect(code, lastSignal, wpm, playback) {
        if (code.isNotEmpty() && !playback) {
            val stamp = lastSignal
            delay(dotMs * 3)
            if (stamp == lastSignal && !held) finishLetter()
        }
    }
    // Held-key race guard: after releasing, the timeout above is launched by lastSignal change.
    LaunchedEffect(mode) {
        code = ""; result = "—"; transcript = ""; score = 0; attempts = 0
        secondsRemaining = 60; target = MorseEngine.codes.keys.random()
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
        val targetCode = MorseEngine.codes[target] ?: ""
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
            Modifier.fillMaxSize().background(Background).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Text("MORSE / LAB", color = White, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 4.sp)
            Text("СЛУШАЙ  •  НАЖИМАЙ  •  ЗАПОМИНАЙ", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { item ->
                    FilterChip(selected = item == mode, onClick = { mode = item }, label = { Text(item.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(when (mode) {
                        Mode.FREE -> "ТВОЙ СИГНАЛ"
                        Mode.LISTEN -> "УЗНАЙ НА СЛУХ"
                        else -> "ПЕРЕДАЙ БУКВУ"
                    }, color = Muted, fontSize = 11.sp)
                    Text(if (mode == Mode.FREE) "${if (code.isEmpty()) "· · ·" else code}" else if (mode == Mode.LISTEN) "?" else "$target", color = Gold, fontSize = 35.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("РАСШИФРОВКА", color = Muted, fontSize = 11.sp)
                    Text(result, color = Mint, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (mode == Mode.SPEED) Text("Время: $secondsRemaining с   •   Счёт: $score/$attempts", color = Gold)
            if (mode == Mode.TARGET || mode == Mode.BLIND) Text("Правильно: $score из $attempts", color = Muted)
            Spacer(Modifier.height(8.dp))
            if (mode != Mode.BLIND && mode != Mode.LISTEN) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize().padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ДЕРЕВО МОРЗЕ", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("● точка    ▬ тире", color = Muted, fontSize = 10.sp)
                        }
                        MorseTree(code = code, modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Text(if (mode == Mode.LISTEN) "♫\nПрослушай сигнал и выбери букву" else "◉\nОриентируйся на звук", color = Muted, textAlign = TextAlign.Center, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (mode == Mode.LISTEN) {
                Button(onClick = { scope.launch { playTarget() } }, enabled = !playback, modifier = Modifier.fillMaxWidth()) { Text("▶  ПРОСЛУШАТЬ") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val choices = remember(target) { (listOf(target) + MorseEngine.codes.keys.filter { it != target }.shuffled().take(3)).shuffled() }
                    choices.forEach { ch ->
                        OutlinedButton(onClick = {
                            attempts++
                            if (ch == target) score++
                            result = if (ch == target) "Верно!" else "Было $target"
                            target = MorseEngine.codes.keys.random()
                        }, modifier = Modifier.weight(1f)) { Text(ch.toString()) }
                    }
                }
                Text("Правильно: $score из $attempts", color = Muted)
            } else {
                Text("УДЕРЖИВАЙ КНОПКУ • коротко = · • долго = −", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                val enabled = mode != Mode.SPEED || secondsRemaining > 0
                Box(
                    Modifier.size(148.dp).background(if (held) Coral else Mint, RoundedCornerShape(75.dp))
                        .pointerInput(enabled, dotMs, playback, code) {
                            if (enabled && !playback) detectTapGestures(onPress = {
                                held = true
                                startTime = SystemClock.elapsedRealtime()
                                tone.start()
                                tryAwaitRelease()
                                tone.stop()
                                val duration = SystemClock.elapsedRealtime() - startTime
                                held = false
                                addSignal(if (duration < dotMs * 2) '.' else '-')
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (held) "●" else "KEY", fontSize = 33.sp, fontWeight = FontWeight.Black, color = Background)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { code = ""; result = "—" }) { Text("Сброс") }
                    OutlinedButton(onClick = { finishLetter() }) { Text("Завершить букву") }
                    if (mode == Mode.TARGET || mode == Mode.BLIND) OutlinedButton(onClick = { target = MorseEngine.codes.keys.random(); code = "" }) { Text("Пропустить") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Текст: ${transcript.takeLast(32)}", color = White, maxLines = 1)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${wpm} WPM", color = Muted, fontSize = 12.sp)
                Slider(value = wpm.toFloat(), onValueChange = { wpm = it.toInt().coerceIn(5, 35) }, valueRange = 5f..35f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${frequency.toInt()} Hz", color = Muted, fontSize = 12.sp)
                Slider(value = frequency, onValueChange = { frequency = it }, valueRange = 400f..1000f, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Position is derived from binary tree index, never from a hard-coded UI label chart. */
@Composable
private fun MorseTree(code: String, modifier: Modifier = Modifier) {
    // Five levels: last row includes numbers. A horizontally scrollable chart keeps nodes legible.
    BoxWithConstraints(modifier) {
        val width = maxOf(maxWidth, 920.dp)
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
            Canvas(Modifier.width(width).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                val gapY = h / 6f
                fun position(path: String): Offset {
                    var index = 0
                    path.forEach { index = index * 2 + if (it == '.') 0 else 1 }
                    val slots = 1 shl path.length
                    return Offset((index + .5f) * w / slots, (path.length + .48f) * gapY)
                }
                for (depth in 1..5) {
                    MorseEngine.codes.values.filter { it.length == depth }.forEach { path ->
                        val parent = position(path.dropLast(1))
                        val child = position(path)
                        val selected = code.startsWith(path)
                        drawLine(if (selected) Mint else Color(0xFF3C506D), parent, child, strokeWidth = if (selected) 4.dp.toPx() else 1.6.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                MorseEngine.codes.forEach { (letter, path) ->
                    val center = position(path)
                    val selected = code.startsWith(path)
                    val radius = 11.dp.toPx()
                    val color = if (selected) (if (path.last() == '.') Mint else Coral) else Color(0xFF25364B)
                    if (path.last() == '.') {
                        drawCircle(color, radius, center)
                        drawCircle(if (selected) White else Muted, radius, center, style = Stroke(1.2.dp.toPx()))
                    } else {
                        drawRect(color, Offset(center.x - radius, center.y - radius * .75f), androidx.compose.ui.geometry.Size(radius * 2, radius * 1.5f))
                    }
                    drawContext.canvas.nativeCanvas.apply {
                        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            this.color = android.graphics.Color.WHITE
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 12.sp.toPx()
                            isFakeBoldText = selected
                        }
                        drawText(letter.toString(), center.x, center.y - radius - 5.dp.toPx(), p)
                    }
                }
                drawCircle(Gold, 5.dp.toPx(), position(""))
            }
        }
    }
}