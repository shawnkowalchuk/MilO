package com.shawnkowalchuk.milo.data.settings

// The four settings of the report for the accountant: what a typed value is stored as, what an
// email address must look like, and what a report cannot do without. Pure, so the rules are
// tested without a phone.

/**
 * How long a name, a company or a vehicle description may be. The report prints each on a line
 * of its heading and the name in the footer of every page; this is far more than any of them
 * needs, and it keeps a pasted paragraph out of the heading.
 */
const val MAX_REPORT_TEXT_LENGTH = 80

/** The longest an email address may be, by the rules of email itself. */
const val MAX_EMAIL_LENGTH = 254

/** The longest the part before the "@" may be, by the same rules. */
private const val MAX_EMAIL_LOCAL_LENGTH = 64

/** A country or domain ending has at least two letters: ".ca", ".com". */
private const val MIN_DOMAIN_ENDING_LENGTH = 2

/**
 * A typed name, company or vehicle description as it is stored: without the spaces around it,
 * and null if nothing is left. Null is how "not set" is stored.
 */
fun reportTextOrNull(typed: String): String? = typed.trim().ifEmpty { null }

/**
 * Whether [text] can be stored as a name, a company or a vehicle description as it is: not
 * blank, with no spaces around it, and no longer than the report has room for.
 */
fun isReportText(text: String): Boolean =
    text == reportTextOrNull(text) && text.length <= MAX_REPORT_TEXT_LENGTH

/**
 * Whether [text] is one email address MilO can hand to the email app: something before one
 * "@", and after it a domain of at least two parts, the last of them letters ("example.ca").
 *
 * The check is a sensible one, not the whole standard: it is there to catch a slip of the
 * thumb (a missing "@", a space, a comma where the dot belongs, two addresses in one field),
 * and a wrong address that looks right can only be caught by the email bouncing.
 */
fun isEmailAddress(text: String): Boolean {
    if (text.length > MAX_EMAIL_LENGTH || text.count { it == '@' } != 1) return false
    // One address, and nothing an email app would read as a list or as a name beside it.
    if (text.any { it.isWhitespace() || it in ",;:<>()[]\"\\" }) return false
    val local = text.substringBefore('@')
    val labels = text.substringAfter('@').split('.')
    val localIsRight =
        local.isNotEmpty() &&
            local.length <= MAX_EMAIL_LOCAL_LENGTH &&
            !local.startsWith('.') &&
            !local.endsWith('.') &&
            ".." !in local
    val domainIsRight =
        labels.size >= 2 &&
            labels.all { label ->
                label.isNotEmpty() &&
                    label.all { it.isLetterOrDigit() || it == '-' } &&
                    !label.startsWith('-') &&
                    !label.endsWith('-')
            } &&
            labels.last().length >= MIN_DOMAIN_ENDING_LENGTH &&
            labels.last().all { it.isLetter() }
    return localIsRight && domainIsRight
}

/** A setting a report needs and does not have. Each is set on the Settings screen. */
enum class MissingDetail {
    /** Shawn's name: it is in the report's heading and in the email's subject. */
    NAME,

    /** The accountant's email address: the email app is opened with it. */
    ACCOUNTANT_EMAIL,
}

/**
 * What stands in the way of making the PDF: the name, if it is not set. The company and the
 * vehicle are left off the report when they are not set, and the accountant's address is not
 * printed on it.
 */
fun MiloSettings.missingForPdf(): List<MissingDetail> =
    listOfNotNull(MissingDetail.NAME.takeIf { reportName == null })

/** What stands in the way of sending the report: the name and the accountant's address. */
fun MiloSettings.missingForSending(): List<MissingDetail> = missingForPdf() +
    listOfNotNull(MissingDetail.ACCOUNTANT_EMAIL.takeIf { accountantEmail == null })
