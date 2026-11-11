package dev.suks.midas.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.suks.midas.data.Category
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.TxSource
import dev.suks.midas.ui.components.CategoryBadge
import dev.suks.midas.ui.components.MoneyText

@Composable
fun TransactionRow(tx: Transaction, category: Category?, currency: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category?.icon, category?.color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                add(category?.name ?: "Uncategorised")
                when (tx.source) {
                    TxSource.BILL -> add("Bill")
                    TxSource.BUY_LIST -> add("Buy list")
                    TxSource.GROCERY -> add("Groceries")
                    TxSource.MANUAL -> {}
                }
                subtitle?.let(::add)
            }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(tx.amountMinor, currency, type = tx.type, style = MaterialTheme.typography.titleSmall)
    }
}
