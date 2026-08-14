package com.loyu.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.loyu.ledger.data.local.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(vm: LedgerViewModel) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val expense by vm.monthExpense.collectAsState()
    val income by vm.monthIncome.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Loyu 記帳") }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { showAdd = true }, text = { Text("＋ 記一筆") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("本月摘要", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SummaryCard(income = income, expense = expense)
            }
            item { Text("最近紀錄", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (transactions.isEmpty()) item { Text("還沒有紀錄，按右下角「記一筆」開始。") }
            items(transactions, key = { it.id }) { row ->
                ListItem(
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

    if (showAdd) {
        AddTransactionSheet(
            accounts = accounts,
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { type, amount, accountId, categoryId, merchant, note ->
                vm.addTransaction(type, amount, accountId, categoryId, merchant, note)
                showAdd = false
            },
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
private fun AddTransactionSheet(
    accounts: List<com.loyu.ledger.data.local.AccountEntity>,
    categories: List<com.loyu.ledger.data.local.CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (TransactionType, Long, Long, Long, String, String) -> Unit,
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val filteredCategories = categories.filter { it.type == type }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var categoryId by remember(type, filteredCategories) { mutableStateOf(filteredCategories.firstOrNull()?.id) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("新增記帳", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: return@Button
                    onSave(type, amount, accountId ?: return@Button, categoryId ?: return@Button, merchant, note)
                },
                enabled = (amountText.toLongOrNull() ?: 0) > 0 && accountId != null && categoryId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存") }
        }
    }
}

private fun money(value: Long): String = "NT$ ${NumberFormat.getIntegerInstance(Locale.TAIWAN).format(value)}"
private fun formatDate(epoch: Long): String = SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(Date(epoch))
