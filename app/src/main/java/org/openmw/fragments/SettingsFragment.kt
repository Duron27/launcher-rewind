package org.openmw.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openmw.Constants
import org.openmw.GameFilesPreferences
import org.openmw.dataStore
import org.openmw.getAbsolutePathFromUri
import org.openmw.storeGameFilesUri
import org.openmw.utils.ModValue
import org.openmw.utils.writeModValuesToFile
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SettingsFragment : ComponentActivity() {

    private fun findFilesWithExtensions(
        directory: DocumentFile?,
        extensions: Array<String>
    ): List<DocumentFile> {
        return directory?.listFiles()?.filter { file ->
            extensions.any { file.name?.endsWith(".$it") == true }
        } ?: emptyList()
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    fun handleDocumentTreeSelection(context: Context, uri: Uri, onUriPersisted: (String?) -> Unit) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch {
                storeGameFilesUri(context, uri)
                val savedPath = getAbsolutePathFromUri(context, uri)
                // Use DocumentFile to access the selected directory
                val selectedDirectory = DocumentFile.fromTreeUri(context, uri)
                val dataStoreKey = stringPreferencesKey("game_files_uri")
                val iniFile = selectedDirectory?.findFile("Morrowind.ini")
                val dataFilesFolder = selectedDirectory?.findFile("Data Files")
                val extensions = arrayOf("esm", "bsa")
                val modDirectory = dataFilesFolder ?: selectedDirectory
                val files = findFilesWithExtensions(modDirectory, extensions)

                if (iniFile != null && dataFilesFolder != null && dataFilesFolder.isDirectory) {
                    // Update savedPath after setting it with the document tree
                    val uriString = context.dataStore.data.map { preferences ->
                        preferences[dataStoreKey]
                    }.first()
                    onUriPersisted(uriString?.let {
                        getAbsolutePathFromUri(
                            context,
                            Uri.parse(it)
                        )
                    })

                    // Prepare content lines
                    val orderedEsmFiles = listOf("Morrowind.esm", "Tribunal.esm", "Bloodmoon.esm")
                    val esmContentLines = orderedEsmFiles.filter { name ->
                        files.any { file -> file.name == name }
                    }.map { name ->
                        "content=$name"
                    }

                    // Prepare fallback-archive lines
                    val orderedBsaFiles = listOf("Morrowind.bsa", "Tribunal.bsa", "Bloodmoon.bsa")
                    val fallbackArchiveLines = orderedBsaFiles.filter { name ->
                        files.any { file -> file.name == name }
                    }.map { name ->
                        "fallback-archive=$name"
                    }

                    // Modify base_cfg file
                    val fileName = Constants.OPENMW_CFG
                    // Define the regex pattern to match any user-data value

                    val regexData = Regex("""^data\s*=\s*".*?"""")
                    val replacementStringData = """data="${savedPath}Data Files""""
                    val file = File(fileName)

                    // Read and replace lines in the file
                    val lines = file.readLines().map { line ->
                        var modifiedLine = line
                        if (line.contains(regexData)) {
                            modifiedLine = modifiedLine.replace(regexData, replacementStringData)
                        }
                        if (line.contains("resources=./resources")) {
                            modifiedLine = modifiedLine.replace("resources=./resources", "resources=${Constants.RESOURCES}")
                        }
                        // Add more replacements if needed
                        modifiedLine
                    }

                    // Write modified lines back to the file
                    file.bufferedWriter().use { writer ->
                        lines.forEach { line ->
                            writer.write(line)
                            writer.newLine()
                        }

                        // Append fallback-archive lines
                        fallbackArchiveLines.forEach { line ->
                            writer.write(line)
                            writer.newLine()
                        }
                    }

                    // Write .esm content lines to a separate file
                    val esmFile = File(Constants.USER_OPENMW_CFG)
                    esmFile.bufferedWriter().use { writer ->
                        esmContentLines.forEach { line ->
                            writer.write(line)
                            writer.newLine()
                        }
                    }

                    val iniData = iniFile.uri.let { uri ->
                        context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                    }
                    val converter = IniConverter(iniData ?: "")
                    val convertedData = converter.convert()

                    listOf("openmw.cfg", "openmw.base.cfg", "openmw.fallback.cfg").forEach { outputFileName ->
                        val outputFile = File(Constants.GLOBAL_CONFIG, outputFileName)
                        outputFile.appendText(convertedData + "\n")
                    }

                } else {
                    context.dataStore.edit { preferences ->
                        preferences[dataStoreKey] = ""
                    }
                    onUriPersisted("")
                    showToast(context, "Please select a folder with Morrowind.ini.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onUriPersisted(null)
            showToast(context, "An error occurred while selecting the folder.")
        }
    }

    private fun showToast(context: Context, message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}

class IniConverter(private val data: String) {
    fun convert(): String {
        return data
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(";") }
            .fold(Pair("", "")) { acc, line ->
                if (line.startsWith("[") && line.endsWith("]")) {
                    acc.copy(first = line.substring(1, line.length - 1).replace(" ", "_"))
                } else if (line.contains("=")) {
                    val converted = convertLine(line)
                    if (converted.isNotEmpty()) {
                        acc.copy(second = acc.second + "fallback=${acc.first}_$converted\n")
                    } else acc
                } else acc
            }.second
    }

    private fun convertLine(line: String): String {
        val (key, value) = line.split("=", limit = 2)
        if (key.isBlank() || value.isBlank()) return ""
        return "${key.replace(" ", "_")},$value"
    }
}

fun getGameFilesUri(context: Context): String? {
    val dataStoreKey = GameFilesPreferences.GAME_FILES_URI_KEY
    val dataStore = context.dataStore
    return runBlocking {
        val preferences = dataStore.data.first()
        preferences[dataStoreKey]
    }
}

fun containsMorrowindFolder(zipFilePath: String): Boolean {
    ZipInputStream(FileInputStream(zipFilePath)).use { zipIn ->
        var entry: ZipEntry? = zipIn.nextEntry
        while (entry != null) {
            if (entry.name.startsWith("Morrowind/")) {
                return true
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
    }
    return false
}
