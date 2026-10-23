package app.voxshelf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
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
import kotlin.math.absoluteValue

fun text(size: TextUnit, weight: Int = 400, color: Color = Lantern.Text) =
    TextStyle(fontFamily = Nunito, fontSize = size, fontWeight = FontWeight(weight), color = color)

/** Muted tones for generated covers, so lists are easy to scan but stay in the Lantern palette. */
private val coverTones = listOf(
    Color(0xFF2F5D4A), Color(0xFF2D5662), Color(0xFF4E3A55), Color(0xFF6B3F2E), Color(0xFF3B4A5E), Color(0xFF4F5A2F),
)

fun coverColor(title: String) = coverTones[title.hashCode().absoluteValue % coverTones.size]

/**
 * The LibriVox cover art when there is one ([art] is its small version). A cover made from the title and
 * author shows while it loads, and stays if there's no art or it can't load.
 */
@Composable
fun Cover(title: String, author: String, size: Dp, art: app.voxshelf.Art? = null, glow: Boolean = false) {
    val big = size >= 160.dp
    Box(
        Modifier
            .size(size)
            .drawBehind {
                if (glow) drawCircle(
                    Brush.radialGradient(listOf(Lantern.Gold.copy(alpha = 0.32f), Color.Transparent), radius = this.size.width * 0.7f),
                    radius = this.size.width * 0.7f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.15f))
                .background(coverColor(title))
                .padding(size * 0.08f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (big) {
                LanternIcon(size * 0.18f)
                Spacer(Modifier.height(size * 0.04f))
            }
            Text(
                title,
                style = text((size.value * 0.13f).sp, 800),
                lineHeight = (size.value * 0.14f).sp,
                textAlign = TextAlign.Center,
                maxLines = if (big) 3 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (big) {
                Spacer(Modifier.height(size * 0.025f))
                Text(author, style = text((size.value * 0.056f).sp, 600, Lantern.TextSoft), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        if (art != null) CoverArt(art, size >= 80.dp, title, Modifier.size(size).clip(RoundedCornerShape(size * 0.05f)))
    }
}

/**
 * Tries the fast copy on the Voxshelf server first. If it's missing, falls back to LibriVox's art:
 * full size on covers 80dp and up (the small one looks blurry there), then the small one.
 */
@Composable
private fun CoverArt(art: app.voxshelf.Art, big: Boolean, title: String, modifier: Modifier) {
    val choices = remember(art, big) { listOfNotNull(art.fast, art.full.takeIf { big && it != art.thumb }, art.thumb) }
    var tried by remember(choices) { mutableIntStateOf(0) }
    coil3.compose.AsyncImage(
        model = choices[tried],
        contentDescription = "Cover of $title",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        onError = { if (tried < choices.lastIndex) tried++ },
        modifier = modifier,
    )
}

/** Starts loading the covers of the next few books past [lastVisible], so they're ready when you scroll to them. */
@Composable
fun PrefetchCovers(works: List<app.voxshelf.WorkSummary>, lastVisible: () -> Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Watched outside composition, so scrolling doesn't redraw the list.
    androidx.compose.runtime.LaunchedEffect(works) {
        val loader = coil3.SingletonImageLoader.get(context)
        androidx.compose.runtime.snapshotFlow(lastVisible).collect { last ->
            works.drop(last + 1).take(PREFETCH).mapNotNull { it.first.cover }.forEach { art ->
                loader.enqueue(coil3.request.ImageRequest.Builder(context).data(art.fast).build())
            }
        }
    }
}

private const val PREFETCH = 4

@Composable
fun LanternIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        drawLine(Lantern.Gold, Offset(9 * u, 3 * u), Offset(15 * u, 3 * u), 1.6f * u, StrokeCap.Round)
        drawRoundRect(Lantern.Gold, Offset(7 * u, 6 * u), Size(10 * u, 13 * u), CornerRadius(4 * u), style = Stroke(1.6f * u))
        drawLine(Lantern.Gold, Offset(12 * u, 11 * u), Offset(12 * u, 15 * u), 1.6f * u, StrokeCap.Round)
    }
}

@Composable
fun Pill(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = text(14.sp, 700, if (selected) Lantern.Forest else Lantern.Text),
        maxLines = 1,
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Lantern.Gold else Lantern.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** A round icon button drawn in Lantern style; [draw] paints the icon in a 24-unit box. */
@Composable
fun IconCircle(description: String, onClick: () -> Unit, draw: androidx.compose.ui.graphics.drawscope.DrawScope.(u: Float) -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Lantern.Surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) { draw(size.width / 24f) }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.chevron(u: Float, down: Boolean) {
    val stroke = 2.4f * u
    if (down) {
        drawLine(Lantern.Text, Offset(6 * u, 9 * u), Offset(12 * u, 15 * u), stroke, StrokeCap.Round)
        drawLine(Lantern.Text, Offset(12 * u, 15 * u), Offset(18 * u, 9 * u), stroke, StrokeCap.Round)
    } else {
        drawLine(Lantern.Text, Offset(15 * u, 6 * u), Offset(9 * u, 12 * u), stroke, StrokeCap.Round)
        drawLine(Lantern.Text, Offset(9 * u, 12 * u), Offset(15 * u, 18 * u), stroke, StrokeCap.Round)
    }
}

fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun hoursMinutes(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/** "Jane Austen · 1813" in book listings. A long author name is shortened so the year always shows. */
@Composable
fun AuthorLine(r: app.voxshelf.Recording, size: androidx.compose.ui.unit.TextUnit) {
    Row {
        Text(
            r.authors.ifBlank { "Various authors" },
            style = text(size, 600, Lantern.TextSoft),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        r.year?.let { Text(" · $it", style = text(size, 600, Lantern.TextSoft), maxLines = 1) }
    }
}

/** A labelled on/off switch row. */
@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Switch) { onChange(!on) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = text(16.sp, 600), modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = on,
            onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Lantern.Forest, checkedTrackColor = Lantern.Gold,
                uncheckedThumbColor = Lantern.TextMuted, uncheckedTrackColor = Lantern.Surface, uncheckedBorderColor = Lantern.SurfaceHigh,
            ),
        )
    }
}
