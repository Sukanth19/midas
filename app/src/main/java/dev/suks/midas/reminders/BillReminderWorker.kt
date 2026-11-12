package dev.suks.midas.reminders

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.suks.midas.MainActivity
import dev.suks.midas.MidasApplication
import dev.suks.midas.R
import dev.suks.midas.data.Bill
import dev.suks.midas.data.Workspace
import dev.suks.midas.domain.Dates
import dev.suks.midas.domain.Money
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Daily check (around 9am) that posts a notification for bills that are due soon or overdue,
 * in every workspace, so a roommate's rent is never forgotten just because their workspace isn't open.
 */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = (applicationContext as MidasApplication).workspaces
        // Reminders on/off is an app-wide setting, so any workspace's settings will do.
        if (!manager.current.settings.current().billReminders) return Result.success()
        val all = manager.all()
        all.forEach { (workspace, container) ->
            val due = dueForReminder(container.bills.getActive(), container.today())
            if (due.isNotEmpty()) {
                notify(applicationContext, workspace, showWorkspace = all.size > 1, due, container.today(), container.settings.current().currency)
            }
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "bills"
        private const val WORK_NAME = "bill-reminders"

        /** Bills whose reminder window has started: due within remindDaysBefore days, or already overdue. */
        fun dueForReminder(bills: List<Bill>, today: LocalDate): List<Bill> =
            bills.filter { it.active && Dates.daysUntil(today, it.nextDue) <= it.remindDaysBefore }
                .sortedBy { it.nextDue }

        fun message(bill: Bill, today: LocalDate, currency: String): String {
            val days = Dates.daysUntil(today, bill.nextDue)
            val amount = Money.format(bill.amountMinor, currency)
            return when {
                days < 0 -> "$amount was due ${Dates.shortDate(bill.nextDue, today)}"
                days == 0L -> "$amount is due today"
                days == 1L -> "$amount is due tomorrow"
                else -> "$amount is due in $days days"
            }
        }

        /** Notification title; names the workspace when there's more than one, so you know whose bill it is. */
        fun title(bill: Bill, workspace: Workspace, showWorkspace: Boolean): String =
            if (showWorkspace) "${bill.name} · ${workspace.name}" else bill.name

        /** Bill ids repeat across workspaces, so other workspaces mix their id into the notification id. */
        fun notificationId(workspaceId: String, billId: Long): Int =
            if (workspaceId == Workspace.DEFAULT_ID) billId.toInt() else 31 * workspaceId.hashCode() + billId.hashCode()

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders before rent and bills are due"
                },
            )
        }

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(9, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, workspace: Workspace, showWorkspace: Boolean, bills: List<Bill>, today: LocalDate, currency: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val open = PendingIntent.getActivity(
                context, workspace.id.hashCode(),
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_BILLS)
                    .putExtra(MainActivity.EXTRA_WORKSPACE_ID, workspace.id)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val nm = NotificationManagerCompat.from(context)
            bills.forEach { b ->
                val n = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_midas)
                    .setContentTitle(title(b, workspace, showWorkspace))
                    .setContentText(message(b, today, currency))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setGroup("bills")
                    .build()
                nm.notify(notificationId(workspace.id, b.id), n)
            }
        }
    }
}
