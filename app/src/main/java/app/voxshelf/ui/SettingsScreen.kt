package app.voxshelf.ui

import androidx.compose.foundation.clickable
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

            Spacer(Modifier.height(28.dp))
            Text("About", style = text(17.sp, 800))
            Spacer(Modifier.height(8.dp))
            Paragraph("Every recording is made by LibriVox volunteers and is in the public domain in the USA.")
            Paragraph("Voxshelf is an independent app. It isn't made or endorsed by LibriVox.")
            Paragraph("Audio and cover art are hosted by the Internet Archive. Book ratings come from Open Library.")
            Spacer(Modifier.height(8.dp))
            Link("Visit LibriVox", "https://librivox.org")
            Link("Volunteer or donate to LibriVox", "https://librivox.org/pages/how-to-help/")
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
