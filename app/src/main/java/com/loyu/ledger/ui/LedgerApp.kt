package com.loyu.ledger.ui

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.loyu.ledger.data.local.AccountEntity
import com.loyu.ledger.data.local.AccountNet
import com.loyu.ledger.data.local.AccountType
import com.loyu.ledger.data.local.CategoryEntity
import com.loyu.ledger.data.local.CategoryTotal
import com.loyu.ledger.data.local.DebtDirection
import com.loyu.ledger.data.local.DebtEntity
import com.loyu.ledger.data.local.TransactionEntity
import com.loyu.ledger.data.local.TransactionRow
import com.loyu.ledger.data.local.TransactionType
import com.loyu.ledger.data.invoice.InvoiceCsvParser
import com.loyu.ledger.data.invoice.InvoiceCsvReceipt
import com.loyu.ledger.data.prefs.ThemeMode
import com.loyu.ledger.data.remote.VoiceTransactionResult
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

private enum class ViewMode { LIST, CALENDAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(vm: LedgerViewModel) {
    val accounts by vm.accounts.collectAsState()
    val allAccounts by vm.allAccounts.collectAsState()
    val accountNet by vm.accountNet.collectAsState()
    val categories by vm.categories.collectAsState()
    val allCategories by vm.allCategories.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val expense by vm.monthExpense.collectAsState()
    val income by vm.monthIncome.collectAsState()
    val selectedMonth by vm.selectedMonth.collectAsState()
    val expenseByCategory by vm.expenseByCategory.collectAsState()
    val incomeByCategory by vm.incomeByCategory.collectAsState()
    val groqApiKey by vm.groqApiKey.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val debts by vm.debts.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<TransactionRow?>(null) }
    var showAccounts by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showImportInvoices by remember { mutableStateOf(false) }
    var showDebts by remember { mutableStateOf(false) }
    var showAssetsLiabilities by remember { mutableStateOf(false) }
    var showAddDebt by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var selectedDay by remember(selectedMonth) {
        val today = LocalDate.now()
        mutableStateOf(if (selectedMonth == today.withDayOfMonth(1)) today else null)
    }
    val zone = remember { ZoneId.systemDefault() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("有魚記帳") },
                actions = {
                    TextButton(onClick = { showStatistics = true }) { Text("統計") }
                    TextButton(onClick = { showDebts = true }) { Text("借貸") }
                    TextButton(onClick = { showAssetsLiabilities = true }) { Text("資產負債") }
                    TextButton(onClick = { showSettings = true }) { Text("設定") }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showFabMenu) {
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = false; showAddDebt = true },
                        icon = { Text("🤝") },
                        text = { Text("新增借貸") },
                    )
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = false; showAdd = true },
                        icon = { Text("💰") },
                        text = { Text("收入/支出") },
                    )
                }
                FloatingActionButton(onClick = { showFabMenu = !showFabMenu }) {
                    Text(if (showFabMenu) "×" else "＋", style = MaterialTheme.typography.headlineMedium)
                }
            }
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
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = viewMode == ViewMode.LIST, onClick = { viewMode = ViewMode.LIST }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("列表") }
                    SegmentedButton(selected = viewMode == ViewMode.CALENDAR, onClick = { viewMode = ViewMode.CALENDAR }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("月曆") }
                }
            }

            val monthStart = selectedMonth.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val monthEnd = selectedMonth.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val monthDebts = debts.filter { it.occurredAt in monthStart until monthEnd }

            if (viewMode == ViewMode.LIST) {
                item {
                    SummaryRow(income = income, expense = expense)
                }
                val timeline = buildTimeline(transactions, monthDebts)
                if (timeline.isEmpty()) item { Text("這個月還沒有紀錄，按右下角「記一筆」開始。") }
                items(timeline, key = { it.key }) { entry ->
                    TimelineListItem(entry = entry, onClickTransaction = { editingRow = it }, onClickDebt = { showDebts = true })
                    HorizontalDivider()
                }
            } else {
                item {
                    val incomeDays = remember(transactions) {
                        transactions.filter { it.type == TransactionType.INCOME }
                            .map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }.toSet()
                    }
                    val expenseDays = remember(transactions) {
                        transactions.filter { it.type == TransactionType.EXPENSE }
                            .map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }.toSet()
                    }
                    val debtDays = remember(monthDebts) {
                        monthDebts.map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }.toSet()
                    }
                    MonthCalendar(
                        month = selectedMonth,
                        incomeDays = incomeDays,
                        expenseDays = expenseDays,
                        debtDays = debtDays,
                        selectedDay = selectedDay,
                        onSelectDay = { selectedDay = it },
                    )
                }
                val day = selectedDay
                if (day == null) {
                    item { Text("點選上方日期查看當天的收支明細。") }
                } else {
                    val dayTransactions = transactions.filter { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() == day }
                    val dayDebts = monthDebts.filter { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() == day }
                    val dayExpense = dayTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    val dayIncome = dayTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    item {
                        SummaryRow(income = dayIncome, expense = dayExpense)
                    }
                    val dayTimeline = buildTimeline(dayTransactions, dayDebts)
                    if (dayTimeline.isEmpty()) {
                        item { Text("這天還沒有紀錄。") }
                    } else {
                        items(dayTimeline, key = { it.key }) { entry ->
                            TimelineListItem(entry = entry, onClickTransaction = { editingRow = it }, onClickDebt = { showDebts = true })
                            HorizontalDivider()
                        }
                    }
                }
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
            onSave = { type, amount, accountId, categoryId, merchant, note, occurredAt ->
                if (editing != null) {
                    vm.updateTransaction(editing.id, type, amount, accountId, categoryId, merchant, note, occurredAt)
                } else {
                    vm.addTransaction(type, amount, accountId, categoryId, merchant, note, occurredAt)
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
            onVoiceInput = { text -> vm.parseVoiceTransaction(text, categories.map { it.name }) },
        )
    }

    if (showAccounts) {
        AccountManagementSheet(
            accounts = allAccounts,
            onDismiss = { showAccounts = false },
            onAdd = { name, type, openingBalance -> vm.addAccount(name, type, openingBalance) },
            onEdit = { id, name, type, openingBalance -> vm.updateAccount(id, name, type, openingBalance) },
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
            onPreviousMonth = { vm.previousMonth() },
            onNextMonth = { vm.nextMonth() },
        )
    }

    if (showDebts) {
        DebtManagementSheet(
            debts = debts,
            accounts = accounts,
            onDismiss = { showDebts = false },
            onAdd = { direction, counterparty, amount, occurredAt, dueDate, note, accountId ->
                vm.addDebt(direction, counterparty, amount, occurredAt, dueDate, note, accountId)
            },
            onSettle = { id, accountId -> vm.settleDebt(id, accountId) },
            onDelete = { vm.deleteDebt(it) },
        )
    }

    if (showAssetsLiabilities) {
        AssetsLiabilitiesSheet(
            debts = debts,
            accounts = allAccounts.filter { it.isActive },
            accountNet = accountNet,
            onDismiss = { showAssetsLiabilities = false },
            onOpenDebts = { showAssetsLiabilities = false; showDebts = true },
        )
    }

    if (showAddDebt) {
        DebtFormDialog(
            defaultDirection = DebtDirection.LEND,
            knownCounterparties = remember(debts) { debts.map { it.counterparty }.distinct() },
            accounts = accounts,
            onDismiss = { showAddDebt = false },
            onSave = { direction, counterparty, amount, occurredAt, dueDate, note, accountId ->
                vm.addDebt(direction, counterparty, amount, occurredAt, dueDate, note, accountId)
                showAddDebt = false
            },
        )
    }

    if (showSettings) {
        SettingsSheet(
            currentApiKey = groqApiKey,
            themeMode = themeMode,
            onDismiss = { showSettings = false },
            onSaveApiKey = { vm.setGroqApiKey(it) },
            onThemeModeChange = { vm.setThemeMode(it) },
            onExportBackup = { vm.exportBackup() },
            onImportBackup = { vm.importBackup(it) },
            onOpenAccounts = { showSettings = false; showAccounts = true },
            onOpenCategories = { showSettings = false; showCategories = true },
            onOpenImportInvoices = { showSettings = false; showImportInvoices = true },
        )
    }

    if (showImportInvoices) {
        InvoiceImportSheet(
            accounts = accounts,
            categories = categories,
            onDismiss = { showImportInvoices = false },
            onLoadExistingKeys = { vm.existingTransactionKeys() },
            onImport = { entries -> vm.importInvoiceTransactions(entries) },
        )
    }
}

@Composable
private fun SummaryRow(income: Long, expense: Long) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("收入", style = MaterialTheme.typography.labelSmall)
                Text(money(income), color = IncomeGreen, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("支出", style = MaterialTheme.typography.labelSmall)
                Text(money(expense), fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("結餘", style = MaterialTheme.typography.labelSmall)
                Text(money(income - expense), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val IncomeGreen = Color(0xFF2E7D32)
private val ExpenseRed = Color(0xFFC62828)
private val DebtBlue = Color(0xFF1565C0)

@Composable
private fun TransactionListItem(row: TransactionRow, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(if (row.merchant.isNotBlank()) row.merchant else row.categoryName) },
        supportingContent = { Text("${row.categoryIcon} ${row.categoryName} · ${row.accountName} · ${formatDate(row.occurredAt)}") },
        trailingContent = {
            val prefix = if (row.type == TransactionType.EXPENSE) "-" else "+"
            val color = if (row.type == TransactionType.INCOME) IncomeGreen else Color.Unspecified
            Text("$prefix${money(row.amount)}", fontWeight = FontWeight.SemiBold, color = color)
        },
    )
}

private sealed class TimelineEntry(val occurredAt: Long, val key: String) {
    class Txn(val row: TransactionRow) : TimelineEntry(row.occurredAt, "t${row.id}")
    class Debt(val debt: DebtEntity) : TimelineEntry(debt.occurredAt, "d${debt.id}")
}

private fun buildTimeline(transactions: List<TransactionRow>, debts: List<DebtEntity>): List<TimelineEntry> =
    (transactions.map { TimelineEntry.Txn(it) } + debts.map { TimelineEntry.Debt(it) })
        .sortedByDescending { it.occurredAt }

@Composable
private fun TimelineListItem(
    entry: TimelineEntry,
    onClickTransaction: (TransactionRow) -> Unit,
    onClickDebt: (DebtEntity) -> Unit,
) {
    when (entry) {
        is TimelineEntry.Txn -> TransactionListItem(row = entry.row, onClick = { onClickTransaction(entry.row) })
        is TimelineEntry.Debt -> DebtListItem(debt = entry.debt, onClick = { onClickDebt(entry.debt) })
    }
}

@Composable
private fun DebtListItem(debt: DebtEntity, onClick: () -> Unit) {
    val directionLabel = if (debt.direction == DebtDirection.LEND) "借出/代墊" else "借入"
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(debt.counterparty, color = if (debt.isSettled) mutedColor else Color.Unspecified) },
        supportingContent = {
            Text(
                buildString {
                    append("🤝 $directionLabel · ${formatDate(debt.occurredAt)}")
                    if (debt.isSettled) append(" · 已還")
                }
            )
        },
        trailingContent = {
            Text(money(debt.amount), fontWeight = FontWeight.SemiBold, color = if (debt.isSettled) mutedColor else DebtBlue)
        },
    )
}

private val weekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

@Composable
private fun MonthCalendar(
    month: LocalDate,
    incomeDays: Set<LocalDate>,
    expenseDays: Set<LocalDate>,
    debtDays: Set<LocalDate>,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
) {
    val firstDayOfMonth = month.withDayOfMonth(1)
    val daysInMonth = month.lengthOfMonth()
    val today = remember { LocalDate.now() }
    // DayOfWeek: MONDAY=1..SUNDAY=7; convert so SUNDAY lands in column 0.
    val firstWeekdayIndex = firstDayOfMonth.dayOfWeek.value % 7
    val totalCells = firstWeekdayIndex + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = row * 7 + col - firstWeekdayIndex + 1
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = firstDayOfMonth.withDayOfMonth(dayNumber)
                            val selected = date == selectedDay
                            val background = when {
                                selected -> MaterialTheme.colorScheme.primaryContainer
                                date == today -> MaterialTheme.colorScheme.surfaceVariant
                                else -> Color.Transparent
                            }
                            Column(
                                modifier = Modifier.fillMaxSize()
                                    .clip(CircleShape)
                                    .background(background)
                                    .clickable { onSelectDay(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$dayNumber", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Box(Modifier.size(4.dp).background(if (date in incomeDays) IncomeGreen else Color.Transparent, CircleShape))
                                    Box(Modifier.size(4.dp).background(if (date in expenseDays) ExpenseRed else Color.Transparent, CircleShape))
                                    Box(Modifier.size(4.dp).background(if (date in debtDays) DebtBlue else Color.Transparent, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSheet(
    accounts: List<com.loyu.ledger.data.local.AccountEntity>,
    categories: List<com.loyu.ledger.data.local.CategoryEntity>,
    existing: TransactionRow?,
    onDismiss: () -> Unit,
    onSave: (TransactionType, Long, Long, Long, String, String, Long) -> Unit,
    onDelete: (() -> Unit)?,
    onVoiceInput: suspend (String) -> VoiceTransactionResult?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var showCalculator by remember { mutableStateOf(false) }
    var merchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var occurredAtMillis by remember { mutableStateOf(existing?.occurredAt ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showInvoiceScanner by remember { mutableStateOf(false) }
    var voiceProcessing by remember { mutableStateOf(false) }
    val filteredCategories = categories.filter { it.type == type }
    var accountId by remember(accounts) { mutableStateOf(existing?.accountId ?: accounts.firstOrNull()?.id) }
    var categoryId by remember(type, filteredCategories) {
        mutableStateOf(existing?.categoryId?.takeIf { id -> filteredCategories.any { it.id == id } } ?: filteredCategories.firstOrNull()?.id)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            voiceProcessing = true
            scope.launch {
                val parsed = onVoiceInput(spokenText)
                if (parsed != null) {
                    parsed.type?.let { type = it }
                    parsed.amount?.let { amountText = it.toString() }
                    if (parsed.merchant.isNotBlank()) merchant = parsed.merchant
                    if (parsed.note.isNotBlank()) note = if (note.isBlank()) parsed.note else "$note ${parsed.note}"
                    val effectiveType = parsed.type ?: type
                    categories.firstOrNull { it.type == effectiveType && it.name == parsed.categoryName }?.let { categoryId = it.id }
                } else {
                    note = if (note.isBlank()) spokenText else "$note $spokenText"
                }
                voiceProcessing = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (existing == null) "新增記帳" else "編輯記帳", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "說出這筆記帳的內容")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            speechLauncher.launch(intent)
                        } else {
                            Toast.makeText(context, "找不到可用的語音輸入服務", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !voiceProcessing,
                    modifier = Modifier.weight(1f),
                ) { Text(if (voiceProcessing) "辨識中…" else "🎤 語音輸入") }
                OutlinedButton(onClick = { showInvoiceScanner = true }, modifier = Modifier.weight(1f)) {
                    Text("📷 掃發票")
                }
            }
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("日期：${formatDateOnly(occurredAtMillis)}")
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                SegmentedButton(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter(Char::isDigit) },
                label = { Text("金額") },
                prefix = { Text("NT$ ") },
                trailingIcon = { IconButton(onClick = { showCalculator = true }) { Text("🧮") } },
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
                        onSave(type, amount, accountId ?: return@Button, categoryId ?: return@Button, merchant, note, occurredAtMillis)
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = occurredAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedUtcMillis ->
                        val pickedDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        val existingTime = Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                        occurredAtMillis = pickedDate.atTime(existingTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showInvoiceScanner) {
        InvoiceScannerDialog(
            onDismiss = { showInvoiceScanner = false },
            onScanned = { invoice ->
                showInvoiceScanner = false
                amountText = invoice.totalAmount.toString()
                val existingTime = Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                occurredAtMillis = invoice.date.atTime(existingTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val itemsText = invoice.items.joinToString("、") { "${it.name} x${it.quantity}" }
                val invoiceNote = if (itemsText.isNotBlank()) "統編:${invoice.sellerId} $itemsText" else "統編:${invoice.sellerId}"
                note = if (note.isBlank()) invoiceNote else "$note $invoiceNote"
            },
        )
    }

    if (showCalculator) {
        CalculatorDialog(
            initialExpression = amountText,
            onDismiss = { showCalculator = false },
            onConfirm = { amountText = it.toString(); showCalculator = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    currentApiKey: String,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onExportBackup: suspend () -> String,
    onImportBackup: suspend (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenImportInvoices: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = onExportBackup()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess {
                Toast.makeText(context, "備份匯出成功", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "匯出失敗", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("設定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("外觀", fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = themeMode == ThemeMode.SYSTEM, onClick = { onThemeModeChange(ThemeMode.SYSTEM) }, shape = SegmentedButtonDefaults.itemShape(0, 3)) { Text("跟隨系統") }
                    SegmentedButton(selected = themeMode == ThemeMode.LIGHT, onClick = { onThemeModeChange(ThemeMode.LIGHT) }, shape = SegmentedButtonDefaults.itemShape(1, 3)) { Text("亮色") }
                    SegmentedButton(selected = themeMode == ThemeMode.DARK, onClick = { onThemeModeChange(ThemeMode.DARK) }, shape = SegmentedButtonDefaults.itemShape(2, 3)) { Text("深色") }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("語音輸入", fontWeight = FontWeight.SemiBold)
                Text(
                    "Groq API Key（用於語音輸入的語意解析。留空的話，語音輸入只會把辨識出的文字放進備註欄位，不會自動分欄位。金鑰只存在這台裝置上。）",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Groq API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onSaveApiKey(apiKey.trim()) }, modifier = Modifier.align(Alignment.End)) { Text("儲存") }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("資料管理", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenAccounts, modifier = Modifier.weight(1f)) { Text("帳戶管理") }
                    OutlinedButton(onClick = onOpenCategories, modifier = Modifier.weight(1f)) { Text("分類管理") }
                }
                Text(
                    "從其他 App 匯出電子發票消費明細 CSV，勾選要記帳的項目後一次匯入，取代雲端電子發票 API 串接。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onOpenImportInvoices, modifier = Modifier.fillMaxWidth()) { Text("匯入電子發票明細（CSV）") }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("備份與還原", fontWeight = FontWeight.SemiBold)
                Text(
                    "把所有記帳資料匯出成一個檔案，可以自行存到 Google 雲端硬碟、LINE 或本機。換手機或重灌前建議先匯出備份。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val filename = "loyu-ledger-backup-${SimpleDateFormat("yyyyMMdd", Locale.TAIWAN).format(Date())}.json"
                            exportLauncher.launch(filename)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("匯出備份") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("匯入還原") }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("關於", fontWeight = FontWeight.SemiBold)
                Text("有魚記帳（LoyuLedger）v0.1.0 · 開發中", style = MaterialTheme.typography.bodySmall)
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("關閉") }
        }
    }

    val importUri = pendingImportUri
    if (importUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("確定要匯入還原嗎？") },
            text = { Text("匯入將會覆蓋目前所有本機資料（帳戶、分類、交易紀錄），且無法復原。建議先匯出目前的資料備份。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    scope.launch {
                        runCatching {
                            val json = context.contentResolver.openInputStream(importUri)?.bufferedReader()?.use { it.readText() }
                                ?: throw IllegalStateException("empty file")
                            onImportBackup(json)
                        }.onSuccess {
                            Toast.makeText(context, "匯入完成", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "備份檔案格式錯誤，匯入失敗", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("確定匯入") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceImportSheet(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onLoadExistingKeys: suspend () -> Set<String>,
    onImport: suspend (List<TransactionEntity>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }

    var receipts by remember { mutableStateOf<List<InvoiceCsvReceipt>>(emptyList()) }
    var existingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var checked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var categoryByIndex by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var accountByIndex by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var categoryPickerIndex by remember { mutableStateOf<Int?>(null) }
    var accountPickerIndex by remember { mutableStateOf<Int?>(null) }

    val pickCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("empty file")
                val parsed = InvoiceCsvParser.parse(text)
                val keys = onLoadExistingKeys()
                parsed to keys
            }.onSuccess { (parsed, keys) ->
                receipts = parsed
                existingKeys = keys
                checked = parsed.indices.filter { i -> parsed[i].total > 0 && "${parsed[i].date}|${parsed[i].merchant}|${parsed[i].total}" !in keys }.toSet()
                val defaultCategoryId = expenseCategories.firstOrNull()?.id
                categoryByIndex = if (defaultCategoryId != null) parsed.indices.associateWith { defaultCategoryId } else emptyMap()
                val defaultAccountId = accounts.firstOrNull()?.id
                accountByIndex = if (defaultAccountId != null) parsed.indices.associateWith { defaultAccountId } else emptyMap()
                loading = false
            }.onFailure {
                loading = false
                Toast.makeText(context, "發票明細 CSV 格式無法解析", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("匯入電子發票明細", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "選擇從其他 App 匯出的電子發票消費明細 CSV，同一天、同一店家的品項會自動合併成一筆消費紀錄（折扣/退款會直接併入小計）。勾選要匯入的紀錄、選好分類與帳戶後一次建立。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { pickCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("選擇 CSV 檔案") }

            if (loading) {
                Text("解析中…")
            } else if (receipts.isNotEmpty()) {
                if (accounts.isEmpty()) {
                    Text("請先到設定新增一個帳戶。", style = MaterialTheme.typography.bodySmall)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("共 ${receipts.size} 筆，已勾選 ${checked.size} 筆", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { checked = receipts.indices.filter { receipts[it].total > 0 }.toSet() }) { Text("全選") }
                        TextButton(onClick = { checked = emptySet() }) { Text("全不選") }
                    }
                }

                receipts.forEachIndexed { index, receipt ->
                    val key = "${receipt.date}|${receipt.merchant}|${receipt.total}"
                    val isDuplicate = key in existingKeys
                    val isInvalid = receipt.total <= 0
                    val category = categories.firstOrNull { it.id == categoryByIndex[index] }
                    val account = accounts.firstOrNull { it.id == accountByIndex[index] }
                    ListItem(
                        leadingContent = {
                            Checkbox(
                                checked = index in checked,
                                onCheckedChange = { isChecked -> checked = if (isChecked) checked + index else checked - index },
                                enabled = !isInvalid,
                            )
                        },
                        headlineContent = { Text(receipt.merchant, maxLines = 1) },
                        supportingContent = {
                            Column {
                                Text("${receipt.date} · ${receipt.items.joinToString("、") { it.name }}", maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                if (isDuplicate) Text("可能與現有紀錄重複，預設不勾選", color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                                if (isInvalid) Text("小計為 0 或負數，無法匯入", color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { categoryPickerIndex = index }, contentPadding = PaddingValues(0.dp)) {
                                        Text("分類：${category?.let { "${it.icon} ${it.name}" } ?: "未選擇"}")
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    TextButton(onClick = { accountPickerIndex = index }, contentPadding = PaddingValues(0.dp)) {
                                        Text("帳戶：${account?.name ?: "未選擇"}")
                                    }
                                }
                            }
                        },
                        trailingContent = { Text(money(receipt.total), fontWeight = FontWeight.SemiBold) },
                    )
                    HorizontalDivider()
                }

                Button(
                    onClick = {
                        val entries = checked.mapNotNull { index ->
                            val receipt = receipts.getOrNull(index) ?: return@mapNotNull null
                            val catId = categoryByIndex[index] ?: return@mapNotNull null
                            val accId = accountByIndex[index] ?: return@mapNotNull null
                            TransactionEntity(
                                type = TransactionType.EXPENSE,
                                amount = receipt.total,
                                accountId = accId,
                                categoryId = catId,
                                merchant = receipt.merchant,
                                note = receipt.items.joinToString("、") { it.name },
                                occurredAt = receipt.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            )
                        }
                        scope.launch {
                            onImport(entries)
                            Toast.makeText(context, "已匯入 ${entries.size} 筆記帳紀錄", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = checked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("匯入 ${checked.size} 筆") }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("關閉") }
        }
    }

    categoryPickerIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { categoryPickerIndex = null },
            title = { Text("選擇分類") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    expenseCategories.forEach { cat ->
                        FilterChip(
                            selected = categoryByIndex[index] == cat.id,
                            onClick = { categoryByIndex = categoryByIndex + (index to cat.id); categoryPickerIndex = null },
                            label = { Text("${cat.icon} ${cat.name}") },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { categoryPickerIndex = null }) { Text("關閉") } },
        )
    }

    accountPickerIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { accountPickerIndex = null },
            title = { Text("選擇帳戶") },
            text = {
                if (accounts.isEmpty()) {
                    Text("請先到設定新增一個帳戶。", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { account ->
                            FilterChip(
                                selected = accountByIndex[index] == account.id,
                                onClick = { accountByIndex = accountByIndex + (index to account.id); accountPickerIndex = null },
                                label = { Text("${account.name}（${accountTypeLabel(account.type)}）") },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { accountPickerIndex = null }) { Text("關閉") } },
        )
    }
}

/**
 * An account's current value: opening balance plus net (income - expense) for cash/bank/
 * e-wallet accounts, or opening balance minus net for credit cards, where net going more
 * negative (more spending) increases what's owed.
 */
private fun accountValue(account: AccountEntity, netByAccountId: Map<Long, Long>): Long {
    val net = netByAccountId[account.id] ?: 0L
    return if (account.type == AccountType.CREDIT_CARD) account.openingBalance - net else account.openingBalance + net
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetsLiabilitiesSheet(
    debts: List<DebtEntity>,
    accounts: List<AccountEntity>,
    accountNet: List<AccountNet>,
    onDismiss: () -> Unit,
    onOpenDebts: () -> Unit,
) {
    val netByAccountId = remember(accountNet) { accountNet.associate { it.accountId to it.net } }
    val assetAccounts = accounts.filter { it.type != AccountType.CREDIT_CARD }
    val liabilityAccounts = accounts.filter { it.type == AccountType.CREDIT_CARD }
    val accountAssetsTotal = assetAccounts.sumOf { accountValue(it, netByAccountId) }
    val accountLiabilitiesTotal = liabilityAccounts.sumOf { accountValue(it, netByAccountId) }

    val debtAssets = debts.filter { it.direction == DebtDirection.LEND && !it.isSettled }
    val debtLiabilities = debts.filter { it.direction == DebtDirection.BORROW && !it.isSettled }
    val debtAssetsTotal = debtAssets.sumOf { it.amount }
    val debtLiabilitiesTotal = debtLiabilities.sumOf { it.amount }

    val totalAssets = accountAssetsTotal + debtAssetsTotal
    val totalLiabilities = accountLiabilitiesTotal + debtLiabilitiesTotal
    val netColor = if (totalAssets >= totalLiabilities) IncomeGreen else ExpenseRed

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("資產與負債", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColoredSummaryLine("資產", money(totalAssets), IncomeGreen)
                    ColoredSummaryLine("負債", money(totalLiabilities), ExpenseRed)
                    HorizontalDivider()
                    ColoredSummaryLine("淨值", money(totalAssets - totalLiabilities), netColor, bold = true)
                }
            }
            Text(
                "資產＝帳戶（現金/銀行/電子錢包）＋未還的借出/代墊；負債＝信用卡欠款＋未還的借入。借貸紀錄不計入收入/支出統計，已還清的借貸紀錄不會顯示在下方。",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("帳戶（資產）", fontWeight = FontWeight.SemiBold)
            if (assetAccounts.isEmpty()) {
                Text("目前沒有現金/銀行/電子錢包帳戶。", style = MaterialTheme.typography.bodySmall)
            } else {
                assetAccounts.forEach { account ->
                    AccountBalanceRow(account, money(accountValue(account, netByAccountId)), IncomeGreen)
                    HorizontalDivider()
                }
            }

            Text("帳戶（負債）", fontWeight = FontWeight.SemiBold)
            if (liabilityAccounts.isEmpty()) {
                Text("目前沒有信用卡帳戶。", style = MaterialTheme.typography.bodySmall)
            } else {
                liabilityAccounts.forEach { account ->
                    AccountBalanceRow(account, money(accountValue(account, netByAccountId)), ExpenseRed)
                    HorizontalDivider()
                }
            }

            Text("借貸（資產）", fontWeight = FontWeight.SemiBold)
            if (debtAssets.isEmpty()) {
                Text("目前沒有借出/代墊紀錄。", style = MaterialTheme.typography.bodySmall)
            } else {
                debtAssets.forEach { debt ->
                    DebtListItem(debt = debt, onClick = onOpenDebts)
                    HorizontalDivider()
                }
            }

            Text("借貸（負債）", fontWeight = FontWeight.SemiBold)
            if (debtLiabilities.isEmpty()) {
                Text("目前沒有借入紀錄。", style = MaterialTheme.typography.bodySmall)
            } else {
                debtLiabilities.forEach { debt ->
                    DebtListItem(debt = debt, onClick = onOpenDebts)
                    HorizontalDivider()
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("關閉") }
        }
    }
}

@Composable
private fun AccountBalanceRow(account: AccountEntity, value: String, color: Color) {
    ListItem(
        headlineContent = { Text(account.name) },
        supportingContent = { Text(accountTypeLabel(account.type)) },
        trailingContent = { Text(value, fontWeight = FontWeight.SemiBold, color = color) },
    )
}

@Composable
private fun ColoredSummaryLine(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtManagementSheet(
    debts: List<DebtEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onAdd: (DebtDirection, String, Long, Long, Long?, String, Long) -> Unit,
    onSettle: (Long, Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var filterDirection by remember { mutableStateOf(DebtDirection.LEND) }
    var showAddForm by remember { mutableStateOf(false) }
    var debtPendingDelete by remember { mutableStateOf<DebtEntity?>(null) }
    var debtPendingSettle by remember { mutableStateOf<DebtEntity?>(null) }
    val filtered = debts.filter { it.direction == filterDirection && !it.isSettled }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("借貸", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = filterDirection == DebtDirection.LEND, onClick = { filterDirection = DebtDirection.LEND }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("我借出/代墊的") }
                SegmentedButton(selected = filterDirection == DebtDirection.BORROW, onClick = { filterDirection = DebtDirection.BORROW }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("我借入的") }
            }
            if (filtered.isEmpty()) {
                Text(if (filterDirection == DebtDirection.LEND) "目前沒有借出/代墊的紀錄。" else "目前沒有借入的紀錄。")
            } else {
                val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
                filtered.forEach { debt ->
                    ListItem(
                        headlineContent = { Text(debt.counterparty, color = if (debt.isSettled) mutedColor else Color.Unspecified) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(formatDateOnly(debt.occurredAt))
                                    if (debt.note.isNotBlank()) append(" · ${debt.note}")
                                    if (debt.isSettled) append(" · 已還清")
                                }
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    money(debt.amount),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (debt.isSettled) mutedColor else ExpenseRed,
                                )
                                if (!debt.isSettled) {
                                    TextButton(onClick = { debtPendingSettle = debt }) { Text("標記已還") }
                                }
                                TextButton(onClick = { debtPendingDelete = debt }) { Text("刪除") }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
            OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增借貸") }
        }
    }

    if (showAddForm) {
        DebtFormDialog(
            defaultDirection = filterDirection,
            knownCounterparties = remember(debts) { debts.map { it.counterparty }.distinct() },
            accounts = accounts,
            onDismiss = { showAddForm = false },
            onSave = { direction, counterparty, amount, occurredAt, dueDate, note, accountId ->
                onAdd(direction, counterparty, amount, occurredAt, dueDate, note, accountId)
                showAddForm = false
            },
        )
    }

    debtPendingDelete?.let { debt ->
        AlertDialog(
            onDismissRequest = { debtPendingDelete = null },
            title = { Text("刪除這筆借貸紀錄？") },
            text = { Text("刪除後無法復原。") },
            confirmButton = { TextButton(onClick = { onDelete(debt.id); debtPendingDelete = null }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { debtPendingDelete = null }) { Text("取消") } },
        )
    }

    debtPendingSettle?.let { debt ->
        var settleAccountId by remember(debt.id) { mutableStateOf(accounts.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { debtPendingSettle = null },
            title = { Text("標記已還") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (debt.direction == DebtDirection.LEND) "錢收回哪個帳戶？" else "從哪個帳戶還款？")
                    if (accounts.isEmpty()) {
                        Text("請先到設定新增一個帳戶。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.forEach { account ->
                                FilterChip(selected = settleAccountId == account.id, onClick = { settleAccountId = account.id }, label = { Text(account.name) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { settleAccountId?.let { onSettle(debt.id, it) }; debtPendingSettle = null },
                    enabled = settleAccountId != null,
                ) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { debtPendingSettle = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtFormDialog(
    defaultDirection: DebtDirection,
    knownCounterparties: List<String>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (DebtDirection, String, Long, Long, Long?, String, Long) -> Unit,
) {
    var direction by remember { mutableStateOf(defaultDirection) }
    var counterparty by remember { mutableStateOf("") }
    var showCounterpartySuggestions by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var showCalculator by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var occurredAtMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    val counterpartySuggestions = remember(counterparty, knownCounterparties) {
        if (counterparty.isBlank()) emptyList()
        else knownCounterparties.filter { it.contains(counterparty, ignoreCase = true) && it != counterparty }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("新增借貸", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = direction == DebtDirection.LEND, onClick = { direction = DebtDirection.LEND }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("借出/代墊") }
                SegmentedButton(selected = direction == DebtDirection.BORROW, onClick = { direction = DebtDirection.BORROW }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("借入") }
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it; showCounterpartySuggestions = true },
                    label = { Text("對象") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = showCounterpartySuggestions && counterpartySuggestions.isNotEmpty(),
                    onDismissRequest = { showCounterpartySuggestions = false },
                    properties = PopupProperties(focusable = false),
                ) {
                    counterpartySuggestions.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { counterparty = name; showCounterpartySuggestions = false },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter(Char::isDigit) },
                label = { Text("金額") },
                prefix = { Text("NT$ ") },
                trailingIcon = { IconButton(onClick = { showCalculator = true }) { Text("🧮") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("日期：${formatDateOnly(occurredAtMillis)}")
            }
            Text(if (direction == DebtDirection.LEND) "從哪個帳戶付出去" else "錢收進哪個帳戶", fontWeight = FontWeight.SemiBold)
            if (accounts.isEmpty()) {
                Text("請先到設定新增一個帳戶。", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        FilterChip(selected = accountId == account.id, onClick = { accountId = account.id }, label = { Text(account.name) })
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("備註") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        val amount = amountText.toLongOrNull() ?: return@Button
                        val selectedAccountId = accountId ?: return@Button
                        onSave(direction, counterparty.trim(), amount, occurredAtMillis, null, note.trim(), selectedAccountId)
                    },
                    enabled = counterparty.isNotBlank() && (amountText.toLongOrNull() ?: 0) > 0 && accountId != null,
                    modifier = Modifier.weight(1f),
                ) { Text("儲存") }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = occurredAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedUtcMillis ->
                        val pickedDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        val existingTime = Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                        occurredAtMillis = pickedDate.atTime(existingTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCalculator) {
        CalculatorDialog(
            initialExpression = amountText,
            onDismiss = { showCalculator = false },
            onConfirm = { amountText = it.toString(); showCalculator = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManagementSheet(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, AccountType, Long) -> Unit,
    onEdit: (Long, String, AccountType, Long) -> Unit,
    onToggleActive: (Long, Boolean) -> Unit,
) {
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var filterLiability by remember { mutableStateOf(false) }
    val filtered = accounts.filter { (it.type == AccountType.CREDIT_CARD) == filterLiability }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("帳戶管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !filterLiability, onClick = { filterLiability = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("資產") }
                SegmentedButton(selected = filterLiability, onClick = { filterLiability = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("負債") }
            }
            filtered.forEach { account ->
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
            defaultType = if (filterLiability) AccountType.CREDIT_CARD else AccountType.CASH,
            onDismiss = { showAddForm = false },
            onSave = { name, type, openingBalance -> onAdd(name, type, openingBalance); showAddForm = false },
        )
    }
    editingAccount?.let { account ->
        AccountFormDialog(
            existing = account,
            defaultType = account.type,
            onDismiss = { editingAccount = null },
            onSave = { name, type, openingBalance -> onEdit(account.id, name, type, openingBalance); editingAccount = null },
        )
    }
}

@Composable
private fun AccountFormDialog(
    existing: AccountEntity?,
    defaultType: AccountType,
    onDismiss: () -> Unit,
    onSave: (String, AccountType, Long) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: defaultType) }
    var openingBalanceText by remember { mutableStateOf(existing?.openingBalance?.toString() ?: "0") }

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
                OutlinedTextField(
                    value = openingBalanceText,
                    onValueChange = { openingBalanceText = it.filter(Char::isDigit) },
                    label = { Text(if (type == AccountType.CREDIT_CARD) "目前欠款" else "目前餘額") },
                    prefix = { Text("NT$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (type == AccountType.CREDIT_CARD) "會被算成負債；之後刷卡消費會讓欠款增加。"
                    else "會被算成資產；之後的收入/支出會從這個金額增減。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), type, openingBalanceText.toLongOrNull() ?: 0) },
                enabled = name.isNotBlank(),
            ) { Text("儲存") }
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
    var filterType by remember { mutableStateOf(TransactionType.EXPENSE) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("分類管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = filterType == TransactionType.EXPENSE, onClick = { filterType = TransactionType.EXPENSE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                SegmentedButton(selected = filterType == TransactionType.INCOME, onClick = { filterType = TransactionType.INCOME }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
            }
            categories.filter { it.type == filterType }.forEach { category ->
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
            defaultType = filterType,
            onDismiss = { showAddForm = false },
            onSave = { name, type, icon -> onAdd(name, type, icon); showAddForm = false },
        )
    }
    editingCategory?.let { category ->
        CategoryFormDialog(
            existing = category,
            defaultType = category.type,
            onDismiss = { editingCategory = null },
            onSave = { name, type, icon -> onEdit(category.id, name, type, icon); editingCategory = null },
        )
    }
}

private val categoryIconPalette = listOf(
    "🍜", "🍔", "☕", "🍎", "🛒", "🛍️", "👗", "🚗", "🚌", "⛽",
    "🏠", "💡", "📱", "💊", "🏥", "🎬", "🎮", "🎵", "📚", "✈️",
    "🎁", "💼", "💰", "📈", "🐾", "👶", "⚽", "🔧", "📦", "❤️",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFormDialog(
    existing: CategoryEntity?,
    defaultType: TransactionType,
    onDismiss: () -> Unit,
    onSave: (String, TransactionType, String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: defaultType) }
    var icon by remember { mutableStateOf(existing?.icon ?: categoryIconPalette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增分類" else "編輯分類") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("圖示", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoryIconPalette.forEach { emoji ->
                        val selected = icon == emoji
                        Box(
                            modifier = Modifier.size(40.dp)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = emoji },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
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
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    val data = if (type == TransactionType.EXPENSE) expenseByCategory else incomeByCategory
    val total = data.sumOf { it.total }.coerceAtLeast(1)
    val colors = chartColors(data.size)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPreviousMonth) { Text("‹ 上個月") }
                Text("$monthLabel 統計", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNextMonth) { Text("下個月 ›") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                SegmentedButton(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
            }
            if (data.isEmpty()) {
                Text(if (type == TransactionType.EXPENSE) "這個月還沒有支出紀錄。" else "這個月還沒有收入紀錄。")
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CategoryPieChart(
                        data = data,
                        colors = colors,
                        centerLabel = if (type == TransactionType.EXPENSE) "支出" else "收入",
                        centerAmount = money(total),
                        modifier = Modifier.size(220.dp),
                    )
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
private fun CategoryPieChart(
    data: List<CategoryTotal>,
    colors: List<Color>,
    centerLabel: String,
    centerAmount: String,
    modifier: Modifier = Modifier,
) {
    val total = data.sumOf { it.total }.coerceAtLeast(1)
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Canvas(modifier) {
        val strokeWidth = size.minDimension * 0.26f
        val labelRadius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        var startAngle = -90f
        val slicePercentPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            textSize = strokeWidth * 0.34f
        }
        data.forEachIndexed { index, item ->
            val sweep = 360f * item.total / total
            drawArc(
                color = colors[index],
                startAngle = startAngle,
                sweepAngle = sweep.coerceAtLeast(0.5f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            val percent = (item.total * 100 / total).toInt()
            if (sweep >= 20f) {
                val midAngleRad = Math.toRadians((startAngle + sweep / 2).toDouble())
                val labelX = center.x + labelRadius * cos(midAngleRad).toFloat()
                val labelY = center.y + labelRadius * sin(midAngleRad).toFloat() + slicePercentPaint.textSize / 3
                drawContext.canvas.nativeCanvas.drawText("$percent%", labelX, labelY, slicePercentPaint)
            }
            startAngle += sweep
        }
        val amountPaint = Paint().apply {
            color = onSurface
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            textSize = size.minDimension * 0.13f
        }
        val labelPaint = Paint().apply {
            color = onSurfaceVariant
            textAlign = Paint.Align.CENTER
            textSize = size.minDimension * 0.075f
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText(centerLabel, center.x, center.y - amountPaint.textSize * 0.3f, labelPaint)
            drawText(centerAmount, center.x, center.y + amountPaint.textSize * 0.65f, amountPaint)
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
private fun formatDateOnly(epoch: Long): String = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date(epoch))

private val calculatorOperators = charArrayOf('+', '−', '×', '÷')

/** Appends a keypress to a calculator expression, keeping it always well-formed for [evaluateCalculatorExpression]. */
private fun applyCalculatorKey(expression: String, key: String): String = when (key) {
    "C" -> ""
    "⌫" -> if (expression.isNotEmpty()) expression.dropLast(1) else expression
    "+", "−", "×", "÷" -> when {
        expression.isEmpty() -> expression
        expression.last() in calculatorOperators -> expression.dropLast(1) + key
        else -> expression + key
    }
    else -> if (expression.length >= 12) expression else expression + key
}

/** Evaluates a +-×÷ expression built from [applyCalculatorKey] presses, honoring ×÷ before +−. Returns null while incomplete or invalid (e.g. divide by zero). */
private fun evaluateCalculatorExpression(expression: String): Long? {
    if (expression.isEmpty() || expression.last() in calculatorOperators) return null
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    for (c in expression) {
        if (c in calculatorOperators) {
            tokens.add(current.toString()); current.clear()
            tokens.add(c.toString())
        } else current.append(c)
    }
    tokens.add(current.toString())
    if (tokens.any { it.isEmpty() }) return null

    val reduced = mutableListOf(tokens[0].toDoubleOrNull() ?: return null)
    var i = 1
    while (i < tokens.size) {
        val op = tokens[i]
        val value = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: return null
        if (op == "×" || op == "÷") {
            val prev = reduced.removeAt(reduced.size - 1)
            if (op == "÷" && value == 0.0) return null
            reduced.add(if (op == "×") prev * value else prev / value)
        } else {
            reduced.add(if (op == "+") value else -value)
        }
        i += 2
    }
    return Math.round(reduced.sum())
}

@Composable
private fun CalculatorKeypad(expression: String, onExpressionChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val amount = remember(expression) { evaluateCalculatorExpression(expression) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Text(
                    "NT$ ${expression.ifEmpty { "0" }}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (expression.any { it in calculatorOperators } && amount != null) {
                    Text("= ${money(amount)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "−"),
            listOf("C", "0", "⌫", "+"),
        ).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    OutlinedButton(onClick = { onExpressionChange(applyCalculatorKey(expression, key)) }, modifier = Modifier.weight(1f)) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** A popup calculator: builds an expression on the keypad, then "帶入" evaluates it and hands the result back to the caller's amount field. */
@Composable
private fun CalculatorDialog(initialExpression: String, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var expression by remember { mutableStateOf(initialExpression) }
    val result = evaluateCalculatorExpression(expression)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("計算機") },
        text = { CalculatorKeypad(expression = expression, onExpressionChange = { expression = it }) },
        confirmButton = {
            TextButton(onClick = { result?.let(onConfirm) }, enabled = (result ?: 0) > 0) { Text("帶入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
