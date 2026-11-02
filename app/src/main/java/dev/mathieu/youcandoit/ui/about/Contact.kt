package dev.mathieu.youcandoit.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Build

object Contact {
    const val EMAIL = "mathieuhentges+todoapp@gmail.com"
    const val PRIVACY_POLICY_URL = "https://mathieuhentges.github.io/youcandoit/privacy-policy.html"

    /**
     * Opens the phone's email app on a new message to [EMAIL]: the user sees and sends it themselves,
     * so the app needs no internet access and collects nothing.
     */
    fun feedbackIntent(subject: String, version: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            // Below the user's message: what's needed to reproduce a bug.
            putExtra(Intent.EXTRA_TEXT, "\n\n—\nVersion $version · Android ${Build.VERSION.RELEASE}")
        }

    fun privacyPolicyIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
}
