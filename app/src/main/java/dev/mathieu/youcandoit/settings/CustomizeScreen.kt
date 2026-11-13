@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.mathieu.youcandoit.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.ui.common.RowShape
import dev.mathieu.youcandoit.ui.common.PresetColors
import dev.mathieu.youcandoit.ui.common.labelRes
import dev.mathieu.youcandoit.ui.common.style

@Composable
fun CustomizeScreen(onBack: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val colors by viewModel.priorityColors.collectAsStateWithLifecycle()
    CustomizeContent(
        theme = theme,
        colors = colors,
        onBack = onBack,
        onThemeSelected = viewModel::onThemeSelected,
        onColorSelected = viewModel::onPriorityColorSelected,
        onResetColors = viewModel::onResetPriorityColors,
    )
}

/** The theme, and a color for each priority. */
@Composable
fun CustomizeContent(
    theme: ThemePreference,
    colors: PriorityColors,
    onBack: () -> Unit,
    onThemeSelected: (ThemePreference) -> Unit,
    onColorSelected: (Priority, Int?) -> Unit,
    onResetColors: () -> Unit,
) {
    // Most important first.
    val priorities = Priority.entries.reversed()
    var picking by rememberSaveable { mutableStateOf<Priority?>(null) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_customize)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(R.string.appearance_title)
            Card {
                Column(Modifier.selectableGroup()) {
                    ThemePreference.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .selectable(selected = option == theme, onClick = { onThemeSelected(option) }, role = Role.RadioButton)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == theme, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(option.labelRes()))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle(R.string.customize_priority_colors)
            Text(
                stringResource(R.string.customize_priority_colors_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Card {
                Column {
                    priorities.forEachIndexed { index, priority ->
                        val custom = colors.customOf(priority) != null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { picking = priority }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(Modifier.size(24.dp).background(colors.style(priority, dark).dot, CircleShape))
                            Text(stringResource(priority.labelRes()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(if (custom) R.string.color_custom else R.string.color_original),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index < priorities.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    }
                }
            }
            TextButton(onClick = onResetColors, enabled = !colors.isDefault) {
                Text(stringResource(R.string.action_reset_colors))
            }
        }
    }

    picking?.let { priority ->
        ColorDialog(
            priority = priority,
            initial = colors.style(priority, dark).dot,
            isCustom = colors.customOf(priority) != null,
            onPick = {
                onColorSelected(priority, it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** [onPick] gets the color as ARGB, or null for the priority's original color. */
@Composable
private fun ColorDialog(priority: Priority, initial: Color, isCustom: Boolean, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    // The exact color, kept apart from the picker's position so a quick pick isn't rounded through HSV.
    var color by remember { mutableStateOf(initial) }
    var hsv by remember { mutableStateOf(Hsv.of(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_priority_color, stringResource(priority.labelRes()))) },
        text = {
            // Scrolls on small screens; dragging in the picker itself doesn't scroll.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).background(color, CircleShape))
                    Text(
                        "#%06X".format(color.toArgb() and 0xFFFFFF),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onPick(null) }, enabled = isCustom) { Text(stringResource(R.string.action_use_original)) }
                }
                ColorPicker(
                    hsv = hsv,
                    onChange = {
                        hsv = it
                        color = it.toColor()
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetColors.forEach { (preset, name) ->
                        Swatch(
                            color = preset,
                            name = stringResource(name),
                            selected = preset.toArgb() == color.toArgb(),
                            onClick = {
                                color = preset
                                hsv = Hsv.of(preset)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(color.toArgb()) }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Swatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .padding(3.dp)
            .background(color, CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            // The swatch alone says nothing to a screen reader.
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RowShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth(), content = content)
}

private fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.SYSTEM -> R.string.theme_system
    ThemePreference.LIGHT -> R.string.theme_light
    ThemePreference.DARK -> R.string.theme_dark
}
