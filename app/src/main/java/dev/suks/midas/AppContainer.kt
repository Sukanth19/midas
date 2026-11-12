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
import dev.suks.midas.data.Workspace
import java.time.Clock
import java.time.LocalDate

/**
 * Hand-rolled dependency container for one workspace: its database and repositories, shared by
 * the app UI, the widgets and the reminder worker. See [WorkspaceManager] for how they're switched.
 */
class AppContainer(
    context: Context,
    val database: MidasDatabase = MidasDatabase.build(context),
    val clock: Clock = Clock.systemDefaultZone(),
    onChanged: ChangeListener = {},
    val workspaceId: String = Workspace.DEFAULT_ID,
    val settings: SettingsRepository = SettingsRepository.forWorkspace(context, workspaceId, onChanged),
) {
    val categories = CategoryRepository(database, onChanged)
    val transactions = TransactionRepository(database, onChanged)
    val buyList = BuyListRepository(database, onChanged)
    val grocery = GroceryRepository(database, onChanged)
    val bills = BillRepository(database, onChanged)

    fun today(): LocalDate = LocalDate.now(clock)

    fun close() = database.close()
}
