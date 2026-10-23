package app.voxshelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voxshelf.Problem
import app.voxshelf.RatingSummary
import app.voxshelf.Ratings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { Ask, Problems, Thanks }

/** "How was this recording?" Thumbs up says thanks; thumbs down asks what went wrong first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingSheet(recording: Int, chapter: Int?, onDone: () -> Unit) {
    val ratings = Ratings.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Ask) }
    var problems by remember { mutableStateOf(emptySet<Problem>()) }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun send(up: Boolean) {
        sending = true
        failed = false
        scope.launch {
            try {
                ratings.send(recording, up, problems, note, chapter)
                step = Step.Thanks
            } catch (e: Exception) {
                failed = true
            }
            sending = false
        }
    }

    if (step == Step.Thanks) LaunchedEffect(Unit) {
        delay(1_500)
        onDone()
    }

    ModalBottomSheet(
        onDismissRequest = onDone,
        containerColor = Lantern.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (step) {
                Step.Ask -> {
                    Text("How was this recording?", style = text(22.sp, 800), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Thumb("👍", "Thumbs up", enabled = !sending) { send(up = true) }
                        Thumb("👎", "Thumbs down", enabled = !sending) { step = Step.Problems }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Not now",
                        style = text(16.sp, 800, Lantern.TextMuted),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDone).padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }

                Step.Problems -> {
                    Text("What went wrong?", style = text(22.sp, 800), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Problem.entries.forEach { p ->
                        val on = p in problems
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Checkbox) { problems = if (on) problems - p else problems + p }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (on) Lantern.Gold else Lantern.SurfaceHigh),
                                contentAlignment = Alignment.Center,
                            ) { if (on) Text("✓", style = text(15.sp, 900, Lantern.Forest)) }
                            Spacer(Modifier.size(14.dp))
                            Text(p.label, style = text(16.sp, if (on) 800 else 600))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it.take(1000) },
                        textStyle = text(16.sp, 600),
                        cursorBrush = SolidColor(Lantern.Gold),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { field ->
                            Box(Modifier.fillMaxWidth().heightIn(min = 80.dp).clip(RoundedCornerShape(16.dp)).background(Lantern.Forest).padding(16.dp)) {
                                if (note.isEmpty()) Text("Anything else? (optional)", style = text(16.sp, 600, Lantern.TextMuted))
                                field()
                            }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    val canSend = problems.isNotEmpty() || note.isNotBlank()
                    Text(
                        if (sending) "Sending…" else "Send",
                        style = text(17.sp, 800, if (canSend) Lantern.Forest else Lantern.TextMuted),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (canSend) Lantern.Gold else Lantern.SurfaceHigh)
                            .clickable(enabled = canSend && !sending) { send(up = false) }
                            .padding(vertical = 16.dp),
                    )
                }

                Step.Thanks -> {
                    Spacer(Modifier.height(12.dp))
                    Text("Thanks!", style = text(24.sp, 800), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (failed) {
                Spacer(Modifier.height(12.dp))
                Text("Couldn't send. Check your internet connection and try again.", style = text(14.sp, 600, Lantern.Gold), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Thumb(emoji: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(Lantern.SurfaceHigh)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = text(36.sp, 400)) }
}

/** The line under a recording on the book page. */
fun ratingLine(summary: RatingSummary?): String {
    val liked = summary?.percentLiked ?: return if ((summary?.total ?: 0) > 0) "Not enough ratings yet" else "No ratings yet"
    return "$liked% liked · ${summary.total} ratings"
}

/** The most-reported problem, once at least two people agree on it. */
fun problemLine(summary: RatingSummary?): String? {
    val (problem, count) = summary?.problems?.maxByOrNull { it.value } ?: return null
    if (count < 2) return null
    val what = when (problem) {
        Problem.Sound -> "sound problems"
        Problem.HardToFollow -> "the reading was hard to follow"
        Problem.Missing -> "missing or repeated parts"
        Problem.WontPlay -> "a chapter that won't play"
        Problem.Order -> "chapters out of order or mislabeled"
        Problem.Other -> "other problems"
    }
    return "$count people reported $what"
}
