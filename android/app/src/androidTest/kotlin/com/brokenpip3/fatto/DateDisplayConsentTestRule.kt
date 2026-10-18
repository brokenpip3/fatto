package com.brokenpip3.fatto

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.File

/** Each test restores the installation's previous acknowledgement instead of depending on test order. */
class DateDisplayConsentTestRule(private val accepted: Boolean = true) : ExternalResource() {
    private val marker: File
        get() =
            File(
                InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
                "date-display-uda-notice-v1.accepted",
            )
    private var previous: ByteArray? = null

    override fun before() {
        previous = marker.takeIf { it.isFile }?.readBytes()
        marker.delete()
        if (accepted) marker.writeText("")
    }

    override fun after() {
        marker.delete()
        previous?.let { marker.writeBytes(it) }
    }
}
