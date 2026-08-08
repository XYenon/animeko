/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.compose.ui.SystemTheme
import com.jthemedetecor.OsThemeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.window.LinuxSystemThemeMonitor
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.isLinux

/**
 * Tracks the OS light/dark preference for [me.him188.ani.app.data.models.preference.DarkMode.AUTO].
 *
 * On Linux this prefers the XDG Desktop Portal `org.freedesktop.appearance` / `color-scheme` setting
 * (works across GNOME, KDE, niri, Sway, Hyprland, etc.) and falls back to [OsThemeDetector]
 * (`gsettings`) when the portal is unavailable or reports no preference.
 */
class SystemThemeDetector {
    private val detector = OsThemeDetector.getDetector()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _current = MutableStateFlow(isDarkToTheme(detector.isDark))
    val current: StateFlow<SystemTheme> = _current.asStateFlow()

    init {
        if (currentPlatformDesktop().isLinux()) {
            // Start the portal listener immediately; prefer its value over gsettings when present.
            val portalTheme = LinuxSystemThemeMonitor.systemTheme
            detector.registerListener { isDark ->
                if (portalTheme.value == null) {
                    _current.value = isDarkToTheme(isDark)
                }
            }
            scope.launch {
                portalTheme.collect { theme ->
                    _current.value = theme ?: isDarkToTheme(detector.isDark)
                }
            }
        } else {
            detector.registerListener {
                _current.value = isDarkToTheme(it)
            }
        }
    }

    private fun isDarkToTheme(isDark: Boolean): SystemTheme {
        return if (isDark) {
            SystemTheme.Dark
        } else {
            SystemTheme.Light
        }
    }
}
