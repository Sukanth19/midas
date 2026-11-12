package dev.suks.midas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollAction
import dev.suks.midas.data.Bill
import dev.suks.midas.data.Category
import dev.suks.midas.data.GroceryItem
import dev.suks.midas.data.Settings
import dev.suks.midas.data.Transaction
import dev.suks.midas.data.Workspace
import dev.suks.midas.ui.workspaces.WorkspaceSheetContent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextClearance
import dev.suks.midas.domain.GroceryMath
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.bills.BillsContent
import dev.suks.midas.ui.bills.BillsViewModel
import dev.suks.midas.ui.home.HomeContent
import dev.suks.midas.ui.home.HomeState
import dev.suks.midas.ui.home.HomeViewModel
import dev.suks.midas.ui.lists.GroceryContent
import dev.suks.midas.ui.lists.GroceryState
import dev.suks.midas.ui.transactions.EditTransactionContent
import dev.suks.midas.ui.transactions.EditTxState
import dev.suks.midas.ui.theme.MidasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ScreensTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 11)
    private val food = Category(1, "Food & Dining", "restaurant", 0xFFEF6C00)
    private val groceries = Category(2, "Groceries", "cart", 0xFF2E7D32)
    private val salary = Category(3, "Salary", "work", 0xFF2E7D32, type = TxType.INCOME)

    @Test fun `home shows spending, budget and recent activity`() {
        val txs = listOf(Transaction(1, 125_050, TxType.EXPENSE, food.id, "Dinner", "", today))
        val state = HomeViewModel.build(today, Settings(monthlyBudgetMinor = 1_000_000), txs, listOf(food), emptyList(), emptyList())
        var added = false
        compose.setContent { MidasTheme { HomeContent(state, onAdd = { added = true }) } }
        compose.onNodeWithTag("hero_spent").assertIsDisplayed()
        compose.onNodeWithText("₹1,250.50").assertIsDisplayed()
        compose.onNodeWithText("₹8,749.50 left of ₹10,000").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Dinner"))
        compose.onNodeWithText("Dinner").assertIsDisplayed()
        compose.onNodeWithTag("add_fab").performClick()
        assertTrue(added)
    }

    @Test fun `home empty state invites the first expense`() {
        compose.setContent { MidasTheme { HomeContent(HomeState(loaded = true, today = today)) } }
        compose.onNodeWithText("Set a monthly budget").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Nothing tracked yet"))
        compose.onNodeWithText("Nothing tracked yet").assertIsDisplayed()
    }

    @Test fun `add transaction validates then saves`() {
        var state by mutableStateOf(
            EditTxState(loading = false, today = today, date = today, categories = listOf(food, groceries, salary)),
        )
        var saves = 0
        compose.setContent {
            MidasTheme {
                EditTransactionContent(
                    state,
                    onAmount = { state = state.copy(amountText = it) },
                    onCategory = { state = state.copy(categoryId = it) },
                    onTitle = { state = state.copy(title = it) },
                    onSave = { if (state.canSave) saves++ else state = state.copy(showErrors = true) },
                )
            }
        }
        // Only expense categories are offered for an expense.
        compose.onNodeWithText("Salary").assertDoesNotExist()
        compose.onNodeWithTag("save").performClick()
        compose.onNodeWithText("Enter an amount").assertIsDisplayed()
        compose.onNodeWithText("Pick a category").assertIsDisplayed()

        compose.onNodeWithTag("amount").performTextInput("249.5")
        compose.onNodeWithText("Groceries").performScrollTo().performClick()
        compose.onNodeWithTag("title").performScrollTo().performTextInput("Veggies")
        compose.onNodeWithTag("save").performClick()
        assertEquals(24_950L, state.amountMinor)
        assertEquals(groceries.id, state.categoryId)
        assertEquals(1, saves)
    }

    @Test fun `amount field ignores invalid input`() {
        var state by mutableStateOf(EditTxState(loading = false, today = today, date = today, categories = listOf(food)))
        compose.setContent { MidasTheme { EditTransactionContent(state, onAmount = { state = state.copy(amountText = it) }) } }
        compose.onNodeWithTag("amount").performTextInput("12.345")
        // Three decimals isn't a valid rupee amount, so nothing is accepted.
        assertEquals("", state.amountText)
    }

    @Test fun `grocery list shows estimate and toggles items`() {
        val items = listOf(
            GroceryItem(1, "Milk", 2.0, "l", 6_000, "Dairy & Eggs"),
            GroceryItem(2, "Rice", 5.0, "kg", 7_000, "Pantry", checked = true),
        )
        val state = GroceryState(
            today = today, sections = items.groupBy { it.section }.toList(),
            totals = GroceryMath.totals(items.map { it.toLine() }), loaded = true,
        )
        val toggled = mutableListOf<Pair<Long, Boolean>>()
        val adjusted = mutableListOf<Pair<Long, Int>>()
        compose.setContent { MidasTheme { GroceryContent(state, onToggle = { id, c -> toggled += id to c }, onAdjust = { id, d -> adjusted += id to d }) } }
        compose.onNodeWithTag("grocery_estimate").assertIsDisplayed()
        compose.onNodeWithText("₹470").assertIsDisplayed()
        compose.onNodeWithText("1/2 in cart · ₹350").assertIsDisplayed()
        compose.onNodeWithTag("check_Milk").performClick()
        assertEquals(listOf(1L to true), toggled)
        compose.onNodeWithText("Finish week").assertIsDisplayed()
    }

    @Test fun `bills screen groups by urgency`() {
        val bills = listOf(
            Bill(1, "Phone", 59_900, nextDue = today.minusDays(2)),
            Bill(2, "Rent", 1_800_000, nextDue = today.plusDays(3), icon = "home"),
        )
        val state = BillsViewModel.build(bills, listOf(food), "INR", today)
        var paid: Long? = null
        compose.setContent { MidasTheme { BillsContent(state, onPay = { id, _ -> paid = id }) } }
        compose.onNodeWithText("Overdue").assertIsDisplayed()
        compose.onNodeWithText("Due in the next 7 days").assertIsDisplayed()
        compose.onNodeWithText("Rent").assertIsDisplayed()
        compose.onAllNodesWithTextFirst("Pay").performClick()
        compose.onNodeWithText("Pay Phone").assertExists()
        compose.onNodeWithText("Mark paid").performClick()
        assertEquals(1L, paid)
    }

    private val personal = Workspace(Workspace.DEFAULT_ID, "Personal", 0xFFC9A227)
    private val roommate = Workspace("r1", "Roommate", 0xFF1E88E5)

    @Test fun `home shows the open workspace and opens the switcher`() {
        var opened = false
        compose.setContent { MidasTheme { HomeContent(HomeState(loaded = true, today = today), workspace = roommate, onWorkspaces = { opened = true }) } }
        compose.onNodeWithText("Roommate").assertIsDisplayed()
        compose.onNodeWithTag("workspace_chip").performClick()
        assertTrue(opened)
    }

    @Test fun `home hides the workspace chip when none is given`() {
        compose.setContent { MidasTheme { HomeContent(HomeState(loaded = true, today = today)) } }
        compose.onNodeWithTag("workspace_chip").assertDoesNotExist()
    }

    @Test fun `switcher lists workspaces and switches on tap`() {
        var selected: String? = null
        compose.setContent { MidasTheme { WorkspaceSheetContent(listOf(personal, roommate), activeId = personal.id, onSelect = { selected = it }) } }
        compose.onNodeWithText("Personal").assertIsDisplayed()
        compose.onNodeWithText("Open now").assertIsDisplayed()
        compose.onNodeWithTag("workspace_Roommate").performClick()
        assertEquals("r1", selected)
    }

    @Test fun `switcher creates a workspace with a cleaned name`() {
        var created: String? = null
        compose.setContent { MidasTheme { WorkspaceSheetContent(listOf(personal), activeId = personal.id, onCreate = { created = it }) } }
        compose.onNodeWithTag("new_workspace").performClick()
        compose.onNodeWithTag("workspace_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("workspace_name").performTextInput("  Goa trip ")
        compose.onNodeWithTag("workspace_confirm").performClick()
        assertEquals("Goa trip", created)
    }

    @Test fun `switcher renames a workspace`() {
        var renamed: Pair<String, String>? = null
        compose.setContent { MidasTheme { WorkspaceSheetContent(listOf(personal, roommate), activeId = personal.id, onRename = { id, n -> renamed = id to n }) } }
        compose.onNodeWithTag("edit_Roommate").performClick()
        compose.onNodeWithTag("workspace_name").performTextClearance()
        compose.onNodeWithTag("workspace_name").performTextInput("Aditya")
        compose.onNodeWithTag("workspace_confirm").performClick()
        assertEquals("r1" to "Aditya", renamed)
    }

    @Test fun `deleting asks for confirmation and the default workspace can't be deleted`() {
        val deleted = mutableListOf<String>()
        compose.setContent { MidasTheme { WorkspaceSheetContent(listOf(personal, roommate), activeId = roommate.id, onDelete = { deleted += it }) } }
        compose.onNodeWithTag("edit_Personal").performClick()
        compose.onNodeWithTag("workspace_delete").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithTag("edit_Roommate").performClick()
        compose.onNodeWithTag("workspace_delete").performClick()
        compose.onNodeWithText("Delete Roommate?").assertIsDisplayed()
        assertTrue(deleted.isEmpty())
        compose.onNodeWithTag("confirm_delete").performClick()
        assertEquals(listOf("r1"), deleted)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFirst(text: String) =
        onAllNodes(hasText(text, substring = false))[0]
}
