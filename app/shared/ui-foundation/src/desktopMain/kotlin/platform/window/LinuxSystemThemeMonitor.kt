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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.him188.ani.utils.logging.logger
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * Monitors the session-wide color scheme exposed by XDG Desktop Portal
 * (`org.freedesktop.appearance` / `color-scheme`).
 *
 * Portal settings are not tied to a window, so all Animeko windows share this single D-Bus listener
 * and [systemTheme] state. A `null` value means the portal is unavailable or reported no preference;
 * callers should keep their own fallback (e.g. [com.jthemedetecor.OsThemeDetector]).
 *
 * Values follow the FreeDesktop Settings portal:
 * - `0` — no preference
 * - `1` — prefer dark
 * - `2` — prefer light
 */
object LinuxSystemThemeMonitor {
    private const val PortalBusName = "org.freedesktop.portal.Desktop"
    private const val PortalObjectPath = "/org/freedesktop/portal/desktop"
    private const val AppearanceNamespace = "org.freedesktop.appearance"
    private const val ColorSchemeKey = "color-scheme"

    private val logger = logger<LinuxSystemThemeMonitor>()
    private val mutableSystemTheme = MutableStateFlow<SystemTheme?>(null)

    /**
     * Keeps the session-bus connection reachable for the process lifetime so signal delivery and
     * the transport threads are not GC'd after the initial read.
     */
    @Suppress("unused")
    @Volatile
    private var connection: Any? = null

    /**
     * Latest portal color-scheme preference, or `null` when unknown / no preference / portal unavailable.
     */
    val systemTheme: StateFlow<SystemTheme?> = mutableSystemTheme.asStateFlow()

    init {
        Thread(::listenForColorScheme, "Linux-system-theme").apply {
            isDaemon = true
            start()
        }
    }

    private fun listenForColorScheme() {
        runCatching {
            withDbusClassLoader {
                val dbusConnection = DBusConnectionBuilder.forSessionBus().build()
                connection = dbusConnection
                val settings = dbusConnection.getRemoteObject(
                    PortalBusName,
                    PortalObjectPath,
                    ColorSchemePortalSettings::class.java,
                )
                dbusConnection.addSigHandler(ColorSchemePortalSettings.SettingChanged::class.java, settings) { signal ->
                    if (signal.namespace == AppearanceNamespace && signal.key == ColorSchemeKey) {
                        updateColorScheme(signal.value)
                    }
                }
                updateColorScheme(settings.readColorScheme())
            }
        }.onFailure {
            logger.debug("Failed to read Linux color scheme from XDG Desktop Portal", it)
        }
    }

    private fun ColorSchemePortalSettings.readColorScheme(): Variant<*> {
        return runCatching { ReadOne(AppearanceNamespace, ColorSchemeKey) }
            .getOrElse { Read(AppearanceNamespace, ColorSchemeKey) }
    }

    private fun updateColorScheme(value: Variant<*>) {
        mutableSystemTheme.value = value.toSystemTheme()
    }

    private inline fun <T> withDbusClassLoader(block: () -> T): T {
        val currentThread = Thread.currentThread()
        val originalClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = DBusConnectionBuilder::class.java.classLoader
        return try {
            block()
        } finally {
            currentThread.contextClassLoader = originalClassLoader
        }
    }
}

/**
 * Maps a portal `color-scheme` variant to [SystemTheme].
 *
 * Returns `null` for no preference (`0`) or unrecognised values.
 */
internal fun Variant<*>.toSystemTheme(): SystemTheme? {
    val raw = unwrapColorSchemeVariant()
    val value = when (raw) {
        is UInt32 -> raw.toInt()
        is Number -> raw.toInt()
        else -> return null
    }
    return value.toSystemThemeFromPortalColorScheme()
}

/**
 * Portal `org.freedesktop.appearance` `color-scheme` integer mapping.
 */
internal fun Int.toSystemThemeFromPortalColorScheme(): SystemTheme? {
    return when (this) {
        1 -> SystemTheme.Dark
        2 -> SystemTheme.Light
        else -> null // 0 = no preference, or unknown
    }
}

private tailrec fun Variant<*>.unwrapColorSchemeVariant(): Any = when (val unwrapped = value) {
    is Variant<*> -> unwrapped.unwrapColorSchemeVariant()
    else -> unwrapped
}

@DBusInterfaceName("org.freedesktop.portal.Settings")
private interface ColorSchemePortalSettings : DBusInterface {
    @Suppress("FunctionName")
    fun Read(namespace: String, key: String): Variant<Variant<*>>

    @Suppress("FunctionName")
    fun ReadOne(namespace: String, key: String): Variant<*>

    class SettingChanged(
        path: String,
        val namespace: String,
        val key: String,
        val value: Variant<*>,
    ) : DBusSignal(path, namespace, key, value)
}
