package com.lightreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapActionTest {
    @Test
    fun normalModeUsesPreviousMenuNextRegions() {
        assertEquals(
            ReaderTapAction.PREVIOUS,
            readerTapAction(xFraction = .12f, yFraction = .5f, toolbarVisible = false, settingsVisible = false, fullScreenTapNext = false),
        )
        assertEquals(
            ReaderTapAction.MENU,
            readerTapAction(xFraction = .5f, yFraction = .5f, toolbarVisible = false, settingsVisible = false, fullScreenTapNext = false),
        )
        assertEquals(
            ReaderTapAction.NEXT,
            readerTapAction(xFraction = .88f, yFraction = .5f, toolbarVisible = false, settingsVisible = false, fullScreenTapNext = false),
        )
    }

    @Test
    fun fullScreenTapModeUsesBothSidesForNextAndNarrowCenterForMenu() {
        assertEquals(
            ReaderTapAction.NEXT,
            readerTapAction(xFraction = .12f, yFraction = .5f, toolbarVisible = false, settingsVisible = false, fullScreenTapNext = true),
        )
        assertEquals(
            ReaderTapAction.MENU,
            readerTapAction(xFraction = .5f, yFraction = .5f, toolbarVisible = false, settingsVisible = false, fullScreenTapNext = true),
        )
        assertEquals(
            ReaderTapAction.NEXT,
            readerTapAction(xFraction = .88f, yFraction = .5f, toolbarVisible = false, settingsVisible = false, fullScreenTapNext = true),
        )
    }

    @Test
    fun visibleToolbarOnlyAllowsCenterMenuTapAndDoesNotStealControls() {
        assertEquals(
            ReaderTapAction.NONE,
            readerTapAction(xFraction = .12f, yFraction = .5f, toolbarVisible = true, settingsVisible = false, fullScreenTapNext = true),
        )
        assertEquals(
            ReaderTapAction.MENU,
            readerTapAction(xFraction = .5f, yFraction = .5f, toolbarVisible = true, settingsVisible = false, fullScreenTapNext = true),
        )
        assertEquals(
            ReaderTapAction.NONE,
            readerTapAction(xFraction = .5f, yFraction = .92f, toolbarVisible = true, settingsVisible = false, fullScreenTapNext = true),
        )
    }

    @Test
    fun visibleSettingsPanelKeepsBottomOverlayFromBeingHandledAsBodyTap() {
        assertEquals(
            ReaderTapAction.MENU,
            readerTapAction(xFraction = .5f, yFraction = .45f, toolbarVisible = true, settingsVisible = true, fullScreenTapNext = true),
        )
        assertEquals(
            ReaderTapAction.NONE,
            readerTapAction(xFraction = .5f, yFraction = .7f, toolbarVisible = true, settingsVisible = true, fullScreenTapNext = true),
        )
    }
}
