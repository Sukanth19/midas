package dev.suks.midas.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.suks.midas.MidasApplication
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Writes a backup once a day into a folder the user picked (which a sync app like Drive or
 * Syncthing can copy elsewhere), keeping the newest [BackupManager.toPrune] week of files.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MidasApplication
        val settings = app.workspaces.current.settings
        val folder = settings.current().backupFolder ?: return Result.success()
        return try {
            BackupFolder.write(applicationContext, folder.toUri(), BackupManager.fileName(LocalDate.now()), app.backups.export())
            settings.setLastAutoBackup(System.currentTimeMillis())
            Result.success()
        } catch (_: Exception) {
            // Most likely the folder was deleted or access was taken away; try again tomorrow.
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "auto-backup"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build())
            // And one straight away, so there's a backup without waiting a day.
            wm.enqueueUniqueWork("$WORK_NAME-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BackupWorker>().build())
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

/** Reads and writes files in a folder picked with the system folder picker. */
object BackupFolder {

    fun write(context: Context, tree: Uri, name: String, text: String) {
        val resolver = context.contentResolver
        val children = list(context, tree)
        // Replace today's file rather than piling up "name (1).json".
        children[name]?.let { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it)) }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val file = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: error("couldn't create $name")
        resolver.openOutputStream(file, "wt")?.use { it.write(text.toByteArray()) } ?: error("couldn't write $name")
        BackupManager.toPrune(children.keys.toList() + name).forEach { old ->
            children[old]?.let { runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it)) } }
        }
    }

    /** File name → document id for everything directly in the folder. */
    private fun list(context: Context, tree: Uri): Map<String, String> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val out = mutableMapOf<String, String>()
        context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) out[c.getString(1)] = c.getString(0)
        }
        return out
    }

    /** "Documents/Midas" for a folder picked on the phone's storage. */
    fun label(tree: Uri): String = runCatching {
        Uri.decode(DocumentsContract.getTreeDocumentId(tree)).substringAfter(':').ifEmpty { "Phone storage" }
    }.getOrDefault("Chosen folder")
}
