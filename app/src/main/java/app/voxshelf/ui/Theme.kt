package app.voxshelf.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.voxshelf.R

/** The "Lantern" look: deep forest green, warm gold, soft rounded shapes. */
object Lantern {
    val Forest = Color(0xFF0F1A15)
    val Surface = Color(0xFF18261F)
    val SurfaceHigh = Color(0xFF233A2F)
    val Cover = Color(0xFF2F5D4A)
    val Gold = Color(0xFFF2C76E)
    val Text = Color(0xFFEAF2EC)
    val TextSoft = Color(0xFFCFE3D6)
    val TextMuted = Color(0xFFA6B8AD)
}

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800))

@Composable
fun VoxshelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Lantern.Forest,
            surface = Lantern.Surface,
            primary = Lantern.Gold,
            onPrimary = Lantern.Forest,
            onBackground = Lantern.Text,
            onSurface = Lantern.Text,
        ),
        content = content,
    )
}
