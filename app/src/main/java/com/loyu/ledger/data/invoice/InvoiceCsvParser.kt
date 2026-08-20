package com.loyu.ledger.data.invoice

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class InvoiceCsvLineItem(
    val name: String,
    val subtotal: Long,
)

data class InvoiceCsvReceipt(
    val invoiceNumber: String,
    val occurredAt: LocalDateTime,
    val merchant: String,
    val items: List<InvoiceCsvLineItem>,
) {
    val total: Long get() = items.sumOf { it.subtotal }
}

/**
 * Parses the app's own "分享" CSV export (消費時間,發票號碼,店家名稱,賣方統編,消費品項,單價,個數,小計,總計,備註).
 * Only this format is supported: it carries a 發票號碼 (invoice number), which groups rows into
 * receipts exactly. Older "消費明細" downloads without an invoice number have to guess grouping
 * from date+merchant and can misfire on two same-day visits to the same store, so they're rejected
 * outright rather than silently imported with a worse heuristic.
 */
object InvoiceCsvParser {
    private val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("H:mm:ss")

    fun parse(csvText: String): List<InvoiceCsvReceipt> {
        val lines = csvText.removePrefix("﻿").lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = parseCsvLine(lines[0]).map { it.trim() }

        val dateIdx = header.indexOf("消費時間")
        val invoiceNumberIdx = header.indexOf("發票號碼")
        val itemIdx = header.indexOf("消費品項")
        val subtotalIdx = header.indexOf("小計")
        val merchantIdx = header.indexOf("店家名稱")
        require(invoiceNumberIdx >= 0) { "這個 CSV 不含「發票號碼」欄位，目前只支援 App 內建「分享」功能匯出的格式。" }
        require(dateIdx >= 0 && itemIdx >= 0 && subtotalIdx >= 0 && merchantIdx >= 0) { "CSV 欄位格式不符，缺少必要欄位。" }
        val maxIdx = maxOf(dateIdx, invoiceNumberIdx, itemIdx, subtotalIdx, merchantIdx)

        data class Row(val invoiceNumber: String, val occurredAt: LocalDateTime, val merchant: String, val item: InvoiceCsvLineItem)
        val rows = lines.drop(1).mapNotNull { line ->
            val fields = parseCsvLine(line)
            if (fields.size <= maxIdx) return@mapNotNull null
            val occurredAt = parseDateTime(fields[dateIdx].trim()) ?: return@mapNotNull null
            val subtotal = fields[subtotalIdx].trim().toDoubleOrNull()?.let { Math.round(it) } ?: return@mapNotNull null
            val merchant = fields[merchantIdx].trim()
            val invoiceNumber = fields[invoiceNumberIdx].trim()
            if (merchant.isBlank() || invoiceNumber.isBlank()) return@mapNotNull null
            Row(invoiceNumber, occurredAt, merchant, InvoiceCsvLineItem(fields[itemIdx].trim(), subtotal))
        }

        return rows.groupBy { it.invoiceNumber }.values.map { group ->
            val first = group.first()
            InvoiceCsvReceipt(first.invoiceNumber, first.occurredAt, first.merchant, group.map { it.item })
        }
    }

    /** Accepts "yyyy/M/d H:mm:ss", falling back to date-only (midnight) when there's no time part. */
    private fun parseDateTime(raw: String): LocalDateTime? {
        val spaceIndex = raw.indexOf(' ')
        if (spaceIndex < 0) {
            val date = runCatching { LocalDate.parse(raw, dateOnlyFormatter) }.getOrNull() ?: return null
            return date.atStartOfDay()
        }
        val date = runCatching { LocalDate.parse(raw.substring(0, spaceIndex), dateOnlyFormatter) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(raw.substring(spaceIndex + 1), timeOnlyFormatter) }.getOrNull() ?: LocalTime.MIDNIGHT
        return LocalDateTime.of(date, time)
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { fields.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }
}
