package com.bona.fileflow

import androidx.activity.ComponentActivity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private data class DocumentItem(
        val uri: String,
        val name: String,
        val mime: String,
        val openedAt: Long,
        val favorite: Boolean
    )

    private val prefs by lazy { getSharedPreferences("fileflow_library", MODE_PRIVATE) }
    private val documents = mutableListOf<DocumentItem>()
    private lateinit var contentArea: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var themeButton: TextView
    private var selectedTab = "All"
    private var sortNewestFirst = true
    private var lightMode = false

    private val purple = Color.rgb(139, 92, 246)
    private val darkBackground = Color.rgb(13, 13, 27)
    private val darkPanel = Color.rgb(27, 27, 46)
    private val lightBackground = Color.rgb(246, 245, 251)
    private val lightPanel = Color.WHITE

    private val openDocumentRequestCode = 4101

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == openDocumentRequestCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            addDocument(uri)
            openDocumentItem(uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadDocuments()
        lightMode = prefs.getBoolean("light_mode", false)
        createHomeScreen()
    }

    private fun bg(): Int = if (lightMode) lightBackground else darkBackground
    private fun panel(): Int = if (lightMode) lightPanel else darkPanel
    private fun mainText(): Int = if (lightMode) Color.rgb(28, 27, 38) else Color.WHITE
    private fun mutedText(): Int = if (lightMode) Color.rgb(105, 103, 120) else Color.rgb(165, 165, 185)

    private fun createHomeScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg())
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val brand = TextView(this).apply {
            text = "FileFlow"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(purple)
        }
        themeButton = TextView(this).apply {
            text = if (lightMode) "☾" else "☀"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(mainText())
            setPadding(dp(12), dp(5), dp(4), dp(5))
            setOnClickListener {
                lightMode = !lightMode
                prefs.edit().putBoolean("light_mode", lightMode).apply()
                createHomeScreen()
            }
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(themeButton)

        val subtitle = TextView(this).apply {
            text = "Your documents, all in one place."
            textSize = 14f
            setTextColor(mutedText())
            setPadding(0, 0, 0, dp(18))
        }

        val openButton = Button(this).apply {
            text = "＋   Open a document"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(purple)
            setOnClickListener { chooseDocument() }
        }

        searchField = EditText(this).apply {
            hint = "Search your documents"
            textSize = 14f
            setSingleLine(true)
            setTextColor(mainText())
            setHintTextColor(mutedText())
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(panel())
            addTextChangedListener(SimpleTextWatcher { renderDocuments() })
        }

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(8))
        }
        listOf("All", "Recent", "Favorites").forEach { tab ->
            val chip = TextView(this).apply {
                text = tab
                textSize = 13f
                typeface = if (selectedTab == tab) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selectedTab == tab) Color.WHITE else mutedText())
                setPadding(dp(13), dp(9), dp(13), dp(9))
                if (selectedTab == tab) setBackgroundColor(purple)
                setOnClickListener {
                    selectedTab = tab
                    createHomeScreen()
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
            )
            params.marginEnd = dp(8)
            tabs.addView(chip, params)
        }

        val sectionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sectionTitle = TextView(this).apply {
            text = when (selectedTab) {
                "Recent" -> "Recently opened"
                "Favorites" -> "Your favorites"
                else -> "Your library"
            }
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mainText())
        }
        val sort = TextView(this).apply {
            text = if (sortNewestFirst) "Sort ↓" else "Sort ↑"
            textSize = 12f
            setTextColor(purple)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setOnClickListener {
                sortNewestFirst = !sortNewestFirst
                renderDocuments()
                text = if (sortNewestFirst) "Sort ↓" else "Sort ↑"
            }
        }
        sectionHeader.addView(sectionTitle, LinearLayout.LayoutParams(0, dp(36), 1f))
        sectionHeader.addView(sort)

        contentArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(contentArea)
        }

        root.addView(header)
        root.addView(subtitle)
        root.addView(openButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { bottomMargin = dp(16) })
        root.addView(searchField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ))
        root.addView(tabs)
        root.addView(sectionHeader)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
        renderDocuments()
    }

    private fun renderDocuments() {
        if (!::contentArea.isInitialized) return
        contentArea.removeAllViews()
        val query = if (::searchField.isInitialized) searchField.text.toString().trim() else ""
        val filtered = documents.filter {
            (selectedTab != "Favorites" || it.favorite) &&
            (selectedTab != "Recent" || true) &&
            it.name.contains(query, ignoreCase = true)
        }.let { if (sortNewestFirst) it.sortedByDescending { d -> d.openedAt } else it.sortedBy { d -> d.name.lowercase(Locale.getDefault()) } }

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (query.isNotEmpty()) "No matching documents found." else
                    if (selectedTab == "Favorites") "Your favorite documents will appear here." else
                    "Your library is empty. Open a document to get started."
                textSize = 14f
                setTextColor(mutedText())
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(45), dp(18), dp(45))
            }
            contentArea.addView(empty)
            return
        }

        filtered.forEach { item -> contentArea.addView(documentCard(item)) }
    }

    private fun documentCard(item: DocumentItem): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(12), dp(10), dp(12))
            setBackgroundColor(panel())
            isClickable = true
            setOnClickListener { openDocumentItem(item.uri) }
        }
        val icon = TextView(this).apply {
            text = iconFor(item.mime, item.name)
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(purple)
        }
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = item.name
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mainText())
            maxLines = 1
        }
        val info = TextView(this).apply {
            text = "${item.mime.substringAfterLast('/').uppercase(Locale.getDefault())}  •  ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(item.openedAt))}"
            textSize = 11f
            setTextColor(mutedText())
            setPadding(0, dp(4), 0, 0)
        }
        details.addView(title)
        details.addView(info)
        val favorite = TextView(this).apply {
            text = if (item.favorite) "★" else "☆"
            textSize = 23f
            setTextColor(if (item.favorite) purple else mutedText())
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(5), dp(5))
            setOnClickListener { toggleFavorite(item.uri) }
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(42), dp(44)))
        card.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        })
        card.addView(favorite)
        return card.apply {
            val outer = LinearLayout(this@MainActivity)
            outer.orientation = LinearLayout.VERTICAL
        }
    }

    private fun chooseDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain", "text/csv", "application/epub+zip", "image/*"
            ))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, openDocumentRequestCode)
    }

    private fun addDocument(uri: Uri) {
        val name = queryName(uri) ?: "Untitled document"
        val mime = contentResolver.getType(uri) ?: guessMime(name)
        documents.removeAll { it.uri == uri.toString() }
        documents.add(0, DocumentItem(uri.toString(), name, mime, System.currentTimeMillis(), false))
        saveDocuments()
    }

    private fun openDocumentItem(uriString: String) {
        val item = documents.firstOrNull { it.uri == uriString } ?: return
        val uri = Uri.parse(uriString)
        val mime = item.mime.ifBlank { "*/*" }
        if (mime == "application/pdf" || item.name.endsWith(".pdf", true)) {
            startActivity(Intent(this, PdfViewerActivity::class.java).apply {
                putExtra("pdf_uri", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                Toast.makeText(this, "Opening with a compatible app on your device.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "No app found to open this file type yet.", Toast.LENGTH_LONG).show()
            }
        }
        val index = documents.indexOfFirst { it.uri == uriString }
        if (index >= 0) documents[index] = documents[index].copy(openedAt = System.currentTimeMillis())
        saveDocuments()
        renderDocuments()
    }

    private fun toggleFavorite(uri: String) {
        val index = documents.indexOfFirst { it.uri == uri }
        if (index >= 0) {
            documents[index] = documents[index].copy(favorite = !documents[index].favorite)
            saveDocuments()
            renderDocuments()
        }
    }

    private fun queryName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.getDefault())) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "epub" -> "application/epub+zip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    private fun iconFor(mime: String, name: String): String = when {
        mime == "application/pdf" || name.endsWith(".pdf", true) -> "▤"
        mime.contains("word") || name.endsWith(".docx", true) || name.endsWith(".doc", true) -> "W"
        mime.contains("sheet") || mime.contains("excel") || name.endsWith(".xlsx", true) || name.endsWith(".xls", true) -> "X"
        mime.contains("presentation") || name.endsWith(".pptx", true) || name.endsWith(".ppt", true) -> "P"
        mime.startsWith("image/") -> "▧"
        name.endsWith(".epub", true) -> "E"
        else -> "≡"
    }

    private fun loadDocuments() {
        documents.clear()
        try {
            val array = JSONArray(prefs.getString("documents", "[]"))
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                documents.add(DocumentItem(
                    obj.getString("uri"), obj.getString("name"),
                    obj.optString("mime", "*/*"), obj.optLong("openedAt"),
                    obj.optBoolean("favorite", false)
                ))
            }
        } catch (_: Exception) { }
    }

    private fun saveDocuments() {
        val array = JSONArray()
        documents.forEach { item ->
            array.put(JSONObject().apply {
                put("uri", item.uri); put("name", item.name); put("mime", item.mime)
                put("openedAt", item.openedAt); put("favorite", item.favorite)
            })
        }
        prefs.edit().putString("documents", array.toString()).apply()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class SimpleTextWatcher(val changed: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed() }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
}
