package dev.spendtracker.sms

import dev.spendtracker.data.db.AutopayKind
import dev.spendtracker.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private fun txn(sender: String?, body: String): SmsParser.ParsedTxn {
        val r = SmsParser.parse(sender, body)
        assertTrue("expected a transaction but got $r for: $body", r is SmsParser.Result.Transaction)
        return (r as SmsParser.Result.Transaction).value
    }

    private fun mandate(sender: String?, body: String): SmsParser.ParsedMandate {
        val r = SmsParser.parse(sender, body)
        assertTrue("expected a mandate but got $r for: $body", r is SmsParser.Result.Mandate)
        return (r as SmsParser.Result.Mandate).value
    }

    @Test
    fun sbiUpiDebit() {
        val t = txn(
            "VM-SBIUPI",
            "Dear UPI user A/C X1234 debited by 486.0 on date 28Sep26 trf to SWIGGY Refno 527112345678. If not u? call 1800111109. -SBI"
        )
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(48600L, t.amountPaise)
        assertEquals("Swiggy", t.counterparty)
        assertEquals("1234", t.accountLast4)
        assertEquals("sbi", t.bankHint)
        assertEquals("527112345678", t.ref)
    }

    @Test
    fun sbiUpiCreditWithoutPayerName() {
        val t = txn(
            "AD-SBIUPI",
            "Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 28Sep26 by (Ref no 527112345699)"
        )
        assertEquals(TxType.INCOME, t.type)
        assertEquals(50000L, t.amountPaise)
        assertEquals(null, t.counterparty)
        assertEquals("1234", t.accountLast4)
        assertEquals("527112345699", t.ref)
    }

    @Test
    fun sbiTransferDebitWithBalance() {
        val t = txn(
            "VM-SBIINB",
            "Your A/C XXXXX1234 has a debit by transfer of Rs 1,500.00 on 28/09/26. Avl Bal Rs 12,345.00 -SBI"
        )
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(150000L, t.amountPaise)
        assertEquals("1234", t.accountLast4)
    }

    @Test
    fun idfcUpiDebitWithPath() {
        val t = txn(
            "AD-IDFCFB",
            "Your A/c XX5678 is debited for INR 486.00 on 28-SEP-2026 towards UPI/P2M/527112345678/Swiggy Limited/Payment. Avl Bal INR 12,345.00. Not you? Call 18001080 - IDFC FIRST Bank"
        )
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(48600L, t.amountPaise)
        assertEquals("Swiggy", t.counterparty)
        assertEquals("5678", t.accountLast4)
        assertEquals("idfc", t.bankHint)
        assertEquals("527112345678", t.ref)
    }

    @Test
    fun indusindVpaDebit() {
        val t = txn(
            "JD-INDUSB",
            "Rs.486.00 debited from A/c XX9012 on 28-09-2026 to VPA swiggy.stores@axisbank Ref No. 527112345678. Not you? Call 18602677777 -IndusInd Bank"
        )
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(48600L, t.amountPaise)
        assertEquals("swiggy.stores@axisbank", t.counterparty)
        assertEquals("9012", t.accountLast4)
        assertEquals("indusind", t.bankHint)
        assertEquals("527112345678", t.ref)
    }

    @Test
    fun indusindImpsCredit() {
        val t = txn(
            "JD-INDUSB",
            "Your A/c X9012 is credited with INR 5,000.00 on 28-SEP-26 from RAHUL SHARMA (IMPS Ref no 626712345678). -IndusInd Bank"
        )
        assertEquals(TxType.INCOME, t.type)
        assertEquals(500000L, t.amountPaise)
        assertEquals("Rahul Sharma", t.counterparty)
        assertEquals("626712345678", t.ref)
    }

    @Test
    fun cardSpend() {
        val t = txn(
            "VM-INDUSB",
            "INR 1,299.00 spent on IndusInd Bank Card XX4321 at AMAZON on 28-Sep-26. Avl limit INR 45,000.00. Not you? Call 18602677777"
        )
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(129900L, t.amountPaise)
        assertEquals("Amazon", t.counterparty)
        assertEquals("4321", t.accountLast4)
    }

    @Test
    fun otpIsIgnored() {
        val r = SmsParser.parse("VM-SBIINB", "123456 is your OTP for Rs 2,000.00 payment at Amazon. Do not share it with anyone. -SBI")
        assertEquals(SmsParser.Result.Ignored, r)
    }

    @Test
    fun collectRequestIsIgnored() {
        val r = SmsParser.parse("VM-SBIUPI", "Rahul has requested Rs 500.00 from you via UPI. Approve in your UPI app. -SBI")
        assertEquals(SmsParser.Result.Ignored, r)
    }

    private fun due(sender: String?, body: String): SmsParser.ParsedDue {
        val r = SmsParser.parse(sender, body)
        assertTrue("expected a due notice but got $r for: $body", r is SmsParser.Result.Due)
        return (r as SmsParser.Result.Due).value
    }

    @Test
    fun cardStatementBecomesBillWithTotalDue() {
        val d = due("VM-INDUSB", "Your IndusInd Card XX4321 statement is ready. Total amount due INR 12,000.00, min amount due INR 600.00 by 15-Oct-26.")
        assertEquals(AutopayKind.BILL, d.kind)
        assertEquals(1200000L, d.amountPaise)
        assertEquals("4321", d.accountLast4)
        assertTrue("name was ${d.name}", d.name.contains("card bill", ignoreCase = true))
        assertNotNull(d.dueAt)
    }

    @Test
    fun lazyPayDueNotice() {
        val d = due("VM-LAZYPY", "Your LazyPay bill of Rs 746 is due on 3rd Oct. Pay now to avoid late fees and keep your credit limit active.")
        assertEquals(AutopayKind.BILL, d.kind)
        assertEquals(74600L, d.amountPaise)
        assertEquals("LazyPay", d.name)
        assertNotNull(d.dueAt)
    }

    @Test
    fun emiReminderWithFutureDebit() {
        val d = due("VM-SBIINB", "Your EMI of Rs 4,500.00 for Loan A/c XX1234 will be debited on 05-Oct-2026. Please maintain sufficient balance. -SBI")
        assertEquals(AutopayKind.EMI, d.kind)
        assertEquals(450000L, d.amountPaise)
        assertEquals("SBI EMI", d.name)
        assertEquals("1234", d.accountLast4)
        assertNotNull(d.dueAt)
    }

    @Test
    fun emiDebitIsStillATransaction() {
        val t = txn("VM-SBIINB", "EMI of Rs 4,500.00 has been debited from A/c XX1234 on 05-10-26 towards Loan A/c 987654. Avl Bal Rs 20,000.00 -SBI")
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(450000L, t.amountPaise)
    }

    @Test
    fun ipoMandateCreated() {
        val m = mandate(
            "JD-INDUSB",
            "UPI Mandate of Rs 15,000.00 has been successfully created for TATA CAPITAL LIMITED IPO from A/c XX9012. Valid till 03-Oct-26. UMN 1234abcd@indus -IndusInd Bank"
        )
        assertEquals(AutopayKind.IPO, m.kind)
        assertEquals(1500000L, m.amountPaise)
        assertEquals("Tata Capital Limited IPO", m.name)
        assertEquals("9012", m.accountLast4)
        assertNotNull(m.dueAt)
        assertEquals(false, m.cancelled)
    }

    @Test
    fun subscriptionAutopaySetUp() {
        val m = mandate(
            "VM-SBIUPI",
            "Autopay of Rs 649.00 for Netflix is set up on your A/c X1234. Next debit on 05-Oct-2026. -SBI"
        )
        assertEquals(AutopayKind.SUBSCRIPTION, m.kind)
        assertEquals(64900L, m.amountPaise)
        assertEquals("Netflix", m.name)
        assertNotNull(m.dueAt)
    }

    @Test
    fun genericMandateTakesNameAfterFor() {
        val m = mandate(
            "VM-SBIUPI",
            "Autopay of Rs 1,199.00 for Gym Membership has been registered on your A/c XX1234. Next debit on 01-Nov-26. -SBI"
        )
        assertEquals(AutopayKind.OTHER, m.kind)
        assertEquals(119900L, m.amountPaise)
        assertEquals("Gym Membership", m.name)
        assertNotNull(m.dueAt)
    }

    @Test
    fun ipoMandateWithTitleCaseName() {
        val m = mandate(
            "AD-IDFCFB",
            "Mandate of INR 14,000.00 created for Swiggy Ltd IPO via UPI from A/c XX5678, valid upto 02/10/2026. IDFC FIRST Bank"
        )
        assertEquals(AutopayKind.IPO, m.kind)
        assertEquals("Swiggy Ltd IPO", m.name)
        assertEquals("5678", m.accountLast4)
        assertNotNull(m.dueAt)
    }

    @Test
    fun mandateRevoked() {
        val m = mandate(
            "JD-INDUSB",
            "UPI Mandate of Rs 15,000.00 for TATA CAPITAL LIMITED IPO has been revoked and the amount released. -IndusInd Bank"
        )
        assertEquals(AutopayKind.IPO, m.kind)
        assertEquals(true, m.cancelled)
    }

    @Test
    fun mandateExecutionIsATransaction() {
        val t = txn(
            "VM-SBIUPI",
            "Rs 649.00 debited from A/c X1234 on 05-10-26 towards Netflix Autopay Mandate UMN 99aa@sbi Ref 527199999999 -SBI"
        )
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(64900L, t.amountPaise)
        assertTrue("counterparty was ${t.counterparty}", t.counterparty?.startsWith("Netflix") == true)
        assertEquals("527199999999", t.ref)
    }
}
