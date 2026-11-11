package dev.suks.midas

import android.content.Context
import dev.suks.midas.data.BillRepository
import dev.suks.midas.data.BuyListRepository
import dev.suks.midas.data.CategoryRepository
import dev.suks.midas.data.ChangeListener
import dev.suks.midas.data.GroceryRepository
import dev.suks.midas.data.MidasDatabase
import dev.suks.midas.data.SettingsRepository
import dev.suks.midas.data.TransactionRepository
import java.time.Clock
import java.time.LocalDate

/**
 * Hand-rolled dependency container. One database, one set of repositories, shared by the
 * app UI, the widgets and the reminder worker.
 */
class AppContainer(
    context: Context,
    val database: MidasDatabase = MidasDatabase.build(context),
    val clock: Clock = Clock.systemDefaultZone(),
    onChanged: ChangeListener = {},
) {
    val categories = CategoryRepository(database, onChanged)
    val transactions = TransactionRepository(database, onChanged)
    val buyList = BuyListRepository(database, onChanged)
    val grocery = GroceryRepository(database, onChanged)
    val bills = BillRepository(database, onChanged)
    val settings = SettingsRepository(context, onChanged)

    fun today(): LocalDate = LocalDate.now(clock)
}
