# SMS capture: how it works

Everything here runs on the phone. The parser is pure Kotlin
(`app/src/main/java/dev/spendtracker/sms/SmsParser.kt`) and is covered by unit tests with
sample messages (`app/src/test/.../SmsParserTest.kt`).

## Pipeline

```
incoming SMS
   │
   ├─ capture switched off?            → ignored
   ├─ sender or text on the block list → ignored
   │
   └─ SmsParser.parse(sender, body)
        ├─ OTP / offer / collect request / failed payment   → Ignored
        ├─ "mandate created / autopay set up / revoked"      → Mandate
        ├─ "EMI due on / bill due by / total amount due"     → Due
        └─ "debited / credited / spent"                      → Transaction
```

### Transaction
1. **Duplicate check** by the bank reference number (`Ref No`, `UPI Ref`, `RRN`, `UTR`, the
   number inside `UPI/P2M/…`). Without a reference, the same amount and direction within
   three minutes is treated as a duplicate.
2. **Account**: the last digits in the message (`A/c XX1234`, `Card ending 4321`) are
   matched against your accounts' *last 4 digits*. Failing that, the bank name in the
   sender id or signature (SBI, IDFC, IndusInd, HDFC, ICICI, Axis, Kotak, …) is matched
   against the account *name*. If neither matches, the account is left empty and the
   transaction is flagged for review.
3. **Payee**: the merchant or UPI id after `to`, `towards`, `at`, `trf to`, from the
   `UPI/…/Name` path, or the VPA (`name@bank`). Incoming money uses `from` / `by`.
4. **Category**: if you have filed this payee before with *Remember this*, its category is
   applied. Otherwise the transaction is flagged **to review** and a notification opens it.
5. **Autopay match**: a debit whose amount equals an active autopay due within five days is
   that autopay running. The transaction takes the autopay's category and the autopay moves
   to its next date.

### Mandate
Messages that *set up* a standing instruction ("UPI Mandate … successfully created for
TATA CAPITAL LIMITED IPO", "Autopay of Rs 649 for Netflix is set up") create an Autopay
entry: kind from the wording (IPO, subscription, EMI, insurance, bill), amount, the date
after *valid till / next debit on*, and the account. A revoke/release message ends the
matching entry.

### Due
Payment-due notices ("Your EMI of Rs 4,500 … will be debited on 05-Oct", "LazyPay bill of
Rs 746 is due on 3rd Oct", card statements with "Total amount due") create an Autopay entry
of kind EMI or Bill with the amount and last date. Repeated reminders for the same name
update that one entry rather than adding another; only a changed date notifies again.

## Message shapes the tests cover

| Bank / source | Shape |
|---|---|
| SBI (UPI) | `A/C X1234 debited by 486.0 on date 28Sep26 trf to SWIGGY Refno …` |
| SBI (credit) | `ur A/cX1234 credited by Rs500 on 28Sep26 by (Ref no …)` |
| SBI (transfer) | `Your A/C XXXXX1234 has a debit by transfer of Rs 1,500.00 … Avl Bal …` |
| IDFC First | `Your A/c XX5678 is debited for INR 486.00 … towards UPI/P2M/…/Swiggy Limited/…` |
| IndusInd | `Rs.486.00 debited from A/c XX9012 … to VPA swiggy.stores@axisbank Ref No. …` |
| IndusInd (IMPS) | `Your A/c X9012 is credited with INR 5,000.00 … from RAHUL SHARMA (IMPS Ref no …)` |
| Card | `INR 1,299.00 spent on … Card XX4321 at AMAZON on 28-Sep-26` |
| IPO mandate | `UPI Mandate of Rs 15,000.00 has been successfully created for … IPO … Valid till 03-Oct-26` |
| Subscription | `Autopay of Rs 649.00 for Netflix is set up … Next debit on 05-Oct-2026` |
| EMI notice | `Your EMI of Rs 4,500.00 for Loan A/c XX1234 will be debited on 05-Oct-2026` |
| Pay-later bill | `Your LazyPay bill of Rs 746 is due on 3rd Oct. Pay now …` |
| Card statement | `Total amount due INR 12,000.00, min amount due INR 600.00 by 15-Oct-26` |
| Ignored | OTPs, "has requested Rs …", failed/declined, offers |

## Adding your bank

1. Copy a real alert and mask the account number, names and reference.
2. Add it to `SmsParserTest.kt` with the values you expect (type, amount, payee, last four
   digits, bank hint, reference).
3. Run `./gradlew :app:testDebugUnitTest`. Adjust the patterns in `SmsParser.kt` until the
   test passes without breaking the others.

## Permissions used

| Permission | Why |
|---|---|
| `RECEIVE_SMS` | Read alerts as they arrive (only while the switch is on). |
| `READ_SMS` | The one-time *Import recent bank SMS* scan. |
| `POST_NOTIFICATIONS` | Review prompts and autopay reminders (Android 13+). |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the daily reminder alarm after a restart. |

There is no `INTERNET` permission.
