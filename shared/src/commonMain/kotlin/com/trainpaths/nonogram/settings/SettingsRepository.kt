package com.trainpaths.nonogram.settings

import com.russhwolf.settings.Settings
import com.trainpaths.nonogram.ColorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_THEME = "color_theme"
private const val KEY_SHOW_ALL_NAMES = "show_names"
private const val KEY_AUTO_CROSS = "auto_cross_lines"

class SettingsRepository(private val settings: Settings) {

    private val _theme = MutableStateFlow(ColorTheme.fromKey(settings.getStringOrNull(KEY_THEME)))
    val theme: StateFlow<ColorTheme> = _theme.asStateFlow()

    private val _showNames = MutableStateFlow(settings.getBoolean(KEY_SHOW_ALL_NAMES, false))
    val showNames: StateFlow<Boolean> = _showNames.asStateFlow()

    private val _autoCrossLines = MutableStateFlow(settings.getBoolean(KEY_AUTO_CROSS, false))
    val autoCrossLines: StateFlow<Boolean> = _autoCrossLines.asStateFlow()

    fun setTheme(theme: ColorTheme) {
        if (_theme.value == theme) return
        _theme.value = theme
        settings.putString(KEY_THEME, theme.name)
    }

    fun setShowAllNames(value: Boolean) {
        if (_showNames.value == value) return
        _showNames.value = value
        settings.putBoolean(KEY_SHOW_ALL_NAMES, value)
    }

    fun setAutoCrossLines(value: Boolean) {
        if (_autoCrossLines.value == value) return
        _autoCrossLines.value = value
        settings.putBoolean(KEY_AUTO_CROSS, value)
    }
}
