package com.trainpaths.nonogram.filter

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.icons.arrow_drop_down
import com.trainpaths.nonogram.icons.filter
import com.trainpaths.nonogram.icons.more_horiz

private val ICON_SIZE = 24.dp
private val CORNER = 8.dp
private val BUTTON_SHAPE = RoundedCornerShape(topStart = CORNER, topEnd = CORNER)
private val MENU_SHAPE = RoundedCornerShape(
    topStart = 0.dp,
    topEnd = CORNER,
    bottomEnd = CORNER,
    bottomStart = CORNER,
)
private val ENTRY_HEIGHT = 40.dp
private val ITEM_HEIGHT = 32.dp

@Composable
private fun menuItemColors() = MenuDefaults.itemColors(
    textColor = MaterialTheme.colorScheme.secondary,
    leadingIconColor = MaterialTheme.colorScheme.secondary,
    trailingIconColor = MaterialTheme.colorScheme.secondary,
)

/**
 * Top-bar button opening the filter/sort dropdown. Edits are collected in a draft and handed to
 * [onApply] only when the menu is dismissed, so the grid re-filters in one step on exit. The
 * closing "More" row applies the draft the same way before [onMore] opens the Filter screen.
 */
@Composable
fun FilterMenuButton(
    entries: List<FilterEntry>,
    state: FilterSortState,
    onApply: (FilterSortState) -> Unit,
    onMore: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(state) }

    val animationSpec: AnimationSpec<Color> = tween(100)

    val container by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.outline else Color.Transparent,
        animationSpec = animationSpec,
        label = "filterButtonContainer",
    )
    val content by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        animationSpec = animationSpec,
        label = "filterButtonContent",
    )

    Box {
        IconButton(
            onClick = {
                draft = state
                expanded = true
            },
            modifier = Modifier.background(container, BUTTON_SHAPE),
            colors = IconButtonDefaults.iconButtonColors(contentColor = content),
        ) {
            Icon(
                imageVector = filter,
                contentDescription = "Filter and sort",
                modifier = Modifier.size(32.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onApply(draft)
            },
            shape = MENU_SHAPE,
            containerColor = MaterialTheme.colorScheme.outline,
        ) {
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (entry is FilterAttribute) {
                    SortItem(
                        attribute = entry,
                        state = draft,
                        onClick = { draft = draft.cycleSort(entry.label) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                    entry.options.forEach { option ->
                        CheckItem(
                            label = option.label,
                            checked = draft.isChecked(option.label),
                            onClick = { draft = draft.toggle(option.label) },
                        )
                    }
                } else {
                    CheckItem(
                        label = entry.label,
                        checked = draft.isChecked(entry.label),
                        onClick = { draft = draft.toggle(entry.label) },
                        isEntry = true,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.secondary,
            )
            MoreItem(
                active = draft.hasSearch,
                onClick = {
                    expanded = false
                    onApply(draft)
                    onMore()
                },
            )
        }
    }
}

/** Opens the Filter screen; its icon lights up while a search or size range is narrowing the list. */
@Composable
private fun MoreItem(active: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        modifier = Modifier.height(ENTRY_HEIGHT),
        text = {
            Text(
                "More",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        },
        onClick = onClick,
        trailingIcon = {
            Icon(
                imageVector = more_horiz,
                contentDescription = if (active) "Search and size filter active" else "Search and size filter",
                modifier = Modifier
                    .size(ICON_SIZE),
            )
        },
        colors = menuItemColors(),
    )
}

@Composable
private fun SortItem(
    attribute: FilterAttribute,
    state: FilterSortState,
    onClick: () -> Unit,
) {
    val selected = state.sortAttribute == attribute.label
    val rotation by animateFloatAsState(
        targetValue = if (selected && state.sortDirection == SortDirection.ASC) 180f else 0f,
        label = "chevron",
    )
    DropdownMenuItem(
        modifier = Modifier.height(ENTRY_HEIGHT),
        text = {
            Text(
                attribute.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        },
        onClick = onClick,
        trailingIcon = {
            Icon(
                imageVector = arrow_drop_down,
                contentDescription = sortDescription(attribute.label, selected, state.sortDirection),
                modifier = Modifier
                    .size(32.dp)
                    .alpha(if (selected) 1f else 0.4f)
                    .rotate(rotation),
            )
        },
        colors = menuItemColors(),
    )
}

/**
 * A checkbox row — used both for an attribute's values and, with [isEntry], for a standalone
 * [FilterToggle], which sits at attribute level and so is styled like one.
 */
@Composable
private fun CheckItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    isEntry: Boolean = false,
) {
    DropdownMenuItem(
        modifier = Modifier.height(if (isEntry) ENTRY_HEIGHT else ITEM_HEIGHT),
        text = {
            Text(
                label,
                style = if (isEntry) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            )
        },
        onClick = onClick,
        leadingIcon = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.size(ICON_SIZE),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                ),
            )
        },
        trailingIcon = { Spacer(Modifier.size(ICON_SIZE)) },
        colors = menuItemColors(),
    )
}

private fun sortDescription(label: String, selected: Boolean, direction: SortDirection?): String =
    when {
        !selected -> "Sort by $label"
        direction == SortDirection.ASC -> "$label, ascending"
        else -> "$label, descending"
    }
