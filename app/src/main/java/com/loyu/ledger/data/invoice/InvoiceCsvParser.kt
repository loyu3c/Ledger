package com.loyu.ledger.data.invoice

import java.time.LocalDate

data class InvoiceCsvLineItem(
    val name: String,
    val subtotal: Long,
)

data class InvoiceCsvReceipt(
    val date: LocalDate,
    val merchant: String,
    val items: List<InvoiceCsvLineItem>,
) {
    val total: Long get() = items.sumOf { it.subtotal }
}

/**
 * Parses a "消費明細" CSV export (消費日期,消費品項,單價,個數,小計,店家名稱) from third-party
 * e-invoice carrier apps, since there's no cloud e-invoice API integration in this local-first
 * app. Each row is one line item of a receipt; consecutive rows sharing the same date and
 * merchant are folded into a single receipt (discount/refund rows included), since the export
 * carries no invoice number to group by.
 */
object InvoiceCsvParser {
    fun parse(csvText: String): List<InvoiceCsvReceipt> {
        val lines = csvText.lineSequence().filter { it.isNotBlank() }.drop(1)
        val receipts = mutableListOf<InvoiceCsvReceipt>()
        var currentDate: LocalDate? = null
        var currentMerchant: String? = null
        var currentItems = mutableListOf<InvoiceCsvLineItem>()

        fun flush() {
            val date = currentDate
            val merchant = currentMerchant
            if (date != null && merchant != null && currentItems.isNotEmpty()) {
                receipts.add(InvoiceCsvReceipt(date, merchant, currentItems.toList()))
            }
            currentItems = mutableListOf()
        }

        for (line in lines) {
            val fields = parseCsvLine(line)
            if (fields.size < 6) continue
            val date = parseRocDate(fields[0].trim()) ?: continue
            val name = fields[1].trim()
            val subtotal = fields[4].trim().toDoubleOrNull()?.let { Math.round(it) } ?: continue
            val merchant = fields[5].trim()
            if (merchant.isBlank()) continue

            if (date != currentDate || merchant != currentMerchant) {
                flush()
                currentDate = date
                currentMerchant = merchant
            }
            currentItems.add(InvoiceCsvLineItem(name, subtotal))
        }
        flush()
        return receipts
    }

    private fun parseRocDate(raw: String): LocalDate? {
        if (raw.length != 7 || !raw.all { it.isDigit() }) return null
        val rocYear = raw.substring(0, 3).toInt()
        val month = raw.substring(3, 5).toInt()
        val day = raw.substring(5, 7).toInt()
        return runCatching { LocalDate.of(rocYear + 1911, month, day) }.getOrNull()
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
