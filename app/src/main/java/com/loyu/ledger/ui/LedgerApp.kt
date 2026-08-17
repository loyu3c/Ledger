package com.loyu.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.loyu.ledger.data.local.AccountEntity
import com.loyu.ledger.data.local.AccountType
import com.loyu.ledger.data.local.CategoryEntity
import com.loyu.ledger.data.local.CategoryTotal
import com.loyu.ledger.data.local.TransactionRow
import com.loyu.ledger.data.local.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(vm: LedgerViewModel) {
    val accounts by vm.accounts.collectAsState()
    val allAccounts by vm.allAccounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val allCategories by vm.allCategories.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val expense by vm.monthExpense.collectAsState()
    val income by vm.monthIncome.collectAsState()
    val selectedMonth by vm.selectedMonth.collectAsState()
    val expenseByCategory by vm.expenseByCategory.collectAsState()
    val incomeByCategory by vm.incomeByCategory.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<TransactionRow?>(null) }
    var showAccounts by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loyu 記帳") },
                actions = {
                    TextButton(onClick = { showStatistics = true }) { Text("統計") }
                    TextButton(onClick = { showCategories = true }) { Text("分類") }
                    TextButton(onClick = { showAccounts = true }) { Text("帳戶") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("＋ 記一筆") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { vm.previousMonth() }) { Text("‹ 上個月") }
                    Text(monthLabel(selectedMonth), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { vm.nextMonth() }) { Text("下個月 ›") }
                }
                Spacer(Modifier.height(8.dp))
                SummaryCard(income = income, expense = expense)
            }
            item { Text("交易紀錄", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (transactions.isEmpty()) item { Text("這個月還沒有紀錄，按右下角「記一筆」開始。") }
            items(transactions, key = { it.id }) { row ->
                ListItem(
                    modifier = Modifier.clickable { editingRow = row },
                    headlineContent = { Text(if (row.merchant.isNotBlank()) row.merchant else row.categoryName) },
                    supportingContent = { Text("${row.categoryIcon} ${row.categoryName} · ${row.accountName} · ${formatDate(row.occurredAt)}") },
                    trailingContent = {
                        val prefix = if (row.type == TransactionType.EXPENSE) "-" else "+"
                        Text("$prefix${money(row.amount)}", fontWeight = FontWeight.SemiBold)
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd || editingRow != null) {
        val editing = editingRow
        TransactionSheet(
            accounts = accounts,
            categories = categories,
            existing = editing,
            onDismiss = { showAdd = false; editingRow = null },
            onSave = { type, amount, accountId, categoryId, merchant, note ->
                if (editing != null) {
                    vm.updateTransaction(editing.id, type, amount, accountId, categoryId, merchant, note)
                } else {
                    vm.addTransaction(type, amount, accountId, categoryId, merchant, note)
                }
                showAdd = false
                editingRow = null
            },
            onDelete = if (editing != null) {
                {
                    vm.deleteTransaction(editing.id)
                    showAdd = false
                    editingRow = null
                }
            } else null,
        )
    }

    if (showAccounts) {
        AccountManagementSheet(
            accounts = allAccounts,
            onDismiss = { showAccounts = false },
            onAdd = { name, type -> vm.addAccount(name, type) },
            onEdit = { id, name, type -> vm.updateAccount(id, name, type) },
            onToggleActive = { id, isActive -> vm.setAccountActive(id, isActive) },
        )
    }

    if (showCategories) {
        CategoryManagementSheet(
            categories = allCategories,
            onDismiss = { showCategories = false },
            onAdd = { name, type, icon -> vm.addCategory(name, type, icon) },
            onEdit = { id, name, type, icon -> vm.updateCategory(id, name, type, icon) },
            onToggleActive = { id, isActive -> vm.setCategoryActive(id, isActive) },
        )
    }

    if (showStatistics) {
        StatisticsSheet(
            monthLabel = monthLabel(selectedMonth),
            expenseByCategory = expenseByCategory,
            incomeByCategory = incomeByCategory,
            onDismiss = { showStatistics = false },
        )
    }
}

@Composable
private fun SummaryCard(income: Long, expense: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryLine("收入", money(income))
            SummaryLine("支出", money(expense))
            HorizontalDivider()
            SummaryLine("結餘", money(income - expense), bold = true)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSheet(
    accounts: List<com.loyu.ledger.data.local.AccountEntity>,
    categories: List<com.loyu.ledger.data.local.CategoryEntity>,
    existing: TransactionRow?,
    onDismiss: () -> Unit,
    onSave: (TransactionType, Long, Long, Long, String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var merchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val filteredCategories = categories.filter { it.type == type }
    var accountId by remember(accounts) { mutableStateOf(existing?.accountId ?: accounts.firstOrNull()?.id) }
    var categoryId by remember(type, filteredCategories) {
        mutableStateOf(existing?.categoryId?.takeIf { id -> filteredCategories.any { it.id == id } } ?: filteredCategories.firstOrNull()?.id)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (existing == null) "新增記帳" else "編輯記帳", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                SegmentedButton(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter(Char::isDigit) },
                label = { Text("金額") },
                prefix = { Text("NT$ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("分類", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredCategories.forEach { category ->
                    FilterChip(
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                        label = { Text("${category.icon} ${category.name}") },
                    )
                }
            }
            Text("帳戶", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    FilterChip(selected = accountId == account.id, onClick = { accountId = account.id }, label = { Text(account.name) })
                }
            }
            OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("商家 / 對象（選填）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("備註（選填）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f)) { Text("刪除") }
                }
                Button(
                    onClick = {
                        val amount = amountText.toLongOrNull() ?: return@Button
                        onSave(type, amount, accountId ?: return@Button, categoryId ?: return@Button, merchant, note)
                    },
                    enabled = (amountText.toLongOrNull() ?: 0) > 0 && accountId != null && categoryId != null,
                    modifier = Modifier.weight(1f),
                ) { Text("儲存") }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("刪除這筆紀錄？") },
            text = { Text("刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManagementSheet(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, AccountType) -> Unit,
    onEdit: (Long, String, AccountType) -> Unit,
    onToggleActive: (Long, Boolean) -> Unit,
) {
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddForm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("帳戶管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            accounts.forEach { account ->
                ListItem(
                    headlineContent = { Text(account.name) },
                    supportingContent = {
                        Text(if (account.isActive) accountTypeLabel(account.type) else "${accountTypeLabel(account.type)} · 已停用")
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { editingAccount = account }) { Text("編輯") }
                            Switch(checked = account.isActive, onCheckedChange = { onToggleActive(account.id, it) })
                        }
                    },
                )
            }
            OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增帳戶") }
        }
    }

    if (showAddForm) {
        AccountFormDialog(
            existing = null,
            onDismiss = { showAddForm = false },
            onSave = { name, type -> onAdd(name, type); showAddForm = false },
        )
    }
    editingAccount?.let { account ->
        AccountFormDialog(
            existing = account,
            onDismiss = { editingAccount = null },
            onSave = { name, type -> onEdit(account.id, name, type); editingAccount = null },
        )
    }
}

@Composable
private fun AccountFormDialog(
    existing: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (String, AccountType) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CASH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增帳戶" else "編輯帳戶") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("帳戶名稱") }, singleLine = true)
                Text("類型", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(accountTypeLabel(t)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), type) }, enabled = name.isNotBlank()) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "現金"
    AccountType.BANK -> "銀行帳戶"
    AccountType.CREDIT_CARD -> "信用卡"
    AccountType.E_WALLET -> "電子錢包"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManagementSheet(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, TransactionType, String) -> Unit,
    onEdit: (Long, String, TransactionType, String) -> Unit,
    onToggleActive: (Long, Boolean) -> Unit,
) {
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddForm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("分類管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            categories.forEach { category ->
                ListItem(
                    headlineContent = { Text("${category.icon} ${category.name}") },
                    supportingContent = {
                        val typeLabel = if (category.type == TransactionType.EXPENSE) "支出" else "收入"
                        Text(if (category.isActive) typeLabel else "$typeLabel · 已停用")
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { editingCategory = category }) { Text("編輯") }
                            Switch(checked = category.isActive, onCheckedChange = { onToggleActive(category.id, it) })
                        }
                    },
                )
            }
            OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增分類") }
        }
    }

    if (showAddForm) {
        CategoryFormDialog(
            existing = null,
            onDismiss = { showAddForm = false },
            onSave = { name, type, icon -> onAdd(name, type, icon); showAddForm = false },
        )
    }
    editingCategory?.let { category ->
        CategoryFormDialog(
            existing = category,
            onDismiss = { editingCategory = null },
            onSave = { name, type, icon -> onEdit(category.id, name, type, icon); editingCategory = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFormDialog(
    existing: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, TransactionType, String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var icon by remember { mutableStateOf(existing?.icon ?: "💰") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增分類" else "編輯分類") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("圖示（emoji）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分類名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("類型", fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                    SegmentedButton(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), type, icon.trim()) }, enabled = name.isNotBlank()) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsSheet(
    monthLabel: String,
    expenseByCategory: List<CategoryTotal>,
    incomeByCategory: List<CategoryTotal>,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    val data = if (type == TransactionType.EXPENSE) expenseByCategory else incomeByCategory
    val total = data.sumOf { it.total }.coerceAtLeast(1)
    val colors = chartColors(data.size)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$monthLabel 統計", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                SegmentedButton(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
            }
            if (data.isEmpty()) {
                Text(if (type == TransactionType.EXPENSE) "這個月還沒有支出紀錄。" else "這個月還沒有收入紀錄。")
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    CategoryPieChart(data = data, colors = colors, modifier = Modifier.size(180.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    data.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(colors[index], CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("${item.categoryIcon} ${item.categoryName}", modifier = Modifier.weight(1f))
                            Text("${money(item.total)} · ${item.total * 100 / total}%", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPieChart(data: List<CategoryTotal>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.total }.coerceAtLeast(1)
    Canvas(modifier) {
        val strokeWidth = size.minDimension * 0.28f
        var startAngle = -90f
        data.forEachIndexed { index, item ->
            val sweep = 360f * item.total / total
            drawArc(
                color = colors[index],
                startAngle = startAngle,
                sweepAngle = sweep.coerceAtLeast(0.5f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

private val chartPalette = listOf(
    Color(0xFF6750A4), Color(0xFF00696D), Color(0xFFB3261E), Color(0xFF7D5260),
    Color(0xFF386A20), Color(0xFF8C4A00), Color(0xFF31628E), Color(0xFF6E5A00),
)
private fun chartColors(count: Int): List<Color> = List(count) { chartPalette[it % chartPalette.size] }

private val monthLabelFormatter = DateTimeFormatter.ofPattern("yyyy年MM月", Locale.TAIWAN)
private fun monthLabel(month: LocalDate): String = month.format(monthLabelFormatter)

private fun money(value: Long): String = "NT$ ${NumberFormat.getIntegerInstance(Locale.TAIWAN).format(value)}"
private fun formatDate(epoch: Long): String = SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(Date(epoch))
