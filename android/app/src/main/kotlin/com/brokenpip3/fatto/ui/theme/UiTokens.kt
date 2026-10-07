package com.brokenpip3.fatto.ui.theme

import androidx.compose.ui.unit.dp

/** Shared component dimensions. Keep view-specific measurements in the component only when no token fits. */
object FattoMetrics {
    val minTouchTarget = 48.dp
    val icon = 24.dp
    val dateControlIcon = 32.dp
    val dateControlMinWidth = 56.dp
    val smallIcon = 20.dp
    val inlineIcon = 16.dp
    val progressRing = 48.dp
    val largeProgressRing = 64.dp
    val tagTileMinHeight = 72.dp
    val pickerListMaxHeight = 320.dp
    val formBodyMaxHeight = 520.dp
    val compactLayoutWidth = 480.dp

    @Suppress("MayBeConst")
    val largeFontScale = 1.5f

    val taskListBottomInset = 96.dp
}

/** Stroke widths for borders and progress marks. Thin strokes are explicit grid exceptions. */
object FattoStroke {
    val subtle = 1.dp
    val selected = 2.dp
    val progress = 4.dp
    val projectStripe = 4.dp
    val urgency = 3.dp
}

/** Elevation roles. Toolbars and navigation use flat surfaces with explicit colors for separation. */
object FattoElevation {
    val none = 0.dp
    val card = 1.dp
    val overlay = 6.dp
}

/** Background and decoration alpha only; these values must not be used to fade text. */
object FattoOpacity {
    @Suppress("MayBeConst")
    val tint = 0.08f

    @Suppress("MayBeConst")
    val selectedTint = 0.16f

    @Suppress("MayBeConst")
    val track = 0.16f
}

/**
 * Shared Material mappings: screens use `background`; task and project cards use `surface`; supporting
 * panels use `surfaceContainerLow`; menus, dialogs, and sheets use `surfaceContainerHigh`; fields use
 * `surfaceContainerLowest`. Shapes map small to badges/chips, medium to cards/fields/buttons/menus,
 * and extra-large to dialogs and top sheet corners. Calendar days and progress rings stay circular.
 * Typography maps screen/dialog/sheet titles to `headlineSmall`, section titles to `titleMedium`,
 * task titles to `titleSmall`, body and field text to `bodyLarge`, supporting text to `bodyMedium`,
 * metadata to `bodySmall`, buttons to `labelLarge`, and badges to `labelSmall`.
 *
 * For horizontal form and action groups, use a vertical arrangement below 480dp of available width
 * or at font scale 1.5 and above when the group requires explicit stacking. Date/priority controls
 * wrap according to measured width and spread across each row; tag actions stay inside their field;
 * editor header actions stay beside the title; project text wraps beside fixed
 * progress and navigation elements. This rule does not change navigation, seven-column calendars, or
 * the user's selected tag column count. Use existing Material icons, preferring Filled except for
 * priority's Outlined Flag and AutoMirrored directional variants. Keep Material ripple, focus,
 * disabled, and animation behavior. Enabled text must reach 4.5:1 contrast and essential non-text
 * indicators 3:1 against their adjacent surface; interactive bounds target at least 48dp.
 */
object FattoUiContract
