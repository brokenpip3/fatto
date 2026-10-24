package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.FileDateDisplayConsentStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DateDisplayConsentStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun acceptancePersistsOnlyInsideTheSameInstallationDirectory() {
        val directory = temporary.newFolder("installation")
        val store = FileDateDisplayConsentStore(directory)
        assertFalse(store.isAccepted())
        assertTrue(store.accept())
        assertTrue(store.isAccepted())
        assertTrue(FileDateDisplayConsentStore(directory).isAccepted())
        assertFalse(FileDateDisplayConsentStore(temporary.newFolder("other-installation")).isAccepted())
    }

    @Test fun failedPersistenceNeverReportsAcceptance() {
        val invalidDirectory = temporary.newFile("not-a-directory")
        val store = FileDateDisplayConsentStore(invalidDirectory)
        assertFalse(store.isAccepted())
        assertFalse(store.accept())
        assertFalse(store.isAccepted())
    }

    @Test fun anUnexpectedDirectoryAtTheMarkerPathIsNotAcceptance() {
        val directory = temporary.newFolder()
        java.io.File(directory, "date-display-uda-notice-v1.accepted").mkdir()
        val store = FileDateDisplayConsentStore(directory)
        assertFalse(store.isAccepted())
        assertFalse(store.accept())
    }
}
