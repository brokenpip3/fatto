package com.brokenpip3.fatto.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.brokenpip3.fatto.ui.theme.FontSize
import kotlin.math.roundToInt

@Composable
internal fun FontSizeSetting(
    fontSizePercent: Int,
    onFontSizePercentChange: (Int) -> Unit,
) {
    var selectedPercent by remember(fontSizePercent) { mutableFloatStateOf(fontSizePercent.toFloat()) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Font size: ${selectedPercent.roundToInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onFontSizePercentChange(FontSize.DEFAULT_PERCENT) },
                enabled = fontSizePercent != FontSize.DEFAULT_PERCENT,
                modifier = Modifier.testTag("ResetFontSizeButton").semantics { contentDescription = "Reset font size" },
            ) {
                Text("Reset")
            }
        }
        Slider(
            value = selectedPercent,
            onValueChange = { selectedPercent = it },
            onValueChangeFinished = { onFontSizePercentChange(selectedPercent.roundToInt()) },
            valueRange = FontSize.MIN_PERCENT.toFloat()..FontSize.MAX_PERCENT.toFloat(),
            steps = 6,
            modifier =
                Modifier.fillMaxWidth().testTag("FontSizeSlider").semantics {
                    contentDescription = "Font size"
                    stateDescription = "${selectedPercent.roundToInt()}%"
                },
        )
        Text(
            text = "Adjusts the app text relative to your Android font size. Changes apply when you release the slider.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
