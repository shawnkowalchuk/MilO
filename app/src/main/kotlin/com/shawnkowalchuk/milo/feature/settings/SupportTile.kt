package com.shawnkowalchuk.milo.feature.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLinkLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.platform.system.readInstalledVersion
import java.net.URLEncoder

/**
 * Where a question about MilO is sent (Shawn's request of 2026-10-10: "what about adding a
 * support link? can we have it send email to me?"). It is an address at the website's domain
 * that forwards to his own, which he asked to keep out of sight ("can we hide my email
 * address?"). The website's Support link is the same address.
 */
internal const val SUPPORT_ADDRESS = "support@milotriplog.top"

/**
 * The tile above Buy me a coffee that starts an email to [SUPPORT_ADDRESS] in the phone's
 * email app. It is drawn as the two tiles under it are: the label with its arrowhead, and the
 * whole tile one button.
 *
 * MilO sends nothing itself and still needs no permission to use the internet: it hands the
 * email app a draft, and the person sends it or does not. The draft ends with the version of
 * MilO and the phone it runs on, which is what a question about a missed trip is answered
 * from; the tile says so before the press, and the line can be deleted in the draft. A phone
 * with no email app says so on the tile, with the address to write to from elsewhere.
 */
@Composable
internal fun SupportTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var noEmailApp by rememberSaveable { mutableStateOf(false) }
    val subject = stringResource(R.string.settings_support_subject)
    val version = readInstalledVersion(context)
    val about =
        stringResource(
            R.string.settings_support_about,
            version.name,
            version.code,
            Build.VERSION.RELEASE,
            phoneName(Build.MANUFACTURER, Build.MODEL),
        )
    Tile(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        press =
            TilePress(
                label = stringResource(R.string.settings_support_open),
                onClick = {
                    val draft = supportMailto(SUPPORT_ADDRESS, subject, supportBody(about))
                    noEmailApp = !context.openEmailDraft(draft)
                },
            ),
        gap = MiloTheme.spacing.extraSmall,
    ) {
        TileLinkLabel(stringResource(R.string.settings_support_label))
        Text(text = SUPPORT_ADDRESS, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_support_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (noEmailApp) {
            StatusRow(
                label = stringResource(R.string.settings_support_no_email_app),
                status = RowStatus.PROBLEM,
            )
        }
    }
}

/**
 * The phone as its maker names it: "Xiaomi 23078PND5G". Many makers begin the model with
 * their own name ("samsung SM-S911W" is rare, "Google Pixel 8" is not): it is then said once.
 */
internal fun phoneName(maker: String, model: String): String {
    val cleanMaker = maker.trim().replaceFirstChar { it.uppercase() }
    val cleanModel = model.trim()
    return when {
        cleanMaker.isEmpty() -> cleanModel
        cleanModel.startsWith(cleanMaker, ignoreCase = true) -> cleanModel
        else -> "$cleanMaker $cleanModel"
    }
}

/**
 * The draft's text: room to write in at the top, and under it the one line [about] MilO and
 * the phone.
 */
internal fun supportBody(about: String): String = "\n\n\n$about"

/**
 * A "mailto:" address that carries the subject and the text of the draft, which is the form
 * every email app takes. Spaces are written as %20, not as "+": an email app shows a "+" as
 * it stands.
 */
internal fun supportMailto(address: String, subject: String, body: String): String {
    fun encoded(text: String) = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
    return "mailto:$address?subject=${encoded(subject)}&body=${encoded(body)}"
}

/**
 * Opens the email app on [mailto]. False if the phone has no app that writes emails.
 *
 * Lint offers String.toUri() from core-ktx, a library MilO does not declare, as `CoffeeTile`
 * says. One call does not justify it.
 */
@SuppressLint("UseKtx")
private fun Context.openEmailDraft(mailto: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)))
    true
} catch (noEmailApp: ActivityNotFoundException) {
    false
}
