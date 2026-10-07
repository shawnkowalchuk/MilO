package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.TextEntry
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.MAX_EMAIL_LENGTH
import com.shawnkowalchuk.milo.data.settings.MAX_REPORT_TEXT_LENGTH

/**
 * Who the report for the accountant is from, and where it goes: Shawn's name, his company, a
 * description of the vehicle, and the accountant's email address. Four fields under their
 * names, as the design draws them, each with grey words inside it while it is empty.
 *
 * Each field is stored as it is typed, like every setting: there is no Save button. The one
 * exception is said in the field itself. An address that is not an email address yet is not
 * stored, so the address field shows in red that what it holds is not what is saved.
 *
 * What the four are for is put away behind the line at the end of the tile.
 */
@Composable
internal fun ReportDetailsTile(fields: ReportFields, actions: ReportDetailActions) {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.tileGap,
    ) {
        TileHeading(stringResource(R.string.settings_report_title))
        TextEntry(
            label = stringResource(R.string.settings_report_name),
            initialText = fields.name,
            onTextChange = actions.onName,
            maxLength = MAX_REPORT_TEXT_LENGTH,
            placeholder = stringResource(R.string.settings_report_name_example),
        )
        TextEntry(
            label = stringResource(R.string.settings_report_company),
            initialText = fields.company,
            onTextChange = actions.onCompany,
            maxLength = MAX_REPORT_TEXT_LENGTH,
            placeholder = stringResource(R.string.settings_report_company_example),
        )
        TextEntry(
            label = stringResource(R.string.settings_report_vehicle),
            initialText = fields.vehicle,
            onTextChange = actions.onVehicle,
            maxLength = MAX_REPORT_TEXT_LENGTH,
            placeholder = stringResource(R.string.settings_report_vehicle_example),
        )
        TextEntry(
            label = stringResource(R.string.settings_report_email),
            initialText = fields.accountantEmail,
            onTextChange = actions.onAccountantEmail,
            maxLength = MAX_EMAIL_LENGTH,
            email = true,
            lastField = true,
            error =
                stringResource(R.string.settings_report_email_refused)
                    .takeIf { fields.emailRefused },
            placeholder = stringResource(R.string.settings_report_email_example),
        )
        QuietExpander(
            label = stringResource(R.string.settings_about_report),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
        ) {
            Note(stringResource(R.string.settings_report_intro))
            NamedNote(
                stringResource(R.string.settings_report_vehicle),
                stringResource(R.string.settings_report_vehicle_detail),
            )
            NamedNote(
                stringResource(R.string.settings_report_email),
                stringResource(R.string.settings_report_email_detail),
            )
        }
    }
}
