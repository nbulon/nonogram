package com.trainpaths.nonogram.filter

import com.trainpaths.nonogram.classes.CardStatus
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.MAX_NONOGRAM_SIDE
import com.trainpaths.nonogram.classes.MIN_NONOGRAM_SIDE
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import com.trainpaths.nonogram.classes.cardStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OWNER_UID = "uid-7"

class FilterSortStateTest {

    private fun nonogram(
        id: Long,
        difficulty: Difficulty = Difficulty.EASY,
        authorUid: String = "",
        name: String? = null,
        width: Int = 1,
        height: Int = 1,
        publishStatus: PublishStatus = PublishStatus.NONE,
    ) = Nonogram(
        id = id,
        difficulty = difficulty,
        solution = List(height) { List(width) { 1 } },
        name = name,
        authorUid = authorUid,
        publishStatus = publishStatus,
    )

    private val easy = nonogram(1, Difficulty.EASY)
    private val hard = nonogram(2, Difficulty.HARD)
    private val medium = nonogram(3, Difficulty.MEDIUM)
    private val easyToo = nonogram(4, Difficulty.EASY)
    private val all = listOf(easy, hard, medium, easyToo)

    private val myHard = nonogram(5, Difficulty.HARD, OWNER_UID)
    private val myEasy = nonogram(6, Difficulty.EASY, OWNER_UID)
    private val mixed = listOf(easy, myHard, hard, myEasy)

    private val entries = NonogramFilters.forUser(OWNER_UID)
    private val difficulty = NonogramFilters.DIFFICULTY.label
    private val personal = NonogramFilters.PERSONAL

    @Test
    fun cycleSort_runsThroughDescAscAndBackToNone() {
        val desc = FilterSortState().cycleSort(difficulty)
        assertEquals(difficulty, desc.sortAttribute)
        assertEquals(SortDirection.DESC, desc.sortDirection)

        val asc = desc.cycleSort(difficulty)
        assertEquals(difficulty, asc.sortAttribute)
        assertEquals(SortDirection.ASC, asc.sortDirection)

        val cleared = asc.cycleSort(difficulty)
        assertNull(cleared.sortAttribute)
        assertNull(cleared.sortDirection)
    }

    @Test
    fun cycleSort_onAnotherAttribute_takesOverTheSort() {
        val state = FilterSortState().cycleSort(difficulty).cycleSort("Size")

        assertEquals("Size", state.sortAttribute)
        assertEquals(SortDirection.DESC, state.sortDirection)
    }

    @Test
    fun defaultState_keepsEveryPuzzleInOriginalOrder() {
        assertEquals(all, FilterSortState().applyTo(all, entries))
    }

    @Test
    fun uncheckingOneDifficulty_removesOnlyThosePuzzles() {
        val state = FilterSortState().toggle(Difficulty.EASY.label)

        assertTrue(!state.isChecked(Difficulty.EASY.label))
        assertEquals(listOf(hard, medium), state.applyTo(all, entries))
    }

    @Test
    fun uncheckingEveryDifficulty_yieldsNothing() {
        val state = Difficulty.entries.fold(FilterSortState()) { acc, d -> acc.toggle(d.label) }

        assertEquals(emptyList(), state.applyTo(all, entries))
    }

    @Test
    fun toggle_isReversible() {
        val state = FilterSortState().toggle(Difficulty.HARD.label).toggle(Difficulty.HARD.label)

        assertEquals(FilterSortState(), state)
        assertEquals(all, state.applyTo(all, entries))
    }

    @Test
    fun ascendingSort_ordersEasyFirst_andKeepsTiesStable() {
        val state = FilterSortState(difficulty, SortDirection.ASC)

        assertEquals(listOf(easy, easyToo, medium, hard), state.applyTo(all, entries))
    }

    @Test
    fun descendingSort_isTheReverseOrderOfDifficulty() {
        val state = FilterSortState(difficulty, SortDirection.DESC)

        assertEquals(listOf(hard, medium, easy, easyToo), state.applyTo(all, entries))
    }

    @Test
    fun sortAndFilter_composeInOnePass() {
        val state = FilterSortState(difficulty, SortDirection.ASC).toggle(Difficulty.MEDIUM.label)

        assertEquals(listOf(easy, easyToo, hard), state.applyTo(all, entries))
    }

    @Test
    fun unknownSortAttribute_leavesOrderUntouched() {
        val state = FilterSortState("nope", SortDirection.ASC)

        assertEquals(all, state.applyTo(all, entries))
    }

    @Test
    fun personalChecked_byDefault_keepsYourOwnPuzzles() {
        assertEquals(mixed, FilterSortState().applyTo(mixed, entries))
    }

    @Test
    fun uncheckingPersonal_removesOnlyYourOwnPuzzles() {
        val state = FilterSortState().toggle(personal)

        assertEquals(listOf(easy, hard), state.applyTo(mixed, entries))
    }

    @Test
    fun personal_appliesToTheOwnerOnly() {
        val someoneElse = NonogramFilters.forUser("uid-8")
        val state = FilterSortState().toggle(personal)

        assertEquals(mixed, state.applyTo(mixed, someoneElse))
    }

    @Test
    fun uncheckingPersonalAndADifficulty_appliesBothConstraints() {
        val state = FilterSortState().toggle(personal).toggle(Difficulty.HARD.label)

        assertEquals(listOf(easy), state.applyTo(mixed, entries))
    }

    @Test
    fun sortsBy_isTrueOnlyForAnAttributeInTheEntries() {
        assertEquals(false, FilterSortState().sortsBy(entries))
        assertEquals(false, FilterSortState(personal, SortDirection.ASC).sortsBy(entries))
        assertEquals(false, FilterSortState("nope", SortDirection.ASC).sortsBy(entries))
        assertEquals(
            true,
            FilterSortState(NonogramFilters.DIFFICULTY.label, SortDirection.ASC).sortsBy(entries),
        )
    }

    @Test
    fun sortingByPersonal_isANoOp_becauseItIsNotAnAttribute() {
        val state = FilterSortState(personal, SortDirection.ASC)

        assertEquals(mixed, state.applyTo(mixed, entries))
    }

    // --- name search ---

    private val cat = nonogram(10, name = "Black Cat")
    private val caterpillar = nonogram(11, name = "caterpillar")
    private val dog = nonogram(12, name = "Dog")
    private val unnamed = nonogram(13)
    private val named = listOf(cat, caterpillar, dog, unnamed)

    @Test
    fun blankQuery_keepsEveryPuzzle_unnamedIncluded() {
        assertEquals(named, FilterSortState(query = "   ").applyTo(named, entries))
        assertFalse(FilterSortState().hasSearch)
    }

    @Test
    fun query_isACaseInsensitiveSubstringMatch() {
        assertEquals(listOf(cat, caterpillar), FilterSortState(query = "CAT").applyTo(named, entries))
    }

    @Test
    fun query_isTrimmed_andDropsUnnamedPuzzles() {
        val state = FilterSortState(query = " dog ")

        assertTrue(state.hasSearch)
        assertEquals(listOf(dog), state.applyTo(named, entries))
    }

    // --- size range ---

    private val tiny = nonogram(20, width = 5, height = 5)
    private val wide = nonogram(21, width = 25, height = 10)
    private val huge = nonogram(22, width = 60, height = 60)
    private val sized = listOf(tiny, wide, huge)

    @Test
    fun sizeRange_boundsThePuzzlesLongerSide_inclusively() {
        assertEquals(listOf(wide), FilterSortState(sizeRange = 20..30).applyTo(sized, entries))
        assertEquals(listOf(wide), FilterSortState(sizeRange = 25..25).applyTo(sized, entries))
        assertEquals(listOf(tiny), FilterSortState(sizeRange = 5..20).applyTo(sized, entries))
        assertEquals(sized, FilterSortState(sizeRange = FULL_SIZE_RANGE).applyTo(sized, entries))
    }

    @Test
    fun withMinSize_clampsToTheAllowedRange_andNeverPassesTheMax() {
        val state = FilterSortState(sizeRange = 10..20)

        assertEquals(MIN_NONOGRAM_SIDE..20, state.withMinSize(1).sizeRange)
        assertEquals(15..20, state.withMinSize(15).sizeRange)
        assertEquals(20..20, state.withMinSize(50).sizeRange)
    }

    @Test
    fun withMaxSize_clampsToTheAllowedRange_andNeverPassesTheMin() {
        val state = FilterSortState(sizeRange = 10..20)

        assertEquals(10..MAX_NONOGRAM_SIDE, state.withMaxSize(99).sizeRange)
        assertEquals(10..15, state.withMaxSize(15).sizeRange)
        assertEquals(10..10, state.withMaxSize(2).sizeRange)
    }

    @Test
    fun withSizeRange_coercesBothEnds() {
        assertEquals(FULL_SIZE_RANGE, FilterSortState().withSizeRange(0..99).sizeRange)
        assertEquals(12..40, FilterSortState().withSizeRange(12..40).sizeRange)
        assertTrue(FilterSortState().withSizeRange(12..40).hasSearch)
    }

    // --- generator status ---

    private val invalid = nonogram(30, publishStatus = PublishStatus.NONE, name = "a")
    private val valid = nonogram(31, publishStatus = PublishStatus.VALID, name = "b")
    private val published = nonogram(32, publishStatus = PublishStatus.APPROVED, name = "a")
    private val pending = nonogram(33, publishStatus = PublishStatus.PENDING, name = "b")
    private val mine = listOf(published, invalid, pending, valid)
    private val generator = NonogramFilters.GENERATOR
    private val status = NonogramFilters.STATUS.label

    @Test
    fun cardStatus_followsPublishStatus() {
        assertEquals(CardStatus.INVALID, invalid.cardStatus)
        assertEquals(CardStatus.UNPUBLISHED, valid.cardStatus)
        assertEquals(CardStatus.UNPUBLISHED, pending.cardStatus)
        assertEquals(CardStatus.PUBLISHED, published.cardStatus)
        assertEquals(
            CardStatus.UNPUBLISHED,
            nonogram(34, publishStatus = PublishStatus.UNLISTED).cardStatus,
        )
    }

    @Test
    fun uncheckingPublic_removesOnlyApprovedPuzzles() {
        val state = FilterSortState().toggle(CardStatus.PUBLISHED.label)

        assertEquals(listOf(invalid, pending, valid), state.applyTo(mine, generator))
    }

    @Test
    fun statusSort_ordersInvalidValidPublic_andReversesDescending() {
        assertEquals(
            listOf(invalid, pending, valid, published),
            FilterSortState(status, SortDirection.ASC).applyTo(mine, generator),
        )
        assertEquals(
            listOf(published, pending, valid, invalid),
            FilterSortState(status, SortDirection.DESC).applyTo(mine, generator),
        )
        assertTrue(FilterSortState(status, SortDirection.ASC).sortsBy(generator))
    }

    @Test
    fun searchAndEntries_composeInOnePass() {
        val state = FilterSortState(query = "a").toggle(CardStatus.PUBLISHED.label)

        assertEquals(listOf(invalid), state.applyTo(mine, generator))
    }
}
