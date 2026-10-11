package com.MAD.n152

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

data class StoredDocument(
    val id: String,
    val title: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val wordCount: Int,
    val lineCount: Int,
    val previewPath: String?,
    val reviewNotes: String = "",
    val highlightedPhrases: List<String> = emptyList(),
    val bookmarked: Boolean = false,
    val highlightColor: Int = 0xFFFFD978.toInt(),
    val folderId: String? = null,
    val sourceType: String = "unknown",
    val recognitionMode: String = "auto",
    val characterCount: Int = text.length
)

data class StoredFolder(
    val id: String,
    val name: String,
    val createdAt: Long
)

/** Stores OCR documents and previews entirely inside the app's private files directory. */
class DocumentRepository(context: Context) {
    private val documentsDirectory = File(context.filesDir, "documents").apply { mkdirs() }
    private val foldersFile = File(context.filesDir, "folders.json")

    fun listDocuments(): List<StoredDocument> =
        documentsDirectory
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull(::readDocument)
            .sortedByDescending(StoredDocument::updatedAt)

    fun listFolders(): List<StoredFolder> = runCatching {
        val array = JSONArray(foldersFile.readText())
        List(array.length()) { index ->
            val json = array.getJSONObject(index)
            StoredFolder(json.getString("id"), json.getString("name"), json.getLong("createdAt"))
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    fun createFolder(name: String): StoredFolder {
        val cleanName = name.trim().take(60)
        require(cleanName.isNotBlank()) { "Folder name cannot be empty" }
        val folders = listFolders().toMutableList()
        require(folders.none { it.name.equals(cleanName, ignoreCase = true) }) { "Folder already exists" }
        val folder = StoredFolder(UUID.randomUUID().toString(), cleanName, System.currentTimeMillis())
        folders += folder
        writeFolders(folders)
        return folder
    }

    fun saveDocument(
        id: String?,
        title: String,
        text: String,
        preview: Bitmap?,
        reviewNotes: String = "",
        highlightedPhrases: List<String> = emptyList(),
        bookmarked: Boolean = false,
        highlightColor: Int = 0xFFFFD978.toInt(),
        folderId: String? = null,
        sourceType: String = "unknown",
        recognitionMode: String = "auto"
    ): StoredDocument {
        val documentId = id ?: UUID.randomUUID().toString()
        val existing = readDocument(File(documentsDirectory, "$documentId.json"))
        val now = System.currentTimeMillis()
        val previewFile = File(documentsDirectory, "$documentId.jpg")

        if (preview != null) {
            previewFile.outputStream().buffered().use { stream ->
                preview.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            }
        }

        val document = StoredDocument(
            id = documentId,
            title = title.trim().ifBlank { "Untitled document" },
            text = text,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            wordCount = Regex("\\S+").findAll(text).count(),
            lineCount = text.lineSequence().count(),
            previewPath = previewFile.takeIf(File::exists)?.absolutePath,
            reviewNotes = reviewNotes,
            highlightedPhrases = highlightedPhrases,
            bookmarked = bookmarked,
            highlightColor = highlightColor,
            folderId = folderId,
            sourceType = sourceType,
            recognitionMode = recognitionMode,
            characterCount = text.length
        )
        writeDocument(document)
        return document
    }

    fun moveDocument(document: StoredDocument, folderId: String?): StoredDocument {
        require(folderId == null || listFolders().any { it.id == folderId }) { "Folder not found" }
        val moved = document.copy(folderId = folderId, updatedAt = System.currentTimeMillis())
        writeDocument(moved)
        return moved
    }

    fun deleteDocument(document: StoredDocument) {
        File(documentsDirectory, "${document.id}.json").delete()
        document.previewPath?.let(::File)?.delete()
    }

    private fun readDocument(file: File): StoredDocument? = runCatching {
        val json = JSONObject(file.readText())
        StoredDocument(
            id = json.getString("id"),
            title = json.optString("title", "Untitled document"),
            text = json.optString("text", ""),
            createdAt = json.optLong("createdAt", file.lastModified()),
            updatedAt = json.optLong("updatedAt", file.lastModified()),
            wordCount = json.optInt("wordCount", 0),
            lineCount = json.optInt("lineCount", 0),
            previewPath = json.optString("previewPath").takeIf { it.isNotBlank() && it != "null" },
            reviewNotes = json.optString("reviewNotes", ""),
            highlightedPhrases = json.optJSONArray("highlightedPhrases")?.let { array ->
                List(array.length()) { index -> array.optString(index) }.filter(String::isNotBlank)
            }.orEmpty(),
            bookmarked = json.optBoolean("bookmarked", false),
            highlightColor = json.optInt("highlightColor", 0xFFFFD978.toInt()),
            folderId = json.optString("folderId").takeIf { it.isNotBlank() && it != "null" },
            sourceType = json.optString("sourceType", "unknown"),
            recognitionMode = json.optString("recognitionMode", "auto"),
            characterCount = json.optInt("characterCount", json.optString("text", "").length)
        )
    }.getOrNull()

    private fun writeDocument(document: StoredDocument) {
        val json = JSONObject().apply {
            put("id", document.id)
            put("title", document.title)
            put("text", document.text)
            put("createdAt", document.createdAt)
            put("updatedAt", document.updatedAt)
            put("wordCount", document.wordCount)
            put("lineCount", document.lineCount)
            put("characterCount", document.characterCount)
            put("previewPath", document.previewPath ?: JSONObject.NULL)
            put("reviewNotes", document.reviewNotes)
            put("highlightedPhrases", JSONArray(document.highlightedPhrases))
            put("bookmarked", document.bookmarked)
            put("highlightColor", document.highlightColor)
            put("folderId", document.folderId ?: JSONObject.NULL)
            put("sourceType", document.sourceType)
            put("recognitionMode", document.recognitionMode)
        }
        val destination = File(documentsDirectory, "${document.id}.json")
        val temporary = File(documentsDirectory, "${document.id}.tmp")
        temporary.writeText(json.toString())
        if (destination.exists()) destination.delete()
        check(temporary.renameTo(destination)) { "Could not finish saving the document" }
    }

    private fun writeFolders(folders: List<StoredFolder>) {
        val array = JSONArray().apply {
            folders.forEach { folder ->
                put(JSONObject().apply {
                    put("id", folder.id)
                    put("name", folder.name)
                    put("createdAt", folder.createdAt)
                })
            }
        }
        val temporary = File(foldersFile.parentFile, "folders.tmp")
        temporary.writeText(array.toString())
        if (foldersFile.exists()) foldersFile.delete()
        check(temporary.renameTo(foldersFile)) { "Could not finish saving folders" }
    }
}
