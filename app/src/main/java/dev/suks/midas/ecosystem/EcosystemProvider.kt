package dev.suks.midas.ecosystem

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import dev.suks.midas.MidasApplication
import dev.suks.midas.data.BuyItem
import dev.suks.midas.domain.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * Answers pupdr — see [Ecosystem]. It works on whichever workspace is open, like widgets do.
 * Only [ContentProvider.call] is supported.
 */
class EcosystemProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!Ecosystem.isTrusted(ctx, callingPackage)) throw SecurityException("not a pupdr ecosystem app")
        val container = (ctx.applicationContext as MidasApplication).container
        val x = extras ?: Bundle()
        return runBlocking {
            when (method) {
                Ecosystem.PING -> Bundle().apply { putInt(Ecosystem.RESULT, Ecosystem.VERSION) }
                Ecosystem.BUY_ADD -> {
                    val name = x.getString(Ecosystem.NAME)?.trim()?.take(80)?.takeIf { it.isNotEmpty() } ?: return@runBlocking null
                    val id = container.buyList.save(
                        BuyItem(
                            name = name,
                            estimatedPriceMinor = 0,
                            categoryId = FitnessSpend.categoryFor(container.categories.getAll()),
                            note = x.getString(Ecosystem.NOTE).orEmpty().take(200),
                        )
                    )
                    Bundle().apply { putLong(Ecosystem.RESULT, id) }
                }
                Ecosystem.FITNESS_SPEND -> {
                    val from = LocalDate.ofEpochDay(x.getLong(Ecosystem.FROM))
                    val to = LocalDate.ofEpochDay(x.getLong(Ecosystem.TO))
                    if (to.isBefore(from)) return@runBlocking null
                    val currency = container.settings.settings.first().currency
                    val total = FitnessSpend.total(container.database.transactions().getBetween(from, to), container.categories.getAll())
                    Bundle().apply {
                        putLong(Ecosystem.AMOUNT_MINOR, total)
                        putString(Ecosystem.CURRENCY, currency)
                        putInt(Ecosystem.FRACTION_DIGITS, Money.fractionDigits(currency))
                    }
                }
                else -> null
            }
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
