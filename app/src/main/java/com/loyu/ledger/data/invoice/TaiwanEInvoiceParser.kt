package com.loyu.ledger.data.invoice

import java.time.LocalDate

data class InvoiceItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Long,
)

data class ParsedInvoice(
    val invoiceNumber: String,
    val date: LocalDate,
    val totalAmount: Long,
    val sellerId: String,
    val items: List<InvoiceItem>,
)

/**
 * Parses the QR code(s) printed on a Taiwan e-invoice (電子發票證明聯), per the Ministry of
 * Finance's "電子發票證明聯一維及二維條碼規格說明". The left QR always carries invoice
 * number/date/amounts/tax IDs (77 fixed-width characters, then colon-delimited trailer fields);
 * item details either follow inline in that trailer or appear in a second "right" QR.
 */
object TaiwanEInvoiceParser {

    // salesAmountHex(8) + totalAmountHex(8) + buyerId(8) + sellerId(8) + AES verification(24)
    private const val FIXED_SUFFIX_LEN = 8 + 8 + 8 + 8 + 24

    fun parse(codes: List<String>): ParsedInvoice? {
        val leftCandidates = codes.filter { !it.startsWith("**") }
        val externalItemsRaw = codes.firstOrNull { it.startsWith("**") }
        for (raw in leftCandidates) {
            val parsedLeft = parseLeft(raw) ?: continue
            val (invoice, trailer) = parsedLeft
            val inlineItems = parseItemsTrailer(trailer)
            val items = inlineItems.ifEmpty { externalItemsRaw?.let(::parseItemList) ?: emptyList() }
            return invoice.copy(items = items)
        }
        return null
    }

    private fun parseLeft(raw: String): Pair<ParsedInvoice, String>? {
        if (raw.length < 10) return null
        val invoiceNumber = raw.substring(0, 10)
        // ROC year is 3 digits from ROC 100 (2011) through ROC 999 (2910); fall back to 2
        // digits defensively for any older/edge-case invoice.
        for (dateLen in intArrayOf(7, 6)) {
            val afterDateRandom = 10 + dateLen + 4
            val fixedEnd = afterDateRandom + FIXED_SUFFIX_LEN
            if (raw.length <= fixedEnd || raw[fixedEnd] != ':') continue

            val dateStr = raw.substring(10, 10 + dateLen)
            val rocYear = dateStr.substring(0, dateLen - 4).toIntOrNull() ?: continue
            val month = dateStr.substring(dateLen - 4, dateLen - 2).toIntOrNull() ?: continue
            val day = dateStr.substring(dateLen - 2, dateLen).toIntOrNull() ?: continue
            val date = runCatching { LocalDate.of(rocYear + 1911, month, day) }.getOrNull() ?: continue

            var cursor = afterDateRandom
            cursor += 8 // salesAmountHex, unused
            val totalHex = raw.substring(cursor, cursor + 8); cursor += 8
            cursor += 8 // buyerId, unused
            val sellerId = raw.substring(cursor, cursor + 8); cursor += 8
            // remaining 24 chars are the AES-encrypted verification block; only the
            // government can decrypt it and it carries no bookkeeping-relevant data.

            val totalAmount = totalHex.toLongOrNull(16) ?: continue
            val trailer = raw.substring(fixedEnd)
            return ParsedInvoice(invoiceNumber, date, totalAmount, sellerId, emptyList()) to trailer
        }
        return null
    }

    /** Trailer format: ":" + info(10) + ":" + itemCount + ":" + totalQty + ":" + encodeType [+ items]. */
    private fun parseItemsTrailer(trailer: String): List<InvoiceItem> {
        if (!trailer.startsWith(":")) return emptyList()
        val parts = trailer.substring(1).split(":", limit = 5)
        val rest = parts.getOrNull(4) ?: return emptyList()
        return parseItemList(rest)
    }

    /** Item list format: "**" + name + ":" + qty + ":" + price, repeated with ":" between items. */
    private fun parseItemList(raw: String): List<InvoiceItem> {
        if (!raw.startsWith("**")) return emptyList()
        return raw.substring(2).split(":").chunked(3).mapNotNull { chunk ->
            if (chunk.size < 3) return@mapNotNull null
            InvoiceItem(
                name = chunk[0],
                quantity = chunk[1].toIntOrNull() ?: 1,
                unitPrice = chunk[2].toLongOrNull() ?: 0L,
            )
        }
    }
}
