package com.brokenpip3.fatto.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

fun assertTextFits(node: SemanticsNodeInteraction) {
    val layouts = mutableListOf<TextLayoutResult>()
    node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
    assertTrue("Text must have a layout result", layouts.isNotEmpty())
    layouts.forEach { layout ->
        // Text uses fractional pixels while its measured size is rounded to integers.
        val right = (0 until layout.lineCount).maxOf { layout.getLineRight(it) }
        assertTrue("Text exceeds its width: $right > ${layout.size.width}", right <= layout.size.width + 1f)
        assertTrue("Text exceeds its height", layout.multiParagraph.height <= layout.size.height + 1f)
        assertTrue("Text must not be ellipsized", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
    }
}

fun captureLayoutScreenshot(name: String) {
    if (InstrumentationRegistry.getArguments().getString("layoutScreenshots") != "true") return
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand("mkdir -p /sdcard/Download/fatto-layout"),
    ).use { it.readBytes() }
    android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand("screencap -p /sdcard/Download/fatto-layout/$name.png"),
    ).use { it.readBytes() }
}
