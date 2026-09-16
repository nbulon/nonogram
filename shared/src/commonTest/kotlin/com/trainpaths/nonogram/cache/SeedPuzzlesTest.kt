package com.trainpaths.nonogram.cache

import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.MAX_NONOGRAM_NAME_LENGTH
import com.trainpaths.nonogram.classes.isWellFormedGrid
import com.trainpaths.nonogram.classes.nameControl
import com.trainpaths.nonogram.classes.normalizeNonogramName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The contract `:seedTool` writes against. The file is generated, but a hand-edit that renumbered a puzzle
 * would otherwise be invisible until it reached a device and attached synced progress to the wrong picture.
 */
class SeedPuzzlesTest {

    @Test
    fun ids_areUniqueAndInTheDocumentIdRange() {
        val ids = SEED_PUZZLES.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate seed ids: $ids")
        ids.forEach { id ->
            assertTrue(id >= (1L shl 20) && id < (1L shl 53), "seed id $id is outside [2^20, 2^53)")
        }
    }

    @Test
    fun grids_areWellFormedAndBinary() {
        SEED_PUZZLES.forEach { seed ->
            assertTrue(seed.solution.isWellFormedGrid(), "seed ${seed.id} is ragged or out of range")
            assertTrue(
                seed.solution.all { row -> row.all { it == 0 || it == 1 } },
                "seed ${seed.id} has a cell that is neither filled nor empty",
            )
        }
    }

    @Test
    fun difficulties_areRealEnumEntries() {
        val known = Difficulty.entries.map { it.name }.toSet()
        SEED_PUZZLES.forEach { seed ->
            assertTrue(seed.difficulty in known, "seed ${seed.id} has difficulty '${seed.difficulty}'")
        }
    }

    @Test
    fun names_areAlreadyNormalized() {
        SEED_PUZZLES.forEach { seed ->
            val name = seed.name ?: return@forEach
            assertTrue(name.length <= MAX_NONOGRAM_NAME_LENGTH, "seed ${seed.id} has an over-long name")
            assertEquals(name, normalizeNonogramName(name), "seed ${seed.id}'s name is not normalized")
            assertNull(nameControl(name), "seed ${seed.id}'s name would be refused")
        }
    }
}
