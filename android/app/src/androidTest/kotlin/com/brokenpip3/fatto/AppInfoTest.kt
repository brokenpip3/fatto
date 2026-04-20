package com.brokenpip3.fatto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppInfoTest {
    @Test
    fun testBuildConfigFields() {
        // Verify that the build-time generated fields are not empty or default
        assertFalse("Version name should not be empty", BuildConfig.VERSION_NAME.isEmpty())
        assertNotEquals("Version code should not be zero", 0, BuildConfig.VERSION_CODE)
        assertFalse("Build date should not be empty", BuildConfig.BUILD_DATE.isEmpty())
    }
}
