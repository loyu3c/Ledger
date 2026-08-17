package com.loyu.ledger.data.backup

import com.loyu.ledger.data.local.AccountEntity
import com.loyu.ledger.data.local.AccountType
import com.loyu.ledger.data.local.CategoryEntity
import com.loyu.ledger.data.local.DebtDirection
import com.loyu.ledger.data.local.DebtEntity
import com.loyu.ledger.data.local.TransactionEntity
import com.loyu.ledger.data.local.TransactionType
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val debts: List<DebtEntity>,
)

/** Serializes the whole local database to/from a single JSON file for manual backup/restore. */
object BackupSerializer {
    private const val VERSION = 2

    fun toJson(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        debts: List<DebtEntity>,
    ): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put(
            "accounts",
            JSONArray().apply {
                accounts.forEach {
                    put(
                        JSONObject().apply {
                            put("id", it.id)
                            put("name", it.name)
                            put("type", it.type.name)
                            put("openingBalance", it.openingBalance)
                            put("isActive", it.isActive)
                        }
                    )
                }
            },
        )
        root.put(
            "categories",
            JSONArray().apply {
                categories.forEach {
                    put(
                        JSONObject().apply {
                            put("id", it.id)
                            put("name", it.name)
                            put("type", it.type.name)
                            put("icon", it.icon)
                            put("sortOrder", it.sortOrder)
                            put("isActive", it.isActive)
                        }
                    )
                }
            },
        )
        root.put(
            "transactions",
            JSONArray().apply {
                transactions.forEach {
                    put(
                        JSONObject().apply {
                            put("id", it.id)
                            put("type", it.type.name)
                            put("amount", it.amount)
                            put("accountId", it.accountId)
                            put("categoryId", it.categoryId)
                            put("merchant", it.merchant)
                            put("note", it.note)
                            put("occurredAt", it.occurredAt)
                            put("createdAt", it.createdAt)
                            put("updatedAt", it.updatedAt)
                        }
                    )
                }
            },
        )
        root.put(
            "debts",
            JSONArray().apply {
                debts.forEach {
                    put(
                        JSONObject().apply {
                            put("id", it.id)
                            put("direction", it.direction.name)
                            put("counterparty", it.counterparty)
                            put("amount", it.amount)
                            put("occurredAt", it.occurredAt)
                            put("dueDate", it.dueDate ?: JSONObject.NULL)
                            put("note", it.note)
                            put("isSettled", it.isSettled)
                            put("settledAt", it.settledAt ?: JSONObject.NULL)
                        }
                    )
                }
            },
        )
        return root.toString(2)
    }

    fun fromJson(json: String): BackupData {
        val root = JSONObject(json)

        val accounts = root.getJSONArray("accounts").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AccountEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    type = AccountType.valueOf(o.getString("type")),
                    openingBalance = o.optLong("openingBalance", 0),
                    isActive = o.optBoolean("isActive", true),
                )
            }
        }

        val categories = root.getJSONArray("categories").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CategoryEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    type = TransactionType.valueOf(o.getString("type")),
                    icon = o.optString("icon", "💰"),
                    sortOrder = o.optInt("sortOrder", 0),
                    isActive = o.optBoolean("isActive", true),
                )
            }
        }

        val transactions = root.getJSONArray("transactions").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TransactionEntity(
                    id = o.getLong("id"),
                    type = TransactionType.valueOf(o.getString("type")),
                    amount = o.getLong("amount"),
                    accountId = o.getLong("accountId"),
                    categoryId = o.getLong("categoryId"),
                    merchant = o.optString("merchant", ""),
                    note = o.optString("note", ""),
                    occurredAt = o.getLong("occurredAt"),
                    createdAt = o.optLong("createdAt", o.getLong("occurredAt")),
                    updatedAt = o.optLong("updatedAt", o.getLong("occurredAt")),
                )
            }
        }

        // Older backups (v1) predate the debts table; default to an empty list.
        val debts = (root.optJSONArray("debts") ?: JSONArray()).let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DebtEntity(
                    id = o.getLong("id"),
                    direction = DebtDirection.valueOf(o.getString("direction")),
                    counterparty = o.getString("counterparty"),
                    amount = o.getLong("amount"),
                    occurredAt = o.getLong("occurredAt"),
                    dueDate = if (o.isNull("dueDate")) null else o.getLong("dueDate"),
                    note = o.optString("note", ""),
                    isSettled = o.optBoolean("isSettled", false),
                    settledAt = if (o.isNull("settledAt")) null else o.getLong("settledAt"),
                )
            }
        }

        return BackupData(accounts, categories, transactions, debts)
    }
}
