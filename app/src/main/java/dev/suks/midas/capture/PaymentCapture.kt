package dev.suks.midas.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.suks.midas.MainActivity
import dev.suks.midas.MidasApplication
import dev.suks.midas.R
import dev.suks.midas.data.Workspace
import dev.suks.midas.domain.Money
import dev.suks.midas.domain.ParsedPayment
import dev.suks.midas.domain.PaymentParser
import dev.suks.midas.domain.TxType
import kotlinx.coroutines.flow.first

/**
 * Turns bank SMS and payment-app notifications into payments waiting for review. They always go to
 * the Personal (default) workspace, since that's whose phone and bank account it is.
 */
object PaymentCapture {
    const val CHANNEL_ID = "captures"
    private const val NOTIFICATION_ID = 900_000_001

    /** Parses [text] and stores it if it's a payment. Returns true when a new payment was added. */
    suspend fun handle(app: MidasApplication, text: String, source: String, at: Long = System.currentTimeMillis()): Boolean {
        val container = app.workspaces.containerFor(Workspace.DEFAULT_ID)
        // Bank messages are in rupees; reading them into a dollar book would give nonsense.
        if (container.settings.current().currency != "INR") return false
        val parsed = PaymentParser.parse(text) ?: return false
        container.captures.capture(parsed, source, text, at) ?: return false
        notify(app, parsed, container.captures.pending.first().size)
        return true
    }

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Payments to review", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Payments Midas read from bank SMS or payment apps"
            },
        )
    }

    fun title(p: ParsedPayment): String {
        val amount = Money.format(p.amountMinor, "INR")
        return when {
            p.merchant == null -> if (p.type == TxType.INCOME) "$amount received" else "$amount paid"
            p.type == TxType.INCOME -> "$amount from ${p.merchant}"
            else -> "$amount to ${p.merchant}"
        }
    }

    private fun notify(context: Context, p: ParsedPayment, pendingCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_HOME)
                .putExtra(MainActivity.EXTRA_WORKSPACE_ID, Workspace.DEFAULT_ID)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val more = pendingCount - 1
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_midas)
            .setContentTitle(title(p))
            .setContentText(if (more > 0) "Tap to review · $more more waiting" else "Tap to review and add it")
            .setNumber(pendingCount)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }
}
