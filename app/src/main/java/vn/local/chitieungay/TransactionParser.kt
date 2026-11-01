package vn.local.chitieungay

import java.security.MessageDigest
import java.util.Locale

/** Pure Kotlin, no Android dependencies. Reject ambiguous texts rather than fabricating a transaction. */
data class ParsedBankTx(val amount: Long, val direction: String, val description: String)

object TransactionParser {
    private val amountRx = Regex("(?<![\\p{L}\\d])([+-]?\\s*\\d{1,15}(?:[.,]\\d{3})*)(?:\\s*)(?:VND|VNĐ|đ|₫)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val signalRx = Regex("(?:biến động|bien dong|phát sinh|phat sinh|giao dịch|giao dich|ghi nợ|ghi no|ghi có|ghi co|chuyển khoản|chuyen khoan|thanh toán|thanh toan|rút tiền|rut tien|nhận tiền|nhan tien|tiền vào|tiền ra|trừ tiền|tru tien|nhận được|nhan duoc|số dư|so du|tài khoản|tai khoan)", RegexOption.IGNORE_CASE)
    private val balanceContext = Regex("(?:số dư|so du|số dư cuối|so du cuoi|balance)\\s*(?:hiện tại|hien tai|cuối|cuoi|:|=|là|la)?\\s*$", RegexOption.IGNORE_CASE)
    private val incoming = Regex("(?:ghi có|ghi co|nhận tiền|nhan tien|tiền vào|tien vao|được cộng|duoc cong|chuyển đến|chuyen den|báo có|bao co|credit|received)", RegexOption.IGNORE_CASE)
    private val outgoing = Regex("(?:ghi nợ|ghi no|trừ tiền|tru tien|tiền ra|tien ra|rút tiền|rut tien|thanh toán|thanh toan|chuyển đi|chuyen di|báo nợ|bao no|debit|spent)", RegexOption.IGNORE_CASE)
    private val preferred = Regex("(?:số tiền|so tien|phát sinh|phat sinh|số tiền gd|giao dịch|giao dich|gd|amount|ghi nợ|ghi có|ghi no|ghi co|trừ tiền|nhận tiền|thanh toán|chuyển khoản)\\s*(?:[:=]|là|la)?\\s*$", RegexOption.IGNORE_CASE)

    fun parse(title: String, body: String): ParsedBankTx? {
        val raw = "$title\n$body".trim().take(2400)
        if (!signalRx.containsMatchIn(raw)) return null
        val candidates = amountRx.findAll(raw).filter { m ->
            val prior = raw.substring(maxOf(0, m.range.first - 36), m.range.first)
            !balanceContext.containsMatchIn(prior)
        }.toList()
        if (candidates.isEmpty()) return null
        // Prefer an amount explicitly labeled as the transaction amount, not a balance.
        val m = candidates.firstOrNull { match ->
            val prior = raw.substring(maxOf(0, match.range.first - 45), match.range.first)
            preferred.containsMatchIn(prior)
        } ?: candidates.firstOrNull { it.groupValues[1].trimStart().startsWith('+') || it.groupValues[1].trimStart().startsWith('-') }
          ?: if (candidates.size == 1) candidates[0] else return null

        val matched = m.groupValues[1].replace(" ", "")
        val digits = matched.replace(Regex("[^0-9]"), "")
        val amount = digits.toLongOrNull() ?: return null
        if (amount <= 0L || amount > 9_000_000_000_000L) return null

        val nearby = raw.substring(maxOf(0, m.range.first - 100), minOf(raw.length, m.range.last + 101))
        val direction = when {
            matched.startsWith('+') -> "income"
            matched.startsWith('-') -> "expense"
            incoming.containsMatchIn(nearby) && !outgoing.containsMatchIn(nearby) -> "income"
            outgoing.containsMatchIn(nearby) && !incoming.containsMatchIn(nearby) -> "expense"
            incoming.containsMatchIn(raw) && !outgoing.containsMatchIn(raw) -> "income"
            outgoing.containsMatchIn(raw) && !incoming.containsMatchIn(raw) -> "expense"
            else -> "unknown"
        }
        return ParsedBankTx(amount, direction, raw)
    }

    fun fingerprint(packageName: String, title: String, text: String, timestamp: Long): String {
        val normalized = "$packageName|$title|$text".lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        val bucket = timestamp / 120_000L // best-effort: duplicate notification updates within 2 min
        val bytes = MessageDigest.getInstance("SHA-256").digest("$normalized|$bucket".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
