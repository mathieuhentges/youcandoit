package dev.mathieu.youcandoit.ui

import android.app.Application
import android.content.Intent
import dev.mathieu.youcandoit.ui.about.Contact
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ContactTest {
    @Test
    fun `feedback opens an email to the app's address, with the version below the message`() {
        val intent = Contact.feedbackIntent(subject = "You Can Do It! feedback", version = "0.1.0")

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:mathieuhentges+todoapp@gmail.com", intent.dataString)
        assertEquals(listOf("mathieuhentges+todoapp@gmail.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList())
        assertEquals("You Can Do It! feedback", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Version 0.1.0"))
    }

    @Test
    fun `the privacy policy opens the published page`() {
        assertEquals("https://mathieuhentges.github.io/youcandoit/privacy-policy.html", Contact.privacyPolicyIntent().dataString)
    }
}
