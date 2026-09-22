package com.trainpaths.nonogram.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersionTest {

    @Test
    fun parsesADesktopReleaseTag() {
        assertEquals("1.1.42", parseDesktopTag("desktop-v1.1.42"))
        assertEquals("1.1", parseDesktopTag("desktop-v1.1"))
    }

    @Test
    fun rejectsATagThatIsNotADesktopRelease() {
        assertNull(parseDesktopTag("v1.2.0"))
        assertNull(parseDesktopTag("android-v1.0.3"))
        assertNull(parseDesktopTag("desktop-v"))
        assertNull(parseDesktopTag("desktop-v1.1."))
        assertNull(parseDesktopTag("desktop-v1.1.0-rc1"))
    }

    @Test
    fun comparesComponentsNumerically() {
        assertTrue(isNewer("1.1.42", "1.1.5"))
        assertTrue(isNewer("1.2", "1.1.99"))
        assertTrue(isNewer("1.1.1", "1.1"))
    }

    @Test
    fun theSameOrAnOlderVersionIsNotNewer() {
        assertFalse(isNewer("1.1.42", "1.1.42"))
        assertFalse(isNewer("1.1", "1.1.0"))
        assertFalse(isNewer("1.1.5", "1.1.42"))
        assertFalse(isNewer("1.0.9", "1.1.0"))
    }

    @Test
    fun anUnparseableVersionIsNeverNewer() {
        assertFalse(isNewer("nightly", "1.1.0"))
        assertFalse(isNewer("1.1.0", "nightly"))
    }
}
