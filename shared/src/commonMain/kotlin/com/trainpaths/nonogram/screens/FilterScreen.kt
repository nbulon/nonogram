package com.trainpaths.nonogram.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.AppButton
import com.trainpaths.nonogram.BUTTON_HEIGHT
import com.trainpaths.nonogram.BUTTON_SHAPE
import com.trainpaths.nonogram.classes.MAX_NONOGRAM_SIDE
import com.trainpaths.nonogram.classes.MIN_NONOGRAM_SIDE
import com.trainpaths.nonogram.classes.sanitizeNameInput
import com.trainpaths.nonogram.filter.FULL_SIZE_RANGE
import com.trainpaths.nonogram.filter.FilterSortState
import com.trainpaths.nonogram.icons.close
import com.trainpaths.nonogram.icons.filter
import com.trainpaths.nonogram.icons.reset_settings
import com.trainpaths.nonogram.navigation.TopAppBar
import com.trainpaths.nonogram.outlinedFieldColors
import kotlin.math.roundToInt

/**
 * The dropdown's "More" page: a name search and a min–max bound on the puzzle's longer side. Every
 * edit goes straight to [onChange], so "Search" is just a way back — the list is already filtered.
 */
@Composable
fun FilterScreen(
    state: FilterSortState,
    onChange: (FilterSortState) -> Unit,
    onBack: () -> Unit,
) {
    var minText by remember { mutableStateOf(state.sizeRange.first.toString()) }
    var maxText by remember { mutableStateOf(state.sizeRange.last.toString()) }
    val textFieldColors = outlinedFieldColors()

    fun syncTexts(range: IntRange) {
        minText = range.first.toString()
        maxText = range.last.toString()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(
            titleIcon = filter,
            onBack = onBack,
            backArrow = true,
            showSettings = true,
        )

        Column(
            modifier = Modifier.fillMaxHeight().width(260.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onChange(state.copy(query = sanitizeNameInput(it))) },
                label = { Text("Search by name") },
                placeholder = { Text("Name…") },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onChange(state.copy(query = "")) }) {
                            Icon(imageVector = close, contentDescription = "Clear search")
                        }
                    }
                } else null,
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Size",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SizeField(
                    value = minText,
                    onValueChange = { text ->
                        minText = text.filter { c -> c.isDigit() }.take(2)
                        minText.toIntOrNull()?.let { onChange(state.withMinSize(it)) }
                    },
                    label = "Min",
                    colors = textFieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (!it.isFocused) syncTexts(state.sizeRange) },
                )
                SizeField(
                    value = maxText,
                    onValueChange = { text ->
                        maxText = text.filter { c -> c.isDigit() }.take(2)
                        maxText.toIntOrNull()?.let { onChange(state.withMaxSize(it)) }
                    },
                    label = "Max",
                    colors = textFieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (!it.isFocused) syncTexts(state.sizeRange) },
                )
            }

            RangeSlider(
                value = state.sizeRange.first.toFloat()..state.sizeRange.last.toFloat(),
                onValueChange = { range ->
                    val next = state.withSizeRange(
                        range.start.roundToInt()..range.endInclusive.roundToInt()
                    )
                    syncTexts(next.sizeRange)
                    onChange(next)
                },
                valueRange = MIN_NONOGRAM_SIDE.toFloat()..MAX_NONOGRAM_SIDE.toFloat(),
                steps = MAX_NONOGRAM_SIDE - MIN_NONOGRAM_SIDE - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onPrimary,
                    activeTrackColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTrackColor = MaterialTheme.colorScheme.secondary,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppButton(
                    text = "Filter",
                    onClick = onBack,
                    modifier = Modifier.weight(3f),
                )
                Button(
                    onClick = {
                        syncTexts(FULL_SIZE_RANGE)
                        onChange(state.copy(query = "", sizeRange = FULL_SIZE_RANGE))
                    },
                    enabled = state.hasSearch,
                    shape = BUTTON_SHAPE,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(BUTTON_HEIGHT),
                ) {
                    Icon(imageVector = reset_settings, contentDescription = "Reset filters")
                }
            }
        }
    }
}
