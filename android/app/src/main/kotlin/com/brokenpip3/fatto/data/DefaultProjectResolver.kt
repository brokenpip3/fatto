package com.brokenpip3.fatto.data

internal fun resolveProject(
    explicitProject: String?,
    defaultProjectEnabled: Boolean,
    defaultProject: String?,
): String? =
    explicitProject?.trim()?.takeIf { it.isNotEmpty() }
        ?: defaultProject
            ?.trim()
            ?.takeIf { defaultProjectEnabled && it.isNotEmpty() }
