package vn.sdevice.documentshub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.sdevice.documentshub.data.DocFile

@Composable
fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun NotesDialog(file: DocFile, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(file.id) { mutableStateOf(file.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghi chú", maxLines = 1) },
        text = {
            Column {
                Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 3,
                    maxLines = 8,
                    placeholder = { Text("Thêm ghi chú cho tài liệu này") }
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(text.trim()); onDismiss() }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun RenameFileDialog(file: DocFile, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    // Mặc định bôi đen phần tên, giữ nguyên đuôi tệp (.pdf, .jpg...)
    val dot = file.name.lastIndexOf('.')
    val baseEnd = if (dot > 0) dot else file.name.length
    var value by remember(file.id) { mutableStateOf(TextFieldValue(file.name, TextRange(0, baseEnd))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi tên tệp") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onSave(value.text.trim()); onDismiss() }, enabled = value.text.isNotBlank()) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun PickCategoryDialog(
    title: String,
    categories: List<String>,
    initial: String?,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(initial?.takeIf { it in categories } ?: categories.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                categories.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = c },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == c, onClick = { selected = c })
                        Text(c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selected); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun CategoryDialog(
    categories: List<String>,
    counts: Map<String, Int>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quản lý phân loại") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Tên phân loại mới") }
                    )
                    Button(
                        onClick = { onAdd(newName); newName = "" },
                        enabled = newName.isNotBlank()
                    ) { Text("Thêm") }
                }
                HorizontalDivider()
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    categories.forEach { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${counts[c] ?: 0} tài liệu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { renaming = c; renameText = c }) { Icon(Icons.Default.Edit, "Đổi tên") }
                            IconButton(onClick = { deleting = c }, enabled = categories.size > 1) {
                                Icon(Icons.Default.Delete, "Xóa")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Xong") } }
    )

    renaming?.let { old ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Đổi tên phân loại") },
            text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = { onRename(old, renameText); renaming = null }, enabled = renameText.isNotBlank()) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Hủy") } }
        )
    }
    deleting?.let { name ->
        ConfirmDialog(
            text = "Xóa phân loại \"$name\"? Các tài liệu trong đó sẽ được chuyển sang một phân loại khác, không bị xóa.",
            confirmLabel = "Xóa phân loại",
            onConfirm = { onDelete(name) },
            onDismiss = { deleting = null }
        )
    }
}
