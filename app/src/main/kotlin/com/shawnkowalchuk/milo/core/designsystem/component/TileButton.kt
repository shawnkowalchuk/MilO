package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The design's small button standing by itself in a tile, as it draws "Pair truck" under the
 * truck's name: the same button a status row has at its end (`RowButton`), for a screen to
 * place where a tile needs one.
 *
 * It is as wide as its words unless the caller passes a width through [modifier]. It is drawn
 * 40 dp high and takes up no more, so a tile is as high as drawn; the place a finger can hit is
 * 48 dp high and reaches 4 dp past the button, above and below, into room that has to be free.
 * It grows when its words need more than one line.
 *
 * Two or three buttons side by side that share a line are an `ActionButtonRow`.
 *
 * @param accent true for the one button that puts something right: the accent with dark words.
 * Every other one is the quiet fill of a control on a tile.
 * @param spokenName what a screen reader says the button is, where its words alone would not
 * say enough: a button that shows only a time says what the time is for.
 */
@Composable
fun TileButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    spokenName: String? = null,
) {
    RowButton(
        text = text,
        onClick = onClick,
        modifier =
            modifier
                .takesUpOnly(ButtonDefaults.MinHeight)
                .then(
                    if (spokenName == null) {
                        Modifier
                    } else {
                        Modifier.semantics { contentDescription = spokenName }
                    },
                ),
        accent = accent,
    )
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun TileButtonPreview() {
    MiloTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            TileButton(text = "Pair truck", onClick = {}, accent = true)
        }
    }
}
