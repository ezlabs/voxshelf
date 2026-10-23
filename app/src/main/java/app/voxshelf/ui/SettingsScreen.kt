package app.voxshelf.ui

import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voxshelf.Downloads

/** Settings and credits: the Wi-Fi-only download switch, and who makes the books possible. */
@Composable
fun SettingsScreen() {
    val downloads = Downloads.get(LocalContext.current)
    var wifiOnly by remember { mutableStateOf(downloads.wifiOnly) }
    var feedback by remember { mutableStateOf(false) }
    if (feedback) FeedbackSheet { feedback = false }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Settings", style = text(30.sp, 800), modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(20.dp))
            Text("Downloads", style = text(17.sp, 800))
            Spacer(Modifier.height(4.dp))
            Toggle("Download only on Wi-Fi", wifiOnly) {
                wifiOnly = it
                downloads.wifiOnly = it
            }

            if (app.voxshelf.Feedback.enabled) {
                Spacer(Modifier.height(28.dp))
                Text("Help", style = text(17.sp, 800))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Report a problem or suggest an idea",
                    style = text(15.sp, 800, Lantern.Gold),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { feedback = true }.padding(vertical = 10.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("About", style = text(17.sp, 800))
            Spacer(Modifier.height(8.dp))
            Paragraph("Every recording is made by LibriVox volunteers and is in the public domain in the USA.")
            Paragraph("Voxshelf is an independent app. It isn't made or endorsed by LibriVox.")
            Paragraph("Audio and cover art are hosted by the Internet Archive. Book ratings come from Open Library.")
            Spacer(Modifier.height(8.dp))
            Link("Visit LibriVox", "https://librivox.org")
            Link("Volunteer with LibriVox", "https://librivox.org/pages/volunteer-for-librivox/")
            Link("Internet Archive", "https://archive.org")
            Link("Open Library", "https://openlibrary.org")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = text(15.sp, 400, Lantern.TextSoft), lineHeight = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun Link(label: String, url: String) {
    val uri = LocalUriHandler.current
    Text(
        label,
        style = text(15.sp, 800, Lantern.Gold),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { uri.openUri(url) }.padding(vertical = 10.dp),
    )
}

private enum class FeedbackStep { Write, Sent }

/** Problem or idea, posted publicly as a GitHub issue. Says so up front. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackSheet(onDone: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var kind by remember { mutableStateOf(app.voxshelf.Feedback.Kind.Problem) }
    var message by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(FeedbackStep.Write) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val canSend = message.trim().length >= 10 && !sending

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDone,
        containerColor = Lantern.Forest,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            if (step == FeedbackStep.Sent) {
                Text("Thanks!", style = text(24.sp, 800))
                Spacer(Modifier.height(8.dp))
                Paragraph("Your feedback was posted. You can follow it on GitHub.")
                Text(
                    "Done",
                    style = text(17.sp, 800, Lantern.Forest),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Lantern.Gold).clickable(onClick = onDone).padding(vertical = 16.dp),
                )
                return@Column
            }
            Text("Report a problem or suggest an idea", style = text(22.sp, 800))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Pill("Problem", selected = kind == app.voxshelf.Feedback.Kind.Problem) { kind = app.voxshelf.Feedback.Kind.Problem }
                Pill("Idea", selected = kind == app.voxshelf.Feedback.Kind.Idea) { kind = app.voxshelf.Feedback.Kind.Idea }
            }
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = message,
                onValueChange = { message = it.take(4000) },
                textStyle = text(16.sp, 600),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Lantern.Gold),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { field ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(16.dp)).background(Lantern.Surface).padding(16.dp),
                    ) {
                        if (message.isEmpty()) Text(
                            if (kind == app.voxshelf.Feedback.Kind.Problem) "What went wrong? What were you doing when it happened?"
                            else "What would make Voxshelf better for you?",
                            style = text(16.sp, 600, Lantern.TextMuted),
                        )
                        field()
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "This is posted publicly on GitHub so others can see it and help. Please don't include personal details." +
                    if (kind == app.voxshelf.Feedback.Kind.Problem) " Your app version and phone model are added to help fix it." else "",
                style = text(13.sp, 600, Lantern.TextMuted),
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (sending) "Sending…" else "Send",
                style = text(17.sp, 800, if (canSend) Lantern.Forest else Lantern.TextMuted),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (canSend) Lantern.Gold else Lantern.SurfaceHigh)
                    .clickable(enabled = canSend) {
                        sending = true
                        failed = false
                        scope.launch {
                            try {
                                app.voxshelf.Feedback.send(kind, message)
                                step = FeedbackStep.Sent
                            } catch (e: Exception) {
                                failed = true
                            }
                            sending = false
                        }
                    }
                    .padding(vertical = 16.dp),
            )
            if (failed) {
                Spacer(Modifier.height(12.dp))
                Text("Couldn't send. Check your internet connection and try again.", style = text(14.sp, 600, Lantern.Gold))
            }
        }
    }
}
