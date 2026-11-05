package vn.sdevice.documentshub.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.sdevice.documentshub.data.DocFile
import vn.sdevice.documentshub.data.formatDate

private class Actions(
    val open: (DocFile) -> Unit,
    val notes: (DocFile) -> Unit,
    val move: (DocFile) -> Unit,
    val saveCopy: (DocFile) -> Unit,
    val external: (DocFile) -> Unit,
    val trash: (DocFile) -> Unit,
    val restore: (DocFile) -> Unit,
    val deleteForever: (DocFile) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val files by vm.files.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val saveCopy = rememberSaveCopy(vm)

    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showCats by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var notesFor by remember { mutableStateOf<DocFile?>(null) }
    var moveFor by remember { mutableStateOf<DocFile?>(null) }
    var deleteFor by remember { mutableStateOf<DocFile?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pending = uris
    }

    val nav = vm.nav
    val visible = remember(files, nav, vm.query, vm.type, vm.sort) {
        filterSort(files, nav, vm.query, vm.type, vm.sort)
    }
    val title = when (nav) {
        Nav.All -> "Tất cả tài liệu"
        Nav.Trash -> "Thùng rác"
        is Nav.Category -> nav.name
    }
    val inTrash = nav == Nav.Trash

    val actions = remember(vm, saveCopy) {
        Actions(
            open = { vm.openId = it.id },
            notes = { notesFor = it },
            move = { moveFor = it },
            saveCopy = { saveCopy(it) },
            external = { vm.openExternal(it) },
            trash = { vm.trash(it) },
            restore = { vm.restore(it) },
            deleteForever = { deleteFor = it }
        )
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = searching && !drawer.isOpen) { searching = false; vm.query = "" }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    vm = vm,
                    files = files,
                    cats = cats,
                    onNavigate = { vm.nav = it; scope.launch { drawer.close() } },
                    onManage = { showCats = true; scope.launch { drawer.close() } },
                    onSignOut = { confirmSignOut = true }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Menu") }
                    },
                    title = {
                        if (searching) {
                            val focus = remember { FocusRequester() }
                            TextField(
                                value = vm.query,
                                onValueChange = { vm.query = it },
                                placeholder = { Text("Tìm theo tên hoặc ghi chú") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                            )
                            LaunchedEffect(Unit) { focus.requestFocus() }
                        } else {
                            Column {
                                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${visible.size} tài liệu",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (searching) vm.query = ""
                            searching = !searching
                        }) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Tìm kiếm") }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Lọc và sắp xếp") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                MenuLabel("Sắp xếp")
                                Sort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        onClick = { vm.sort = s; sortMenu = false },
                                        trailingIcon = if (vm.sort == s) ({ Icon(Icons.Default.Check, null) }) else null
                                    )
                                }
                                HorizontalDivider()
                                MenuLabel("Loại tệp")
                                TypeFilter.entries.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.label) },
                                        onClick = { vm.type = t; sortMenu = false },
                                        trailingIcon = if (vm.type == t) ({ Icon(Icons.Default.Check, null) }) else null
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { vm.changeGrid(!vm.grid) }) {
                            Icon(if (vm.grid) Icons.Default.ViewList else Icons.Default.GridView, "Đổi kiểu hiển thị")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!inTrash) {
                    ExtendedFloatingActionButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Thêm tệp") }
                    )
                }
            }
        ) { pad ->
            PullToRefreshBox(
                isRefreshing = vm.loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.padding(pad).fillMaxSize()
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (inTrash && visible.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Đây là thùng rác của Google Drive: tệp vẫn chiếm dung lượng và Drive tự xóa vĩnh viễn sau 30 ngày.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { confirmEmpty = true }) { Text("Dọn sạch") }
                            }
                        }
                    }

                    if (visible.isEmpty()) {
                        EmptyState(nav, searching || vm.query.isNotBlank() || vm.type != TypeFilter.ALL)
                    } else if (vm.grid) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(visible, key = { it.id }) { f -> FileCard(f, actions, inTrash) }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(visible, key = { it.id }) { f -> FileRow(f, actions, inTrash) }
                        }
                    }
                }

                // Tiến trình tải lên / đang xử lý
                val up = vm.upload
                val busy = vm.busy
                if (up != null || busy != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 88.dp).fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            if (up != null) {
                                Text(
                                    "Đang tải lên ${up.index}/${up.total}: ${up.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { up.progress }, modifier = Modifier.fillMaxWidth())
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(busy ?: "", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- Hộp thoại ----------
    if (pending.isNotEmpty()) {
        val preferred = (nav as? Nav.Category)?.name
        PickCategoryDialog(
            title = if (pending.size == 1) "Lưu 1 tệp vào phân loại" else "Lưu ${pending.size} tệp vào phân loại",
            categories = cats,
            initial = preferred,
            confirmLabel = "Tải lên",
            onConfirm = { vm.addFiles(pending, it) },
            onDismiss = { pending = emptyList() }
        )
    }
    notesFor?.let { f -> NotesDialog(f, onSave = { vm.saveNotes(f, it) }, onDismiss = { notesFor = null }) }
    moveFor?.let { f ->
        PickCategoryDialog(
            title = "Chuyển sang phân loại",
            categories = cats,
            initial = f.category,
            confirmLabel = "Chuyển",
            onConfirm = { vm.move(f, it) },
            onDismiss = { moveFor = null }
        )
    }
    deleteFor?.let { f ->
        ConfirmDialog(
            text = "Xóa vĩnh viễn \"${f.name}\"? Không thể hoàn tác.",
            confirmLabel = "Xóa vĩnh viễn",
            onConfirm = { vm.deleteForever(f) },
            onDismiss = { deleteFor = null }
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            text = "Xóa vĩnh viễn tất cả tệp trong thùng rác? Không thể hoàn tác.",
            confirmLabel = "Dọn sạch",
            onConfirm = { vm.emptyTrash() },
            onDismiss = { confirmEmpty = false }
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            text = "Đăng xuất khỏi ${vm.email ?: "tài khoản"}? Bản sao tệp đã tải tạm trên máy sẽ bị xóa. Tệp trên Drive vẫn còn nguyên.",
            confirmLabel = "Đăng xuất",
            onConfirm = { vm.signOut() },
            onDismiss = { confirmSignOut = false }
        )
    }
    if (showCats) {
        CategoryDialog(
            categories = cats,
            counts = files.filter { !it.isTrash }.groupingBy { it.category }.eachCount(),
            onAdd = { vm.addCategory(it) },
            onRename = { o, n -> vm.renameCategory(o, n) },
            onDelete = { vm.deleteCategory(it) },
            onDismiss = { showCats = false }
        )
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerContent(
    vm: MainViewModel,
    files: List<DocFile>,
    cats: List<String>,
    onNavigate: (Nav) -> Unit,
    onManage: () -> Unit,
    onSignOut: () -> Unit
) {
    val live = files.filter { !it.isTrash }
    val perCat = live.groupingBy { it.category }.eachCount()
    val dark by vm.dark.collectAsStateWithLifecycle()
    val isDark = dark ?: isSystemInDarkTheme()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.size(12.dp))
            Text("Documents Hub", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            NavigationDrawerItem(
                label = { Text("Tất cả tài liệu") },
                icon = { Icon(Icons.Default.Layers, null) },
                badge = { Text("${live.size}") },
                selected = vm.nav == Nav.All,
                onClick = { onNavigate(Nav.All) }
            )
            NavigationDrawerItem(
                label = { Text("Thùng rác") },
                icon = { Icon(Icons.Default.Delete, null) },
                badge = { Text("${files.count { it.isTrash }}") },
                selected = vm.nav == Nav.Trash,
                onClick = { onNavigate(Nav.Trash) }
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Phân loại",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onManage) { Icon(Icons.Default.Tune, "Quản lý phân loại") }
            }
            cats.forEach { c ->
                NavigationDrawerItem(
                    label = { Text(c, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Folder, null) },
                    badge = { Text("${perCat[c] ?: 0}") },
                    selected = (vm.nav as? Nav.Category)?.name == c,
                    onClick = { onNavigate(Nav.Category(c)) }
                )
            }
        }

        HorizontalDivider()
        Row(Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tài khoản", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(vm.email ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.setDark(!isDark) }) {
                Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, "Đổi giao diện sáng/tối")
            }
            IconButton(onClick = onSignOut) { Icon(Icons.Default.ExitToApp, "Đăng xuất") }
        }
    }
}

@Composable
private fun EmptyState(nav: Nav, filtered: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (nav == Nav.Trash) Icons.Default.Delete else Icons.Default.Description, null,
            Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        val head = when {
            filtered -> "Không có kết quả phù hợp"
            nav == Nav.Trash -> "Thùng rác trống"
            else -> "Chưa có tài liệu"
        }
        Text(head, style = MaterialTheme.typography.titleMedium)
        if (!filtered && nav != Nav.Trash) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Bấm \"Thêm tệp\" để tải PDF, ảnh, video, âm thanh hoặc bất kỳ tệp nào lên.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FileMenu(f: DocFile, a: Actions, inTrash: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Tùy chọn") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (inTrash) {
                DropdownMenuItem(text = { Text("Khôi phục") }, leadingIcon = { Icon(Icons.Default.RestoreFromTrash, null) },
                    onClick = { open = false; a.restore(f) })
                DropdownMenuItem(text = { Text("Xóa vĩnh viễn") }, leadingIcon = { Icon(Icons.Default.DeleteForever, null) },
                    onClick = { open = false; a.deleteForever(f) })
            } else {
                DropdownMenuItem(text = { Text("Mở") }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                    onClick = { open = false; a.open(f) })
                DropdownMenuItem(text = { Text("Ghi chú") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { open = false; a.notes(f) })
                DropdownMenuItem(text = { Text("Chuyển phân loại") }, leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                    onClick = { open = false; a.move(f) })
                DropdownMenuItem(text = { Text("Lưu bản sao") }, leadingIcon = { Icon(Icons.Default.Download, null) },
                    onClick = { open = false; a.saveCopy(f) })
                DropdownMenuItem(text = { Text("Mở bằng ứng dụng khác") }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                    onClick = { open = false; a.external(f) })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Chuyển vào thùng rác") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { open = false; a.trash(f) })
            }
        }
    }
}

@Composable
private fun FileCard(f: DocFile, a: Actions, inTrash: Boolean) {
    Card(
        onClick = { a.open(f) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.85f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Thumb(f, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))) {
                FileMenu(f, a, inTrash)
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                metaLine(f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(formatDate(f.uploadDate), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileRow(f: DocFile, a: Actions, inTrash: Boolean) {
    ListItem(
        modifier = Modifier.clickableRow { a.open(f) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Thumb(
                f,
                Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                iconSize = 24
            )
        },
        headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(metaLine(f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (f.notes.isNotBlank()) Text(f.notes, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        },
        overlineContent = { Text("${f.category} · ${formatDate(f.uploadDate)}", fontSize = 11.sp) },
        trailingContent = { FileMenu(f, a, inTrash) }
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
