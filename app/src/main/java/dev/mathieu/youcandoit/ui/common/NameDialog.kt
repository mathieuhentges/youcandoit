package dev.mathieu.youcandoit.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.DomainError

/** Create / rename a category. */
@Composable
fun NameDialog(
    @StringRes title: Int,
    name: String,
    error: DomainError?,
    @StringRes confirmLabel: Int,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.label_category_name)) },
                singleLine = true,
                isError = error != null,
                supportingText = { if (error != null) Text(error.userMessage()) },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
