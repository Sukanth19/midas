package dev.suks.midas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.withTransaction
import dev.suks.midas.WorkspaceManager
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** Everything in Midas, as one JSON file. Payments still waiting for review aren't included. */
@Serializable
data class BackupFile(
    val format: Int = FORMAT,
    val createdAt: Long,
    val appVersion: String,
    val activeWorkspaceId: String,
    val myName: String = "Me",
    val workspaces: List<WorkspaceData>,
) {
    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class WorkspaceData(
    val workspace: Workspace,
    val currency: String,
    val monthlyBudgetMinor: Long = 0,
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val buyItems: List<BuyItem> = emptyList(),
    val groceryItems: List<GroceryItem> = emptyList(),
    val groceryWeeks: List<GroceryWeek> = emptyList(),
    val groceryPrices: List<GroceryPrice> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val people: List<Person> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
)

@Dao
interface BackupDao {
    @Query("SELECT * FROM categories") suspend fun categories(): List<Category>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<Transaction>
    @Query("SELECT * FROM buy_items") suspend fun buyItems(): List<BuyItem>
    @Query("SELECT * FROM grocery_items") suspend fun groceryItems(): List<GroceryItem>
    @Query("SELECT * FROM grocery_weeks") suspend fun groceryWeeks(): List<GroceryWeek>
    @Query("SELECT * FROM grocery_prices") suspend fun groceryPrices(): List<GroceryPrice>
    @Query("SELECT * FROM bills") suspend fun bills(): List<Bill>
    @Query("SELECT * FROM people") suspend fun people(): List<Person>
    @Query("SELECT * FROM settlements") suspend fun settlements(): List<Settlement>

    // Transactions point at categories, so they go first.
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM buy_items") suspend fun clearBuyItems()
    @Query("DELETE FROM grocery_items") suspend fun clearGroceryItems()
    @Query("DELETE FROM grocery_weeks") suspend fun clearGroceryWeeks()
    @Query("DELETE FROM grocery_prices") suspend fun clearGroceryPrices()
    @Query("DELETE FROM bills") suspend fun clearBills()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM settlements") suspend fun clearSettlements()

    @Insert suspend fun insertCategories(v: List<Category>)
    @Insert suspend fun insertTransactions(v: List<Transaction>)
    @Insert suspend fun insertBuyItems(v: List<BuyItem>)
    @Insert suspend fun insertGroceryItems(v: List<GroceryItem>)
    @Insert suspend fun insertGroceryWeeks(v: List<GroceryWeek>)
    @Insert suspend fun insertGroceryPrices(v: List<GroceryPrice>)
    @Insert suspend fun insertBills(v: List<Bill>)
    @Insert suspend fun insertPeople(v: List<Person>)
    @Insert suspend fun insertSettlements(v: List<Settlement>)
}

/** Writes every workspace to one JSON backup and restores from one, replacing what's there. */
class BackupManager(private val workspaces: WorkspaceManager, private val appVersion: String) {

    suspend fun export(now: Long = System.currentTimeMillis()): String {
        val all = workspaces.all().map { (ws, c) ->
            val d = c.database.backup()
            val s = c.settings.current()
            WorkspaceData(
                workspace = ws, currency = s.currency, monthlyBudgetMinor = s.monthlyBudgetMinor,
                categories = d.categories(), transactions = d.transactions(), buyItems = d.buyItems(),
                groceryItems = d.groceryItems(), groceryWeeks = d.groceryWeeks(), groceryPrices = d.groceryPrices(),
                bills = d.bills(), people = d.people(), settlements = d.settlements(),
            )
        }
        val backup = BackupFile(
            createdAt = now, appVersion = appVersion, activeWorkspaceId = workspaces.registry.activeId.value,
            myName = workspaces.current.settings.current().myName, workspaces = all,
        )
        return json.encodeToString(BackupFile.serializer(), backup)
    }

    /** Reads a backup without changing anything. Throws [IllegalArgumentException] if it isn't one. */
    fun read(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("That isn't a Midas backup", e)
        }
        require(backup.format <= BackupFile.FORMAT) { "This backup was made by a newer version of Midas" }
        require(backup.workspaces.isNotEmpty()) { "The backup is empty" }
        return backup
    }

    /** Replaces every workspace with the ones in [backup]. Workspaces not in it are deleted. */
    suspend fun restore(backup: BackupFile) {
        val keep = backup.workspaces.map { it.workspace }
        workspaces.registry.workspaces.value.filter { ws -> keep.none { it.id == ws.id } }.forEach { workspaces.delete(it.id) }
        workspaces.registry.replaceAll(keep, backup.activeWorkspaceId)
        for (data in backup.workspaces) {
            val c = workspaces.containerFor(data.workspace.id)
            val d = c.database.backup()
            c.database.withTransaction {
                d.clearTransactions(); d.clearSettlements(); d.clearPeople(); d.clearCategories(); d.clearBuyItems()
                d.clearGroceryItems(); d.clearGroceryWeeks(); d.clearGroceryPrices(); d.clearBills()
                d.insertCategories(data.categories); d.insertTransactions(data.transactions); d.insertBuyItems(data.buyItems)
                d.insertGroceryItems(data.groceryItems); d.insertGroceryWeeks(data.groceryWeeks); d.insertGroceryPrices(data.groceryPrices)
                d.insertBills(data.bills); d.insertPeople(data.people); d.insertSettlements(data.settlements)
            }
            c.settings.setCurrency(data.currency)
            c.settings.setMonthlyBudget(data.monthlyBudgetMinor)
        }
        workspaces.current.settings.setMyName(backup.myName)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** "midas-backup-2026-11-17.json" */
        fun fileName(date: LocalDate) = "midas-backup-$date.json"

        private val autoName = Regex("""^midas-backup-\d{4}-\d{2}-\d{2}\.json$""")

        /** Automatic backups older than the newest [keep] that should be deleted. Other files are never touched. */
        fun toPrune(names: List<String>, keep: Int = 7): List<String> =
            names.filter { autoName.matches(it) }.sortedDescending().drop(keep)
    }
}
