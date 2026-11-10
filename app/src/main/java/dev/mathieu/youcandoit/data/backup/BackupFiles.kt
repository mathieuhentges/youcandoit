package dev.mathieu.youcandoit.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes the file the user picked in the system file picker (Drive, Downloads, …). */
interface BackupFiles {
    suspend fun write(uri: Uri, text: String)
    suspend fun read(uri: Uri): String
}

class ContentResolverBackupFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupFiles {
    override suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt": an existing file picked again is replaced, not partly overwritten.
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't open $uri")
        stream.bufferedWriter().use { it.write(text) }
    }

    override suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
        stream.bufferedReader().use { it.readText() }
    }
}
