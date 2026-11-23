package com.music.amber.ui.components

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.amber.data.settings.PlaylistFolder
import com.music.amber.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

private sealed interface FolderDialog {
    data object Create : FolderDialog
    data object Rename : FolderDialog
    data object Delete : FolderDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistFolderBar(
    folders: List<PlaylistFolder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Boolean,
    onRename: (String, String) -> Boolean,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    val selected = folders.firstOrNull { it.id == selectedId }

    LazyRow(
        modifier = modifier.padding(bottom = 12.dp),
        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Res.string.folder_all)) },
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected = folder.id == selected?.id,
                onClick = { onSelect(folder.id) },
                label = { Text(folder.name) },
            )
        }
        item(key = "new") {
            AssistChip(
                onClick = { dialog = FolderDialog.Create },
                label = { Text(stringResource(Res.string.folder_new)) },
            )
        }
        if (selected != null) {
            item(key = "rename") {
                TextButton(onClick = { dialog = FolderDialog.Rename }) {
                    Text(stringResource(Res.string.folder_rename))
                }
            }
            item(key = "delete") {
                TextButton(onClick = { dialog = FolderDialog.Delete }) {
                    Text(stringResource(Res.string.folder_delete))
                }
            }
        }
    }

    when (dialog) {
        FolderDialog.Create -> FolderNameDialog(
            title = stringResource(Res.string.folder_new),
            confirmLabel = stringResource(Res.string.folder_create),
            initial = "",
            onConfirm = { name -> onCreate(name) },
            onDismiss = { dialog = null },
        )
        FolderDialog.Rename -> if (selected != null) {
            FolderNameDialog(
                title = stringResource(Res.string.folder_rename),
                confirmLabel = stringResource(Res.string.folder_save),
                initial = selected.name,
                onConfirm = { name -> onRename(selected.id, name) },
                onDismiss = { dialog = null },
            )
        }
        FolderDialog.Delete -> if (selected != null) {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(Res.string.folder_delete)) },
                text = { Text(stringResource(Res.string.folder_delete_confirm, selected.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        onSelect(null)
                        onDelete(selected.id)
                        dialog = null
                    }) { Text(stringResource(Res.string.folder_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) { Text(stringResource(Res.string.cancel)) }
                },
            )
        }
        null -> Unit
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var refused by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    refused = false
                },
                singleLine = true,
                isError = refused,
                label = { Text(stringResource(Res.string.folder_name)) },
                supportingText = if (refused) {
                    { Text(stringResource(Res.string.folder_name_refused)) }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (onConfirm(name)) onDismiss() else refused = true }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
