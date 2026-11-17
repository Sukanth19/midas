package dev.suks.midas.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import dev.suks.midas.MidasApplication
import kotlinx.coroutines.launch

/**
 * Reads payment alerts from UPI and banking apps ("You paid ₹200 to Rahul") once the user grants
 * notification access. Notifications from every other app are ignored without being read.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = PAYMENT_APPS[sbn.packageName] ?: return
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return
        val text = textOf(n) ?: return
        val app = applicationContext as MidasApplication
        app.appScope.launch { PaymentCapture.handle(app, text, source, sbn.postTime) }
    }

    companion object {
        /** Package → name shown when reviewing. */
        val PAYMENT_APPS = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM",
            "com.dreamplug.androidapp" to "CRED",
            "in.amazon.mShop.android.shopping" to "Amazon Pay",
            "com.mobikwik_new" to "MobiKwik",
            "money.super.payments" to "super.money",
            "com.naviapp" to "Navi",
            "com.snapwork.hdfc" to "HDFC Bank",
            "com.sbi.lotusintouch" to "YONO SBI",
            "com.csam.icici.bank.imobile" to "ICICI iMobile",
            "com.axis.mobile" to "Axis Mobile",
            "com.msf.kbank.mobile" to "Kotak",
        )

        fun textOf(n: Notification): String? {
            val extras = n.extras ?: return null
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            return "$title. $body".trim('.', ' ').ifBlank { null }
        }

        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, PaymentNotificationListener::class.java)
    }
}
