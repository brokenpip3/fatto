package com.brokenpip3.fatto.vm

import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.model.Task

/** Timestamp ordering stays chronological regardless of a field's display preference. */
internal fun taskComparator(
    order: SortOrder,
    direction: SortDirection,
): Comparator<Task> =
    Comparator { a, b ->
        fun date(
            first: String?,
            second: String?,
            nullsLast: Boolean = true,
        ): Int {
            val left = DateTimeUtils.parseToInstant(first)
            val right = DateTimeUtils.parseToInstant(second)
            return when {
                left == null && right == null -> 0
                left == null -> if (nullsLast) 1 else -1
                right == null -> if (nullsLast) -1 else 1
                else -> left.compareTo(right)
            }
        }

        fun priority(value: String?): Int =
            when (value) {
                "H" -> 0
                "M" -> 1
                "L" -> 2
                else -> 3
            }
        val result =
            when (order) {
                SortOrder.DATE_CREATED -> date(a.entry, b.entry, nullsLast = false)
                SortOrder.DUE_DATE -> date(a.due, b.due)
                SortOrder.SCHEDULED_DATE -> date(a.scheduled, b.scheduled)
                SortOrder.PRIORITY -> priority(a.priority).compareTo(priority(b.priority))
                SortOrder.URGENCY -> a.urgency.compareTo(b.urgency)
                SortOrder.ALPHABETICAL -> a.description.lowercase().compareTo(b.description.lowercase())
            }
        if (direction == SortDirection.DESCENDING) -result else result
    }
