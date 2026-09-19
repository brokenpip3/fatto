package com.brokenpip3.fatto.vm

internal fun selectableProjectNames(
    nodes: List<ProjectNode>,
    showEmptyProjects: Boolean,
): List<String> =
    nodes
        .asSequence()
        .filter { showEmptyProjects || it.count > 0 }
        .map { it.fullName }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .toList()
