package dev.suks.midas.ui.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(initialTab: Int = 0) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Lists") })
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Buy list") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Weekly groceries") })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (tab == 0) BuyListTab() else GroceryTab()
        }
    }
}
