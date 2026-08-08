/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.compose.ui.SystemTheme
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxSystemThemeMonitorTest {
    @Test
    fun `maps portal prefer-dark to SystemTheme Dark`() {
        assertEquals(SystemTheme.Dark, 1.toSystemThemeFromPortalColorScheme())
        assertEquals(SystemTheme.Dark, Variant(UInt32(1)).toSystemTheme())
    }

    @Test
    fun `maps portal prefer-light to SystemTheme Light`() {
        assertEquals(SystemTheme.Light, 2.toSystemThemeFromPortalColorScheme())
        assertEquals(SystemTheme.Light, Variant(UInt32(2)).toSystemTheme())
    }

    @Test
    fun `maps portal no-preference to null`() {
        assertNull(0.toSystemThemeFromPortalColorScheme())
        assertNull(Variant(UInt32(0)).toSystemTheme())
    }

    @Test
    fun `maps unknown portal values to null`() {
        assertNull(3.toSystemThemeFromPortalColorScheme())
        assertNull(Variant(UInt32(99)).toSystemTheme())
    }

    @Test
    fun `unwraps deprecated Read nested variant`() {
        assertEquals(
            SystemTheme.Dark,
            Variant(Variant(UInt32(1))).toSystemTheme(),
        )
    }

    @Test
    fun `accepts plain integer variants`() {
        assertEquals(SystemTheme.Light, Variant(2).toSystemTheme())
        assertEquals(SystemTheme.Dark, Variant(1L).toSystemTheme())
    }
}
