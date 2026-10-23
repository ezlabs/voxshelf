package app.voxshelf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.voxshelf.PlayerState
import app.voxshelf.PlayerViewModel
import app.voxshelf.SleepTimer

private fun text(size: TextUnit, weight: Int = 400, color: Color = Lantern.Text) =
    TextStyle(fontFamily = Nunito, fontSize = size, fontWeight = FontWeight(weight), color = color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showChapters by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val book = state.book

    Column(
        Modifier
            .fillMaxSize()
            .background(Lantern.Forest)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (book == null) {
            Spacer(Modifier.weight(1f))
            Text(state.error ?: "Getting your book ready…", style = text(16.sp, 600, Lantern.TextMuted), textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            return@Column
        }

        Pill("Chapter ${state.chapter + 1} of ${book.chapters.size}", onClick = { showChapters = true })

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Cover(book.title, book.author, 270.dp)
        }

        Text(
            book.chapters.getOrNull(state.chapter)?.title.orEmpty(),
            style = text(24.sp, 800),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${book.title} · Read by ${book.readers.joinToString(", ")}",
            style = text(15.sp, 400, Lantern.TextMuted),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = vm::seekTo,
            colors = SliderDefaults.colors(
                thumbColor = Lantern.Gold,
                activeTrackColor = Lantern.Gold,
                inactiveTrackColor = Lantern.Surface,
            ),
            modifier = Modifier.semantics { contentDescription = "Position in chapter" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(state.positionMs), style = text(13.sp, 700, Lantern.TextMuted))
            Text("${hoursMinutes(state.leftInBookMs)} left in book", style = text(13.sp, 700, Lantern.TextMuted))
            Text(clock(state.durationMs), style = text(13.sp, 700, Lantern.TextMuted))
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RoundButton("-15", "Back 15 seconds", 60.dp, Lantern.Surface, onClick = vm::back)
            PlayButton(state.isPlaying, onClick = vm::playPause)
            RoundButton("+30", "Forward 30 seconds", 60.dp, Lantern.Surface, onClick = vm::forward)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("${speedLabel(state.speed)} speed", onClick = vm::cycleSpeed)
            Pill(sleepLabel(state), onClick = { showSleep = true })
            Pill("Chapters", onClick = { showChapters = true })
        }
    }

    if (showChapters && book != null) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }, containerColor = Lantern.Surface) {
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                itemsIndexed(book.chapters) { i, chapter ->
                    val current = i == state.chapter
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.playChapter(i); showChapters = false }
                            .background(if (current) Lantern.SurfaceHigh else Color.Transparent)
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(chapter.title, style = text(16.sp, if (current) 800 else 600, if (current) Lantern.Gold else Lantern.Text), modifier = Modifier.weight(1f))
                        Text(clock(chapter.seconds * 1000L), style = text(14.sp, 600, Lantern.TextMuted))
                    }
                }
            }
        }
    }

    if (showSleep) {
        ModalBottomSheet(onDismissRequest = { showSleep = false }, containerColor = Lantern.Surface) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sleep timer", style = text(20.sp, 800), modifier = Modifier.padding(bottom = 8.dp))
                SleepTimer.entries.forEach { option ->
                    val selected = option == state.sleepTimer
                    Text(
                        option.label,
                        style = text(17.sp, if (selected) 800 else 600, if (selected) Lantern.Gold else Lantern.Text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { vm.setSleepTimer(option); showSleep = false }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Cover(title: String, author: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Lantern.Gold.copy(alpha = 0.32f), Color.Transparent), radius = this.size.width * 0.85f),
                    radius = this.size.width * 0.85f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(40.dp))
                .background(Lantern.Cover)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LanternIcon(48.dp)
            Spacer(Modifier.height(10.dp))
            Text(title, style = text(36.sp, 800), textAlign = TextAlign.Center, lineHeight = 38.sp)
            Spacer(Modifier.height(6.dp))
            Text(author, style = text(15.sp, 600, Lantern.TextSoft), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LanternIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Lantern.Gold, Offset(9 * u, 3 * u), Offset(15 * u, 3 * u), 1.6f * u, androidx.compose.ui.graphics.StrokeCap.Round)
        drawRoundRect(Lantern.Gold, Offset(7 * u, 6 * u), Size(10 * u, 13 * u), CornerRadius(4 * u), style = stroke)
        drawLine(Lantern.Gold, Offset(12 * u, 11 * u), Offset(12 * u, 15 * u), 1.6f * u, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(Lantern.Gold)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(32.dp)) {
            val w = size.width
            if (isPlaying) {
                drawRoundRect(Lantern.Forest, Offset(w * 0.25f, w * 0.2f), Size(w * 0.18f, w * 0.6f), CornerRadius(w * 0.08f))
                drawRoundRect(Lantern.Forest, Offset(w * 0.57f, w * 0.2f), Size(w * 0.18f, w * 0.6f), CornerRadius(w * 0.08f))
            } else {
                drawPath(Path().apply {
                    moveTo(w * 0.3f, w * 0.17f)
                    lineTo(w * 0.85f, w * 0.5f)
                    lineTo(w * 0.3f, w * 0.83f)
                    close()
                }, Lantern.Forest)
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, description: String, size: Dp, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = text(15.sp, 800))
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = text(14.sp, 700),
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Lantern.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun hoursMinutes(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

private fun speedLabel(speed: Float) = "%.2f".format(speed).trimEnd('0').trimEnd('.') + "×"

private fun sleepLabel(state: PlayerState): String = when {
    state.sleepRemainingMs != null -> "Sleep in ${(state.sleepRemainingMs + 59_999) / 60_000}m"
    state.sleepTimer == SleepTimer.EndOfChapter -> "Sleep at chapter end"
    else -> "Sleep timer"
}
