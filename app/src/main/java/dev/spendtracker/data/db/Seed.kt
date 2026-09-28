package dev.spendtracker.data.db

/** First-launch defaults. Runs once; the user can rename or archive anything later. */
object Seed {

    private data class Def(
        val name: String,
        val icon: String,
        val prompt: CategoryPrompt = CategoryPrompt.NONE,
        val inBudget: Boolean = true,
    )

    private val expenseCategories = listOf(
        Def("Food", "food"),
        Def("Groceries", "grocery"),
        Def("Transport", "transport"),
        Def("Shopping", "shopping"),
        Def("Bills & Recharge", "bills"),
        Def("Rent & Home", "home"),
        Def("Health", "health"),
        Def("Entertainment", "fun"),
        Def("Friends & Family", "people", CategoryPrompt.PERSON),
        Def("Travel", "travel"),
        Def("Other", "other", CategoryPrompt.DESCRIPTION),
    )

    private val incomeCategories = listOf(
        Def("Salary", "salary"),
        Def("Refund", "refund"),
        Def("From People", "person", CategoryPrompt.PERSON),
        Def("Other Income", "other_in", CategoryPrompt.DESCRIPTION),
    )

    private val accounts = listOf(
        Account(name = "SBI", type = AccountType.BANK, sortOrder = 0),
        Account(name = "IDFC First", type = AccountType.BANK, sortOrder = 1),
        Account(name = "IndusInd", type = AccountType.BANK, sortOrder = 2),
        Account(name = "Cash", type = AccountType.CASH, sortOrder = 3),
    )

    suspend fun run(db: AppDatabase) {
        if (db.categoryDao().count() == 0) {
            val list = expenseCategories.mapIndexed { i, d ->
                Category(name = d.name, kind = TxType.EXPENSE, icon = d.icon, sortOrder = i, prompt = d.prompt, countsInBudget = d.inBudget)
            } + incomeCategories.mapIndexed { i, d ->
                Category(name = d.name, kind = TxType.INCOME, icon = d.icon, sortOrder = i, prompt = d.prompt, countsInBudget = d.inBudget)
            }
            db.categoryDao().insertAll(list)
        }
        if (db.accountDao().count() == 0) {
            db.accountDao().insertAll(accounts)
        }
    }
}
