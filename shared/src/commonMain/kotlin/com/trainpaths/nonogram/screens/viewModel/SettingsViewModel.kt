package com.trainpaths.nonogram.screens.viewModel

import androidx.lifecycle.ViewModel
import com.trainpaths.nonogram.ColorTheme
import com.trainpaths.nonogram.settings.SettingsRepository

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val theme = settingsRepository.theme
    val showAllNames = settingsRepository.showNames
    val autoCrossLines = settingsRepository.autoCrossLines
    fun selectTheme(theme: ColorTheme) = settingsRepository.setTheme(theme)
    fun setShowAllNames(value: Boolean) = settingsRepository.setShowAllNames(value)
    fun setAutoCrossLines(value: Boolean) = settingsRepository.setAutoCrossLines(value)
}
