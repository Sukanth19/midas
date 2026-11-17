package dev.suks.midas.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.suks.midas.MidasApplication
import dev.suks.midas.data.Workspace
import kotlinx.coroutines.launch

/** Reads incoming SMS from banks (sender ids like "VM-HDFCBK") when SMS capture is on. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Long messages arrive in parts; join them per sender.
        val bySender = messages.groupBy { it.originatingAddress.orEmpty() }
            .mapValues { (_, parts) -> parts.joinToString("") { it.messageBody.orEmpty() } }
            .filterKeys(::isBusinessSender)
        if (bySender.isEmpty()) return
        val app = context.applicationContext as MidasApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                val settings = app.workspaces.containerFor(Workspace.DEFAULT_ID).settings.current()
                if (settings.captureSms) bySender.values.forEach { PaymentCapture.handle(app, it, "SMS") }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Banks send from letter ids ("AX-SBIUPI"); a phone number could be anyone, so it's ignored. */
        fun isBusinessSender(address: String): Boolean = address.any { it.isLetter() }
    }
}
