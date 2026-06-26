package com.lightreader.app.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsMigrationTest {
    @Test
    fun historicalDefaultSpacingValuesMoveToUcLikeDefaults() {
        assertEquals(20f, migratedLayoutValue(28f, usesModernDefaults = false, historicalDefault = 28f, newDefault = 20f), 0f)
        assertEquals(46f, migratedLayoutValue(64f, usesModernDefaults = false, historicalDefaults = listOf(64f, 52f, 48f), newDefault = 46f), 0f)
        assertEquals(46f, migratedLayoutValue(56f, usesModernDefaults = false, historicalDefaults = listOf(56f, 48f, 42f), newDefault = 46f), 0f)
        assertEquals(46f, migratedLayoutValue(52f, usesModernDefaults = false, historicalDefaults = listOf(64f, 52f, 48f), newDefault = 46f), 0f)
        assertEquals(46f, migratedLayoutValue(42f, usesModernDefaults = false, historicalDefaults = listOf(56f, 48f, 42f), newDefault = 46f), 0f)
        assertEquals(46f, migratedLayoutValue(48f, usesModernDefaults = false, historicalDefaults = listOf(64f, 52f, 48f), newDefault = 46f), 0f)
    }

    @Test
    fun customAndModernSpacingValuesArePreserved() {
        assertEquals(32f, migratedLayoutValue(32f, usesModernDefaults = false, historicalDefault = 28f, newDefault = 20f), 0f)
        assertEquals(28f, migratedLayoutValue(28f, usesModernDefaults = true, historicalDefault = 28f, newDefault = 20f), 0f)
        assertEquals(20f, migratedLayoutValue(null, usesModernDefaults = true, historicalDefault = 28f, newDefault = 20f), 0f)
        assertEquals(48f, migratedLayoutValue(48f, usesModernDefaults = true, historicalDefaults = listOf(64f, 52f, 48f), newDefault = 46f), 0f)
    }

    @Test
    fun oldDefaultJustificationMovesToEnabled() {
        assertEquals(true, migratedJustifiedValue(null, usesModernDefaults = false))
        assertEquals(true, migratedJustifiedValue(false, usesModernDefaults = false))
        assertEquals(true, migratedJustifiedValue(true, usesModernDefaults = false))
        assertEquals(false, migratedJustifiedValue(false, usesModernDefaults = true))
    }
}
