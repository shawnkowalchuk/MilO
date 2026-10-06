package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.TextEntry
import com.shawnkowalchuk.milo.data.settings.MAX_EMAIL_LENGTH
import com.shawnkowalchuk.milo.data.settings.MAX_REPORT_TEXT_LENGTH

/**
 * Who the report for the accountant is from, and where it goes: Shawn's name, his company, a
 * description of the vehicle, and the accountant's email address.
 *
 * Each field is stored as it is typed, like every setting: there is no Save button. The one
 * exception is said in the field itself. An address that is not an email address yet is not
 * stored, so the address field shows in red that what it holds is not what is saved.
 */
@Composable
internal fun ReportDetailsCard(fields: ReportFields, actions: ReportDetailActions) {
    SectionCard(title = stringResource(R.string.settings_report_title)) {
        Quiet(stringResource(R.string.settings_report_intro))
        TextEntry(
            label = stringResource(R.string.settings_report_name),
            initialText = fields.name,
            onTextChange = actions.onName,
            maxLength = MAX_REPORT_TEXT_LENGTH,
        )
        TextEntry(
            label = stringResource(R.string.settings_report_company),
            initialText = fields.company,
            onTextChange = actions.onCompany,
            maxLength = MAX_REPORT_TEXT_LENGTH,
        )
        TextEntry(
            label = stringResource(R.string.settings_report_vehicle),
            initialText = fields.vehicle,
            onTextChange = actions.onVehicle,
            maxLength = MAX_REPORT_TEXT_LENGTH,
        )
        Quiet(stringResource(R.string.settings_report_vehicle_detail))
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
        )
        Quiet(stringResource(R.string.settings_report_email_detail))
    }
}
