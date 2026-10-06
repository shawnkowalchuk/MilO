package com.shawnkowalchuk.milo.platform.system

/**
 * A permission dialog that a button asked for and that has not answered yet.
 *
 * @param couldExplainBefore what Android said, before the dialog was asked for, to the question
 * "should the app explain why it needs this?". It says yes only after the permission has been
 * refused once and can still be asked for.
 */
data class PermissionAsk(val fix: SetupFix.AskPermission, val couldExplainBefore: Boolean)

/**
 * Whether Android answered a request for a permission without showing its dialog.
 *
 * Android stops showing the dialog once a permission has been refused twice, and then answers
 * "refused" at once. A button that leads to nothing would be a dead end, so the caller opens the
 * settings page instead. Android does not say whether it showed the dialog; this is the usual
 * way to tell. A dialog that was shown and refused changes the "should the app explain?" answer
 * (from no to yes at the first refusal, from yes to no at the second), so an answer of "no"
 * both before and after means no dialog appeared.
 *
 * One case is misread, harmlessly: the very first dialog being dismissed without an answer also
 * leaves "no" before and after, and the settings page opens.
 */
fun androidDidNotAsk(
    granted: Boolean,
    couldExplainBefore: Boolean,
    canExplainAfter: Boolean,
): Boolean = !granted && !couldExplainBefore && !canExplainAfter
