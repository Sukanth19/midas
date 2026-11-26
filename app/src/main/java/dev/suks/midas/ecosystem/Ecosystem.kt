package dev.suks.midas.ecosystem

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.security.MessageDigest

/**
 * The contract shared by pupdr, Midas and Velto (each app carries an identical copy).
 *
 * Every app exposes a ContentProvider at "<package>.ecosystem" that only answers [ContentProvider.call]
 * from the other two apps. Both directions are verified: a caller must be one of [TRUSTED] packages and be
 * signed with one of its pinned certificates, and before sending we check the target the same way, so a
 * look-alike app with the same package name can neither read nor receive anything.
 */
object Ecosystem {
    const val PUPDR = "dev.suks.pupdr"
    const val MIDAS = "dev.suks.midas"
    const val VELTO = "com.suks.velto"

    const val VERSION = 1

    // methods
    const val PING = "ping"                     // -> version
    const val HABIT_SET = "habit.set"           // key, day, done
    const val HABIT_GET = "habit.get"           // key, day -> done
    const val BUY_ADD = "buylist.add"           // name, note -> id
    const val FITNESS_SPEND = "spend.fitness"   // from, to (epoch days) -> amountMinor, currency, fractionDigits

    // extras
    const val KEY = "key"
    const val DAY = "day"
    const val DONE = "done"
    const val NAME = "name"
    const val NOTE = "note"
    const val FROM = "from"
    const val TO = "to"
    const val RESULT = "result"
    const val AMOUNT_MINOR = "amountMinor"
    const val CURRENCY = "currency"
    const val FRACTION_DIGITS = "fractionDigits"

    /** Habits that exist in both pupdr and Velto. */
    object Habits {
        const val CREATINE = "creatine"
        const val WATER = "water"
        const val WORKOUT = "workout"
        val ALL = listOf(CREATINE, WATER, WORKOUT)
        fun label(key: String) = when (key) { CREATINE -> "Creatine"; WATER -> "Water"; WORKOUT -> "Workout"; else -> key }
    }

    private const val DEBUG_KEY = "D23B52924FD22E8EB6D76F59630507CFAC4B885125F95C7EAD1E83321C99F26A"
    private val TRUSTED: Map<String, Set<String>> = mapOf(
        PUPDR to setOf(DEBUG_KEY, "8005A4046C05CBB58D1385AE65DB84B428B95E0843DFB6DCED76F7DD7334BD66"),
        MIDAS to setOf(DEBUG_KEY, "3E7F1500683854C541D25187589B051E4C1818B2A7466671D263F309C8D91EE6"),
        VELTO to setOf(DEBUG_KEY, "93BF5629D3A7EB21EF552FD4E94A2569307F2958BE273B8B097E7233A2CDB9D6"),
    )

    fun authority(pkg: String) = "$pkg.ecosystem"

    fun isInstalled(context: Context, pkg: String): Boolean = isTrusted(context, pkg)

    /** True when [pkg] is one of the three apps and is signed with a pinned certificate. */
    fun isTrusted(context: Context, pkg: String?): Boolean {
        if (pkg == null || pkg == context.packageName) return false
        val pins = TRUSTED[pkg] ?: return false
        return runCatching { signingDigests(context, pkg).any { it in pins } }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun signingDigests(context: Context, pkg: String): List<String> {
        val pm = context.packageManager
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return emptyList()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        } ?: return emptyList()
        val sha = MessageDigest.getInstance("SHA-256")
        return certs.map { sig -> sha.digest(sig.toByteArray()).joinToString("") { "%02X".format(it) } }
    }

    /** Calls [method] on another app's provider. Returns null if it isn't installed, isn't trusted or fails. */
    fun call(context: Context, pkg: String, method: String, extras: Bundle = Bundle()): Bundle? {
        if (!isTrusted(context, pkg)) return null
        return runCatching {
            context.contentResolver.call(Uri.parse("content://${authority(pkg)}"), method, null, extras)
        }.getOrNull()
    }
}
