package com.trainpaths.nonogram.filter

import com.trainpaths.nonogram.classes.MAX_NONOGRAM_SIDE
import com.trainpaths.nonogram.classes.MIN_NONOGRAM_SIDE
import com.trainpaths.nonogram.classes.Nonogram

enum class SortDirection { DESC, ASC }

/** The size range that filters nothing out: every grid side the generator allows. */
val FULL_SIZE_RANGE: IntRange = MIN_NONOGRAM_SIDE..MAX_NONOGRAM_SIDE

/** One row of the filter dropdown. Its [label] is shown to the user and identifies it in state. */
sealed interface FilterEntry {
    val label: String
}

/** One checkable value under an attribute, e.g. "Easy" under "Difficulty". */
data class FilterOption(
    val label: String,
    val matches: (Nonogram) -> Boolean,
)

/** A sortable attribute together with the values it can be filtered by. */
data class FilterAttribute(
    override val label: String,
    val options: List<FilterOption>,
    val ascending: Comparator<Nonogram>,
) : FilterEntry

/**
 * A standalone checkable row with no sub-options and no sorting. Its checkbox reads inverted
 * compared to a [FilterOption]: while unchecked, everything [matches] accepts is hidden.
 */
data class FilterToggle(
    override val label: String,
    val matches: (Nonogram) -> Boolean,
) : FilterEntry

/**
 * Unchecked (rather than checked) labels are stored so that the default [FilterSortState] means
 * "everything checked" and options added later default to checked. Options and toggles share this
 * one namespace, so their labels must not collide. [query] and [sizeRange] are the Filter screen's
 * part: a name search and a bound on the puzzle's longer side, applied ahead of the entries.
 */
data class FilterSortState(
    val sortAttribute: String? = null,
    val sortDirection: SortDirection? = null,
    val uncheckedLabels: Set<String> = emptySet(),
    val query: String = "",
    val sizeRange: IntRange = FULL_SIZE_RANGE,
) {

    /** True when the Filter screen holds something that narrows the list. */
    val hasSearch: Boolean get() = query.isNotBlank() || sizeRange != FULL_SIZE_RANGE

    fun isChecked(label: String): Boolean = label !in uncheckedLabels

    fun withMinSize(min: Int): FilterSortState =
        copy(sizeRange = min.coerceIn(MIN_NONOGRAM_SIDE, sizeRange.last)..sizeRange.last)

    fun withMaxSize(max: Int): FilterSortState =
        copy(sizeRange = sizeRange.first..max.coerceIn(sizeRange.first, MAX_NONOGRAM_SIDE))

    fun withSizeRange(range: IntRange): FilterSortState = copy(
        sizeRange = range.first.coerceIn(FULL_SIZE_RANGE)..range.last.coerceIn(FULL_SIZE_RANGE),
    )

    /** Cycles the sort through none -> DESC -> ASC -> none, clearing any other attribute's sort. */
    fun cycleSort(attribute: String): FilterSortState = when {
        sortAttribute != attribute ->
            copy(sortAttribute = attribute, sortDirection = SortDirection.DESC)

        sortDirection == SortDirection.DESC -> copy(sortDirection = SortDirection.ASC)
        else -> copy(sortAttribute = null, sortDirection = null)
    }

    fun toggle(label: String): FilterSortState = copy(
        uncheckedLabels = if (label in uncheckedLabels) {
            uncheckedLabels - label
        } else {
            uncheckedLabels + label
        },
    )

    /** True when [sortAttribute] names an attribute [entries] actually sorts by. */
    fun sortsBy(entries: List<FilterEntry>): Boolean = sortAttributeIn(entries) != null

    fun applyTo(nonograms: List<Nonogram>, entries: List<FilterEntry>): List<Nonogram> {
        val filtered = nonograms.filter { nonogram ->
            matchesSearch(nonogram) && entries.all { entry ->
                when (entry) {
                    is FilterAttribute ->
                        entry.options.any { isChecked(it.label) && it.matches(nonogram) }

                    is FilterToggle -> isChecked(entry.label) || !entry.matches(nonogram)
                }
            }
        }
        val attribute = sortAttributeIn(entries) ?: return filtered
        return filtered.sortedWith(
            if (sortDirection == SortDirection.ASC) attribute.ascending else attribute.ascending.reversed()
        )
    }

    private fun matchesSearch(nonogram: Nonogram): Boolean {
        val needle = query.trim()
        val nameMatches = needle.isEmpty() || nonogram.name?.contains(needle, ignoreCase = true) == true
        return nameMatches && nonogram.longSide in sizeRange
    }

    private fun sortAttributeIn(entries: List<FilterEntry>): FilterAttribute? =
        entries.filterIsInstance<FilterAttribute>().firstOrNull { it.label == sortAttribute }
}
