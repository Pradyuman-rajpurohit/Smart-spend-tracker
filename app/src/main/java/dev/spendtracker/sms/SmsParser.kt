package dev.spendtracker.sms

import dev.spendtracker.data.db.AutopayKind
import dev.spendtracker.data.db.TxType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Turns a bank alert into structured data. Pure Kotlin, no Android types, so it is unit tested
 * against sample messages from SBI, IDFC First and IndusInd plus card, mandate and due alerts.
 *
 * Anything that is not clearly a debit, a credit, a mandate set-up or a payment-due notice
 * is [Result.Ignored].
 */
object SmsParser {

    data class ParsedTxn(
        val type: TxType,
        val amountPaise: Long,
        val counterparty: String?,
        val accountLast4: String?,
        val bankHint: String?,
        val ref: String?,
    )

    data class ParsedMandate(
        val amountPaise: Long,
        val name: String,
        val kind: AutopayKind,
        /** Expected debit or expiry date, start of day, when the message says one. */
        val dueAt: Long?,
        val accountLast4: String?,
        val bankHint: String?,
        /** True when the message says the mandate was revoked, released or cancelled. */
        val cancelled: Boolean,
    )

    /** "Your EMI of Rs 4,500 is due on 5 Oct", "LazyPay bill of Rs 746 due by 3rd Oct", card statements. */
    data class ParsedDue(
        val amountPaise: Long,
        val name: String,
        val kind: AutopayKind,
        val dueAt: Long?,
        val accountLast4: String?,
        val bankHint: String?,
    )

    sealed interface Result {
        data class Transaction(val value: ParsedTxn) : Result
        data class Mandate(val value: ParsedMandate) : Result
        data class Due(val value: ParsedDue) : Result
        data object Ignored : Result
    }

    private const val CUR = """(?:inr|rs\.?|₹|rupees)"""
    private const val NUM = """([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
    private const val VERBS = """(?:debited|credited|spent|paid|withdrawn|received|sent|transferred|deducted|debit|credit)"""

    private val ignore = Regex(
        """\b(?:otp|one[\s-]?time\s+password|verification\s+code|has\s+requested|requested\s+(?:rs|inr|money|a\s+payment)|""" +
            """payment\s+request|collect\s+request|declined|failed|unsuccessful|not\s+processed|could\s+not\s+be|""" +
            """cashback\s+offer|congratulations|apply\s+now|loan\s+offer|pre-approved|invest\s+now|discount|coupon|voucher|""" +
            """recharge\s+now|password|login|kyc)\b""",
        RegexOption.IGNORE_CASE
    )

    private val mandateWord = Regex(
        """\b(?:mandate|autopay|auto-pay|auto\s+pay|e-mandate|emandate|standing\s+instruction|nach|si\s+(?:registered|set))\b""",
        RegexOption.IGNORE_CASE
    )
    private val mandateSetup = Regex(
        """\b(?:created|registered|set\s*up|setup|approved|successfully|authori[sz]ed|activated|initiated|accepted|blocked|""" +
            """has\s+been\s+set|is\s+set|will\s+be\s+debited|upcoming|scheduled|is\s+due|next\s+(?:debit|payment))\b""",
        RegexOption.IGNORE_CASE
    )
    private val mandateCancel = Regex(
        """\b(?:revoked|cancell?ed|released|unblocked|expired|paused|deleted|removed)\b""",
        RegexOption.IGNORE_CASE
    )
    private val pastDebit = Regex(
        """\b(?:has\s+been\s+debited|is\s+debited|was\s+debited|got\s+debited|debited\s+(?:from|by|for|with)|""" +
            """$CUR\s*[0-9][0-9,]*(?:\.[0-9]+)?\s+(?:has\s+been\s+|is\s+|was\s+)?(?:debited|deducted|paid|spent))\b""",
        RegexOption.IGNORE_CASE
    )
    private val pastCredit = Regex(
        """\b(?:has\s+been\s+credited|is\s+credited|was\s+credited|got\s+credited|credited\s+(?:by|with|to\s+your))\b""",
        RegexOption.IGNORE_CASE
    )
    private val futureDebit = Regex("""\bwill\s+be\s+(?:debited|deducted|charged)\b""", RegexOption.IGNORE_CASE)

    /** Payment-due notices: EMIs, loan instalments, pay-later bills, card statements, upcoming autopay debits. */
    private val dueWord = Regex(
        """\b(?:is\s+due|are\s+due|due\s+on|due\s+by|due\s+date|last\s+date|pay\s+by|payable\s+by|overdue|""" +
            """total\s+(?:amount\s+)?due|min(?:imum)?\s+(?:amount\s+)?due|amount\s+due|emi(?:\s+of|\s+for|\s+amount|\s+is)?|""" +
            """instal?lment|bill\s+(?:of|for|amount|is)|outstanding|payment\s+reminder|reminder|""" +
            """will\s+be\s+(?:debited|deducted|charged))\b""",
        RegexOption.IGNORE_CASE
    )
    private val totalDueAmount = Regex(
        """total\s+(?:amount\s+)?(?:due|payable|outstanding)\s*(?:of|is|:|-)?\s*$CUR?\s*$NUM""",
        RegexOption.IGNORE_CASE
    )
    private val dueAmount = Regex(
        """\b(?:emi|instal?lment|bill|payment|amount|dues?)\s+(?:of|for|amount|is|:)?\s*$CUR\s*$NUM""",
        RegexOption.IGNORE_CASE
    )
    private val emiWord = Regex("""\b(?:emi|loan|instal?lment)\b""", RegexOption.IGNORE_CASE)
    private val cardWord = Regex("""\b(?:credit\s+card|card)\b""", RegexOption.IGNORE_CASE)
    private val knownLender = Regex(
        """\b(lazypay|simpl|slice|bajaj\s*finserv|bajaj\s*finance|home\s*credit|kreditbee|moneyview|money\s*view|navi|""" +
            """paytm\s*postpaid|amazon\s*pay\s*later|zestmoney|cred|uni\s*card|fibe|earlysalary|tata\s*capital|iifl|muthoot|""" +
            """manappuram|onecard|flipkart\s*pay\s*later|jupiter|freo|sbi\s*card|hdfc\s*card|icici\s*card|axis\s*card)\b""",
        RegexOption.IGNORE_CASE
    )

    private val amountBeforeVerb = Regex("""$CUR\s*$NUM\s+(?:has\s+been\s+|is\s+|was\s+|got\s+)?$VERBS\b""", RegexOption.IGNORE_CASE)
    private val amountAfterVerb = Regex("""\b$VERBS\s+(?:by|for|with|of|:)?\s*$CUR?\s*$NUM""", RegexOption.IGNORE_CASE)
    private val amountWithCurrency = Regex("""$CUR\s*$NUM|$NUM\s*$CUR""", RegexOption.IGNORE_CASE)
    private val balanceWords = Regex("""(?:bal|balance|limit|avl|available|outstanding)""", RegexOption.IGNORE_CASE)

    private val expenseWord = Regex(
        """\b(?:debited|spent|withdrawn|paid|sent|purchase|payment\s+of|deducted|transferred\s+to|trf\s+to|debit)\b""",
        RegexOption.IGNORE_CASE
    )
    private val incomeWord = Regex(
        """\b(?:credited|received|deposited|refund(?:ed)?|cashback|added\s+to|credit)\b""",
        RegexOption.IGNORE_CASE
    )

    private val accountRe = Regex(
        """\b(?:a/c|a\.c\.|ac|acct|account|card|cr\s+card|credit\s+card|debit\s+card)\s*(?:no\.?|number|#)?\s*:?\s*""" +
            """(?:ending(?:\s+with|\s+in)?\s*)?[x*#]*\s?(\d{3,6})\b""",
        RegexOption.IGNORE_CASE
    )

    private val upiPath = Regex("""\bUPI[/\-](\S+)""", RegexOption.IGNORE_CASE)
    private val vpaRe = Regex("""\b([a-z0-9][a-z0-9._\-]+@[a-z][a-z0-9]+)\b""", RegexOption.IGNORE_CASE)
    private const val NAME = """([A-Za-z0-9][A-Za-z0-9@._&'\- ]{1,40}?)"""
    private const val STOP = """(?=\s*(?:\(|on\s+\d|on\s+date|dated|ref|refno|upi|txn|via|from\s+a/c|to\s+a/c|avl|bal|info|not\s+you|if\s+not|umn|is\s+successful|\.\s|\.$|,|;|$))"""
    private val toPattern = Regex(
        """\b(?:trf\s+to|transfer(?:red)?\s+to|paid\s+to|sent\s+to|towards|to|at)\s+(?:vpa\s+|merchant\s+)?$NAME$STOP""",
        RegexOption.IGNORE_CASE
    )
    private val fromPattern = Regex(
        """\b(?:received\s+from|credited\s+by|from|by)\s+(?:vpa\s+)?$NAME$STOP""",
        RegexOption.IGNORE_CASE
    )
    private val rejectName = Regex(
        """^(?:(?:rs\.?|inr|₹)?\s*[\d,.]+|(?:rs\.?|inr|₹)\s*[\d,.]+.*|your\b.*|ur\b.*|a/c\b.*|ac\b.*|acct\b.*|account\b.*|the\b.*|this\b.*|that\b.*|""" +
            """date\b.*|upi|vpa|linked\b.*|card\b.*|mobile\b.*|number\b.*|no\.?|neft|imps|rtgs|transfer|bank\s+a/c.*|""" +
            """loan\b.*|emi\b.*|bill\b.*|payment\b.*|amount\b.*|""" +
            """\d{1,2}[-/.]?[a-z]{3}[-/.]?\d{2,4}.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val pathSkip = setOf("p2m", "p2a", "cr", "dr", "upi", "pay", "collect", "na", "in", "ind", "mandate")

    private val refRe = Regex(
        """\b(?:ref(?:erence)?(?:\s*no\.?|\s*number|\s*#|\s*id)?|refno|rrn|utr|txn(?:\s*id|\s*no\.?|\s*number)?|""" +
            """transaction\s*(?:id|no\.?|number)|upi\s*ref(?:\s*no\.?)?|imps\s*ref(?:\s*no\.?)?)\s*[:.\-]?\s*([A-Za-z0-9]{6,})""",
        RegexOption.IGNORE_CASE
    )
    private val upiRefInPath = Regex("""\bUPI[/\-](?:[A-Za-z0-9]+[/\-])?(\d{9,})""", RegexOption.IGNORE_CASE)
    private val twelveDigits = Regex("""\b(\d{12})\b""")

    /** 28Sep26, 28-Sep-2026, 28/09/26, 3rd Oct (no year), 03 Oct 2026. */
    private val dateRe = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[-/. ]?([A-Za-z]{3,9}|\d{1,2})(?:[-/. ]?(\d{4}|\d{2}))?\b""")
    private val dateLead = Regex(
        """\b(?:till|until|upto|up\s+to|valid\s+(?:till|until|upto)|expir(?:es|y|ing)(?:\s+on)?|next\s+(?:debit|payment|due)(?:\s+(?:on|date))?|""" +
            """due\s+(?:on|by|date(?:\s+is)?)|last\s+date(?:\s+(?:is|of|for|to\s+pay))?|pay\s+by|payable\s+by|before|on|by)\s*:?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val ipoWord = Regex("""\bIPO\b""", RegexOption.IGNORE_CASE)
    private val notCompanyWords = setOf("rs", "inr", "upi", "mandate", "for", "of", "the", "a/c", "dear", "customer", "your", "towards", "to", "in", "on", "via")
    private val knownSubscription = Regex(
        """\b(netflix|spotify|amazon\s*prime|prime\s*video|hotstar|jio\s*cinema|jiohotstar|youtube(?:\s*premium)?|google\s*one|""" +
            """google\s*play|apple(?:\s*(?:music|tv|one))?|icloud|sony\s*liv|zee5|airtel|jio|vi|swiggy\s*one|zomato\s*gold|""" +
            """cult(?:\.fit)?|microsoft|adobe|chatgpt|openai|claude|canva|linkedin|dropbox|notion|audible|kindle|gaana|wynk)\b""",
        RegexOption.IGNORE_CASE
    )
    private val mandateFor = Regex(
        """\b(?:for|towards|to|of)\s+(?:vpa\s+|merchant\s+)?([A-Za-z][A-Za-z0-9&.' \-]{1,50}?)(?=\s*(?:\(|from\s+a/c|from\s+your|via|on\s+\d|has\s+been|is\s+|was\s+|will\s+|valid|till|until|upto|umn|ref|for\s+(?:rs|inr)|\.\s|\.$|,|;|$))""",
        RegexOption.IGNORE_CASE
    )

    private val whitespace = Regex("""\s+""")

    fun parse(sender: String?, body: String, receivedAt: Long = System.currentTimeMillis()): Result {
        val text = body.replace(whitespace, " ").trim()
        if (text.length < 15) return Result.Ignored
        if (ignore.containsMatchIn(text) && !mandateWord.containsMatchIn(text)) return Result.Ignored

        val hint = bankHint(sender, text)
        val last4 = accountRe.find(text)?.groupValues?.get(1)?.takeLast(4)
        val moneyMoved = pastDebit.containsMatchIn(text) || pastCredit.containsMatchIn(text)

        if (mandateWord.containsMatchIn(text) && !moneyMoved) {
            return parseMandate(text, last4, hint, receivedAt)
        }
        if (dueWord.containsMatchIn(text) && !moneyMoved) {
            return parseDue(text, last4, hint, receivedAt)
        }
        if (futureDebit.containsMatchIn(text)) return Result.Ignored

        val amount = findAmount(text) ?: return Result.Ignored
        if (amount > 100_000_000_00L) return Result.Ignored

        val expenseAt = expenseWord.find(text)?.range?.first ?: Int.MAX_VALUE
        val incomeAt = incomeWord.find(text)?.range?.first ?: Int.MAX_VALUE
        if (expenseAt == Int.MAX_VALUE && incomeAt == Int.MAX_VALUE) return Result.Ignored
        val type = if (expenseAt <= incomeAt) TxType.EXPENSE else TxType.INCOME

        val counterparty = if (type == TxType.EXPENSE) {
            upiPathName(text) ?: firstName(toPattern, text) ?: vpaRe.find(text)?.groupValues?.get(1)
        } else {
            firstName(fromPattern, text) ?: vpaRe.find(text)?.groupValues?.get(1) ?: upiPathName(text)
        }

        return Result.Transaction(
            ParsedTxn(
                type = type,
                amountPaise = amount,
                counterparty = counterparty?.let(::prettify),
                accountLast4 = last4,
                bankHint = hint,
                ref = findRef(text)
            )
        )
    }

    private fun parseMandate(text: String, last4: String?, hint: String?, receivedAt: Long): Result {
        val cancelled = mandateCancel.containsMatchIn(text)
        if (!cancelled && !mandateSetup.containsMatchIn(text)) return Result.Ignored
        val amount = findAmount(text) ?: return Result.Ignored

        val kind: AutopayKind
        val name: String
        when {
            ipoWord.containsMatchIn(text) -> {
                kind = AutopayKind.IPO
                val company = ipoCompany(text) ?: firstName(mandateFor, text)
                name = when {
                    company == null -> "IPO mandate"
                    company.contains("ipo", ignoreCase = true) -> prettify(company)
                    else -> prettify(company) + " IPO"
                }
            }
            knownSubscription.containsMatchIn(text) -> {
                kind = AutopayKind.SUBSCRIPTION
                name = prettify(knownSubscription.find(text)!!.groupValues[1])
            }
            emiWord.containsMatchIn(text) -> {
                kind = AutopayKind.EMI
                name = knownLender.find(text)?.groupValues?.get(1)?.let(::prettify)
                    ?: firstName(mandateFor, text)?.let(::prettify)
                    ?: listOfNotNull(bankLabel(hint), "EMI").joinToString(" ")
            }
            Regex("""\b(?:insurance|premium|policy)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                kind = AutopayKind.INSURANCE
                name = firstName(mandateFor, text)?.let(::prettify) ?: "Insurance premium"
            }
            Regex("""\b(?:electricity|bill|broadband|dth|gas|water|postpaid|recharge)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                kind = AutopayKind.BILL
                name = firstName(mandateFor, text)?.let(::prettify) ?: "Bill autopay"
            }
            else -> {
                kind = AutopayKind.OTHER
                name = firstName(mandateFor, text)?.let(::prettify) ?: "Autopay"
            }
        }

        return Result.Mandate(
            ParsedMandate(
                amountPaise = amount,
                name = name.take(60),
                kind = kind,
                dueAt = findDueDate(text, receivedAt),
                accountLast4 = last4,
                bankHint = hint,
                cancelled = cancelled
            )
        )
    }

    private fun parseDue(text: String, last4: String?, hint: String?, receivedAt: Long): Result {
        val amount = totalDueAmount.find(text)?.let { toPaise(it.groupValues[1]) }
            ?: dueAmount.find(text)?.let { toPaise(it.groupValues[1]) }
            ?: findAmount(text)
            ?: return Result.Ignored
        if (amount > 100_000_000_00L) return Result.Ignored

        val isEmi = emiWord.containsMatchIn(text)
        val lender = knownLender.find(text)?.groupValues?.get(1)
        val subscription = knownSubscription.find(text)?.groupValues?.get(1)
        val kind = when {
            isEmi -> AutopayKind.EMI
            lender == null && subscription != null -> AutopayKind.SUBSCRIPTION
            else -> AutopayKind.BILL
        }
        val bank = bankLabel(hint)
        val name = lender?.let(::prettify)
            ?: subscription?.let(::prettify)
            ?: firstName(mandateFor, text)?.let(::prettify)
            ?: when {
                cardWord.containsMatchIn(text) -> listOfNotNull(bank, "card bill", last4?.let { "··$it" }).joinToString(" ")
                isEmi -> listOfNotNull(bank, "EMI").joinToString(" ")
                else -> listOfNotNull(bank, "bill").joinToString(" ")
            }

        return Result.Due(
            ParsedDue(
                amountPaise = amount,
                name = name.take(60),
                kind = kind,
                dueAt = findDueDate(text, receivedAt),
                accountLast4 = last4,
                bankHint = hint
            )
        )
    }

    // ---- pieces -------------------------------------------------------------

    /**
     * The capitalised words right before "IPO": "created for TATA CAPITAL LIMITED IPO" ->
     * "TATA CAPITAL LIMITED". Stops at the first ordinary lowercase word.
     */
    private fun ipoCompany(text: String): String? {
        val m = ipoWord.find(text) ?: return null
        val before = text.substring(0, m.range.first).trimEnd(' ', '-', ':', '/')
        val picked = ArrayList<String>()
        for (word in before.split(' ').asReversed()) {
            val clean = word.trim(',', '.', ';', ':', '(', ')', '"', '\'')
            if (clean.isEmpty()) break
            if (clean.lowercase() in notCompanyWords) break
            val looksLikeName = clean.first().isUpperCase() || clean == "&" || clean.all { it.isDigit() }
            if (!looksLikeName) break
            picked.add(0, clean)
            if (picked.size >= 6) break
        }
        return picked.joinToString(" ").takeIf { it.length >= 2 }
    }

    private fun findAmount(text: String): Long? {
        amountBeforeVerb.find(text)?.let { return toPaise(it.groupValues[1]) }
        amountAfterVerb.find(text)?.let { m -> toPaise(m.groupValues[1])?.let { return it } }
        for (m in amountWithCurrency.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 25), m.range.first)
            if (balanceWords.containsMatchIn(before)) continue
            val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
            toPaise(raw)?.let { return it }
        }
        return null
    }

    private fun toPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "")
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun firstName(pattern: Regex, text: String): String? {
        for (m in pattern.findAll(text)) {
            val candidate = clean(m.groupValues[1]) ?: continue
            if (rejectName.matches(candidate)) continue
            return candidate
        }
        return null
    }

    private fun upiPathName(text: String): String? {
        val path = upiPath.find(text)?.groupValues?.get(1) ?: return null
        val tokens = path.split('/', '-', '|')
        for (token in tokens) {
            val t = token.trim().trimEnd('.', ',', ';', ':')
            if (t.length < 3) continue
            if (t.all { it.isDigit() }) continue
            if (t.lowercase() in pathSkip) continue
            if (rejectName.matches(t)) continue
            return t
        }
        return null
    }

    private fun clean(raw: String): String? {
        val s = raw.trim().trimEnd('.', ',', ';', ':', '-', ' ').replace(whitespace, " ")
        return s.takeIf { it.length >= 2 }
    }

    /** "SWIGGY" -> "Swiggy". Leaves VPAs and mixed-case names alone. */
    private fun prettify(raw: String): String {
        val s = raw.trim().replace(whitespace, " ")
        if (s.contains('@')) return s.lowercase()
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return s
        return s.split(' ').joinToString(" ") { word ->
            if (word.length <= 3 && word.all { it.isLetter() }) word
            else word.lowercase().replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        }
    }

    private fun findRef(text: String): String? {
        refRe.find(text)?.groupValues?.get(1)?.let { ref ->
            if (!looksLikeDate(ref)) return ref
        }
        upiRefInPath.find(text)?.let { return it.groupValues[1] }
        twelveDigits.find(text)?.let { return it.groupValues[1] }
        return null
    }

    private fun looksLikeDate(s: String): Boolean = s.length == 8 && s.all { it.isDigit() } &&
        s.substring(0, 2).toInt() in 1..31 && s.substring(2, 4).toInt() in 1..12

    private fun bankHint(sender: String?, text: String): String? {
        fun scan(s: String): String? = when {
            "indusind" in s || "indus" in s -> "indusind"
            "idfc" in s -> "idfc"
            "sbi" in s || "state bank" in s -> "sbi"
            "hdfc" in s -> "hdfc"
            "icici" in s -> "icici"
            "kotak" in s -> "kotak"
            "axis" in s -> "axis"
            "yes bank" in s || "yesbank" in s -> "yes"
            "pnb" in s || "punjab national" in s -> "pnb"
            "bob" in s || "bank of baroda" in s -> "bob"
            "canara" in s -> "canara"
            "federal" in s -> "federal"
            "au bank" in s || "aubank" in s -> "au"
            "paytm" in s -> "paytm"
            else -> null
        }
        sender?.lowercase()?.let { s -> scan(s)?.let { return it } }
        val tail = text.takeLast(40).lowercase()
        scan(tail)?.let { return it }
        return scan(text.lowercase().replace(vpaRe, " "))
    }

    fun bankLabel(hint: String?): String? = when (hint) {
        "indusind" -> "IndusInd"
        "idfc" -> "IDFC First"
        "sbi" -> "SBI"
        "hdfc" -> "HDFC"
        "icici" -> "ICICI"
        "kotak" -> "Kotak"
        "axis" -> "Axis"
        "yes" -> "Yes Bank"
        "pnb" -> "PNB"
        "bob" -> "Bank of Baroda"
        "canara" -> "Canara"
        "federal" -> "Federal"
        "au" -> "AU Bank"
        "paytm" -> "Paytm"
        else -> null
    }

    private fun findDueDate(text: String, receivedAt: Long): Long? {
        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
        var led: LocalDate? = null
        var lastFuture: LocalDate? = null
        for (m in dateRe.findAll(text)) {
            val date = toDate(m.groupValues[1], m.groupValues[2], m.groupValues[3], today) ?: continue
            val before = text.substring(maxOf(0, m.range.first - 30), m.range.first)
            if (led == null && dateLead.containsMatchIn(before) && !date.isBefore(today)) led = date
            if (!date.isBefore(today)) lastFuture = date
        }
        val chosen = led ?: lastFuture ?: return null
        return chosen.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun toDate(day: String, month: String, year: String, today: LocalDate): LocalDate? {
        val d = day.toIntOrNull() ?: return null
        val monthNumber = month.toIntOrNull()
        val m = monthNumber ?: monthFromName(month) ?: return null
        val y: Int
        if (year.isEmpty()) {
            // "3rd Oct" is fine; "28/09" without a year is too easy to confuse with an amount.
            if (monthNumber != null) return null
            y = today.year
        } else {
            var parsed = year.toIntOrNull() ?: return null
            if (parsed < 100) parsed += 2000
            y = parsed
        }
        if (d !in 1..31 || m !in 1..12 || y !in (today.year - 1)..(today.year + 5)) return null
        var date = try {
            LocalDate.of(y, m, d)
        } catch (e: Exception) {
            return null
        }
        if (year.isEmpty() && date.isBefore(today.minusDays(45))) date = date.plusYears(1)
        return date
    }

    private fun monthFromName(name: String): Int? {
        val n = name.lowercase().take(3)
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val i = months.indexOf(n)
        return if (i >= 0) i + 1 else null
    }
}
