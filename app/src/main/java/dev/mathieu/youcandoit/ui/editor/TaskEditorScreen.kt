@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.mathieu.youcandoit.ui.editor

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.ui.common.NameDialog
import dev.mathieu.youcandoit.ui.common.formatDate
import dev.mathieu.youcandoit.ui.common.formatTime
import dev.mathieu.youcandoit.ui.common.labelRes
import dev.mathieu.youcandoit.ui.common.reminderLabel
import dev.mathieu.youcandoit.ui.common.style
import dev.mathieu.youcandoit.ui.common.userMessage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@Composable
fun TaskEditorScreen(
    onClose: () -> Unit,
    onAddSubTask: (TaskId) -> Unit = {},
    onEditTask: (TaskId) -> Unit = {},
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onClose()
    }
    TaskEditorContent(
        state = state,
        actions = viewModel,
        onClose = onClose,
        onAddSubTask = { viewModel.taskId?.let(onAddSubTask) },
        onEditTask = onEditTask,
    )
}

@Composable
fun TaskEditorContent(
    state: TaskEditorUiState,
    actions: TaskEditorActions,
    onClose: () -> Unit,
    onAddSubTask: () -> Unit = {},
    onEditTask: (TaskId) -> Unit = {},
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val addedText = state.justAdded?.let { pluralStringResource(R.plurals.message_added, it, it) }
    LaunchedEffect(state.justAdded) {
        if (addedText != null) {
            // Shown from its own scope: clearing the message changes this effect's key, which would
            // otherwise cancel the snackbar at once. Each add, even of the same count, gets its own.
            snackbarScope.launch { snackbar.showSnackbar(addedText) }
            actions.onAddedShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEditing) R.string.title_edit_task else R.string.title_new_task)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    Button(onClick = actions::onSave, enabled = state.canSave, modifier = Modifier.padding(end = 8.dp)) {
                        val count = state.newTitles.size
                        Text(
                            if (state.severalAtOnce && count > 0) pluralStringResource(R.plurals.action_add_tasks, count, count)
                            else stringResource(R.string.action_save),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val error = state.error
            if (error != null && error !is DomainError.BlankTitle) {
                Text(error.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (!state.isEditing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.label_several_at_once),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val switchLabel = stringResource(R.string.label_several_at_once)
                    Switch(
                        checked = state.severalAtOnce,
                        onCheckedChange = actions::onSeveralAtOnceChange,
                        modifier = Modifier.semantics { contentDescription = switchLabel },
                    )
                }
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = actions::onTitleChange,
                label = { Text(stringResource(if (state.severalAtOnce) R.string.label_titles else R.string.label_title)) },
                singleLine = !state.severalAtOnce,
                minLines = if (state.severalAtOnce) 4 else 1,
                // Each sentence, so every line of "several at once" starts with a capital too.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = error is DomainError.BlankTitle,
                supportingText = { if (error is DomainError.BlankTitle) Text(error.userMessage()) },
                modifier = Modifier.fillMaxWidth(),
            )

            // In "several" mode each line is a whole task; there's nowhere for one description.
            if (!state.severalAtOnce) {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = actions::onDescriptionChange,
                    label = { Text(stringResource(R.string.label_description)) },
                    placeholder = { Text(stringResource(R.string.hint_description)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(R.string.label_priority) {
                PriorityPicker(selected = state.priority, onSelect = actions::onPriorityChange)
            }

            // A sub task is always in its parent's category: there's nothing to pick.
            val parentTitle = state.parentTitle
            if (parentTitle != null) {
                Text(
                    stringResource(R.string.label_sub_task_of, parentTitle),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else Section(R.string.label_category) {
                CategoryPicker(
                    categories = state.categories,
                    selectedId = state.categoryId,
                    onSelect = actions::onCategoryChange,
                    onNew = actions::onNewCategoryClick,
                )
            }

            DeadlineSection(state = state, actions = actions)

            if (state.hasDeadline) {
                val requestNotificationPermission = rememberNotificationPermissionRequest()
                Section(R.string.label_reminder) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.reminderOptions.forEach { option ->
                            FilterChip(
                                selected = option == state.reminder,
                                onClick = {
                                    actions.onReminderChange(option)
                                    if (option != Reminder.None) requestNotificationPermission()
                                },
                                label = { Text(reminderLabel(option)) },
                            )
                        }
                    }
                    if (state.reminderFallsBackToDeadline) {
                        Text(
                            stringResource(R.string.note_reminder_falls_back),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.reminder != Reminder.None) ReminderPermissionNotes()
                }
            }

            if (state.canAddSubTasks || state.subTasks.isNotEmpty()) {
                Section(R.string.label_sub_tasks) {
                    state.subTasks.forEach { sub ->
                        Surface(
                            onClick = { onEditTask(sub.id) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(sub.priority.style().dot, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    sub.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (sub.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (sub.isDone) TextDecoration.LineThrough else null,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (state.canAddSubTasks) {
                        TextButton(onClick = onAddSubTask) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_add_sub_task))
                        }
                    }
                }
            }

            if (!state.isEditing) {
                OutlinedButton(onClick = actions::onSaveAndAddAnother, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_save_add_another))
                }
            }

            if (state.isEditing) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete_task))
                }
            }
        }
    }

    state.newCategory?.let { dialog ->
        NameDialog(
            title = R.string.dialog_new_category,
            name = dialog.name,
            error = dialog.error,
            confirmLabel = R.string.action_create,
            onNameChange = actions::onNewCategoryNameChange,
            onConfirm = actions::onNewCategoryConfirm,
            onDismiss = actions::onNewCategoryDismiss,
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dialog_delete_task)) },
            text = {
                val subTasks = state.subTasks.size
                val body = stringResource(R.string.dialog_delete_task_body)
                Text(if (subTasks == 0) body else body + " " + pluralStringResource(R.plurals.dialog_delete_task_sub_tasks, subTasks, subTasks))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Section(@StringRes label: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun PriorityPicker(selected: Priority, onSelect: (Priority) -> Unit) {
    // Least to most important, left to right: Urgent sits at the far right.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Priority.entries.forEach { priority ->
            val style = priority.style()
            val isSelected = priority == selected
            Surface(
                onClick = { onSelect(priority) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) style.container else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) style.content else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) style.dot else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .semantics { this.selected = isSelected },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(10.dp).background(style.dot, CircleShape))
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(priority.labelRes()), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: CategoryId?,
    onSelect: (CategoryId?) -> Unit,
    onNew: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val uncategorized = stringResource(R.string.title_uncategorized)
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: uncategorized
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(selectedName, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(uncategorized) }, onClick = {
                    onSelect(null)
                    expanded = false
                })
                categories.forEach { category ->
                    DropdownMenuItem(text = { Text(category.name) }, onClick = {
                        onSelect(category.id)
                        expanded = false
                    })
                }
            }
        }
        TextButton(onClick = onNew) { Text(stringResource(R.string.action_new_category_short)) }
    }
}

@Composable
private fun DeadlineSection(state: TaskEditorUiState, actions: TaskEditorActions) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val switchLabel = stringResource(R.string.label_has_deadline)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.label_deadline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.hasDeadline,
                onCheckedChange = actions::onDeadlineToggle,
                modifier = Modifier.semantics { contentDescription = switchLabel },
            )
        }
        val date = state.date
        if (state.hasDeadline && date != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickDate = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatDate(date), maxLines = 1)
                }
                OutlinedButton(onClick = { pickTime = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatTime(state.time), maxLines = 1)
                }
            }
            if (pickDate) {
                DeadlineDatePicker(
                    initial = date,
                    onPick = {
                        actions.onDateChange(it)
                        pickDate = false
                    },
                    onDismiss = { pickDate = false },
                )
            }
            if (pickTime) {
                DeadlineTimePicker(
                    initial = state.time,
                    onPick = {
                        actions.onTimeChange(it)
                        pickTime = false
                    },
                    onDismiss = { pickTime = false },
                )
            }
        }
    }
}

/** Days before today can't be picked. The DatePicker works in UTC midnights. */
private object FromToday : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(LocalDate.now())
}

@Composable
private fun DeadlineDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = FromToday,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun DeadlineTimePicker(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
