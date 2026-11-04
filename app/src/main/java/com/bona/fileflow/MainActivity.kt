package com.bona.fileflow

import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.PopupMenu
import java.io.File
import java.security.MessageDigest
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.provider.DocumentsContract

class MainActivity : ComponentActivity() {

    private data class DocumentItem(
        val uri: String,
        val name: String,
        val mime: String,
        val openedAt: Long,
        val favorite: Boolean,
        val isDeleted: Boolean = false,
        val isPrivate: Boolean = false,
        val contentHash: String = "",
        val privatePath: String = ""
    )

    private val prefs by lazy { getSharedPreferences("fileflow_library", MODE_PRIVATE) }
    private val documents = mutableListOf<DocumentItem>()
    private lateinit var contentArea: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var themeButton: TextView
    private var selectedTab = "All"
    private var sortNewestFirst = true
    private var lightMode = false
    private var privateUnlocked = false

    private val purple = Color.rgb(139, 92, 246)
    private val darkBackground = Color.rgb(13, 13, 27)
    private val darkPanel = Color.rgb(27, 27, 46)
    private val lightBackground = Color.rgb(246, 245, 251)
    private val lightPanel = Color.WHITE

    private val openDocumentRequestCode = 4101
    private val convertPdfRequestCode = 4102
    private val folderRequestCode = 4103
    private val createFileRequestCode = 4104
    private var pendingConvertedFile: File? = null
    private var pendingCreatedFile: File? = null

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
        } else if (requestCode == folderRequestCode && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("library_tree_uri", treeUri.toString()).apply()
                scanFolder(treeUri)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not access folder: " + e.message, Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == createFileRequestCode && resultCode == RESULT_OK) {
            val target = data?.data
            val source = pendingCreatedFile
            if (target != null && source != null) {
                try {
                    contentResolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    addDocument(target)
                    Toast.makeText(this, "File saved.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not save: " + e.message, Toast.LENGTH_LONG).show()
                } finally { source.delete(); pendingCreatedFile = null }
            }
        } else if (requestCode == convertPdfRequestCode && resultCode == RESULT_OK) {
            val destination = data?.data
            val source = pendingConvertedFile
            if (destination != null && source != null) {
                try {
                    contentResolver.openOutputStream(destination)?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    }
                    Toast.makeText(this, "PDF created successfully.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "PDF conversion failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    source.delete()
                    pendingConvertedFile = null
                }
            }
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
        val logo = ImageView(this).apply {
            setImageResource(com.bona.fileflow.R.drawable.fileflow_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
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
        listOf("All", "Recent", "Favorites", "Private", "Trash", "Duplicates").forEach { tab ->
            val chip = TextView(this).apply {
                text = tab
                textSize = 13f
                typeface = if (selectedTab == tab) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selectedTab == tab) Color.WHITE else mutedText())
                setPadding(dp(13), dp(9), dp(13), dp(9))
                if (selectedTab == tab) setBackgroundColor(purple)
                setOnClickListener {
                    selectedTab = tab
                    if (tab == "Private" && !privateUnlocked) {
                        verifyPrivatePin {
                            privateUnlocked = true
                            createHomeScreen()
                        }
                    } else {
                        createHomeScreen()
                    }
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
                "Private" -> "Private folder"
                "Trash" -> "Recently deleted"
                "Duplicates" -> "Duplicate documents"
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
        val folderButton = Button(this).apply {
            text = "Browse folder"
            isAllCaps = false
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(intent, folderRequestCode)
            }
        }
        val createButton = Button(this).apply { text = "Create"; isAllCaps = false; setOnClickListener { showCreateFileDialog() } }
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(openButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
            addView(createButton, LinearLayout.LayoutParams(dp(88), dp(48)).apply { marginEnd = dp(5) })
            addView(folderButton, LinearLayout.LayoutParams(dp(118), dp(48)))
        }
        root.addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(14) })
        root.addView(searchField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ))
        val tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }

        root.addView(tabsScroll)
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
        val duplicateHashes = documents.filter { !it.isDeleted && it.contentHash.isNotBlank() }
            .groupingBy { it.contentHash }.eachCount().filterValues { it > 1 }.keys
        val filtered = documents.filter {
            when (selectedTab) {
                "Favorites" -> it.favorite && !it.isDeleted && !it.isPrivate
                "Recent" -> !it.isDeleted && !it.isPrivate &&
                    it.openedAt >= System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                "Private" -> it.isPrivate && !it.isDeleted
                "Trash" -> it.isDeleted
                "Duplicates" -> !it.isDeleted && !it.isPrivate && it.contentHash in duplicateHashes
                else -> !it.isDeleted && !it.isPrivate
            } && it.name.contains(query, ignoreCase = true)
        }.let { if (sortNewestFirst) it.sortedByDescending { d -> d.openedAt } else it.sortedBy { d -> d.name.lowercase(Locale.getDefault()) } }

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (query.isNotEmpty()) "No matching documents found." else when (selectedTab) {
                    "Favorites" -> "Your favorite documents will appear here."
                    "Private" -> "Your private documents will appear here."
                    "Trash" -> "Recently deleted documents will appear here."
                    "Duplicates" -> "No duplicate files were found."
                    else -> "Your library is empty. Open a document to get started."
                }
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
            background = android.graphics.drawable.GradientDrawable().apply { setColor(panel()); cornerRadius = dp(15).toFloat() }
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
        val more = TextView(this).apply {
            text = "⋮"
            textSize = 22f
            setTextColor(mutedText())
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(5), dp(5))
            setOnClickListener { showDocumentMenu(this, item) }
        }
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
        card.addView(more)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(9))
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun scanFolder(treeUri: Uri) {
        var added = 0
        val seen = mutableSetOf<String>()
        fun visit(parentId: String, depth: Int) {
            if (depth > 5 || seen.size >= 500) return
            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            contentResolver.query(childUri, columns, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext() && seen.size < 500) {
                    val id = cursor.getString(idIndex)
                    if (!seen.add(id)) continue
                    val mime = cursor.getString(mimeIndex) ?: "application/octet-stream"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        visit(id, depth + 1)
                    } else {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        if (documents.none { it.uri == fileUri.toString() }) {
                            documents.add(DocumentItem(
                                fileUri.toString(),
                                cursor.getString(nameIndex) ?: "Untitled document",
                                mime,
                                System.currentTimeMillis(),
                                false
                            ))
                            added++
                        }
                    }
                }
            }
        }
        try {
            visit(DocumentsContract.getTreeDocumentId(treeUri), 0)
            saveDocuments()
            renderDocuments()
            Toast.makeText(this, added.toString() + " new files added from the selected folder.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Folder scan failed: " + e.message, Toast.LENGTH_LONG).show()
        }
    }


    private fun showCreateFileDialog() {
        val input = EditText(this).apply {
            hint = "Enter text for the new file"
            minLines = 6
            gravity = Gravity.TOP
        }
        val name = EditText(this).apply { setText("Untitled"); hint = "File name" }
        val choices = arrayOf("Word document (.docx)", "Excel spreadsheet (.xlsx)", "PDF document (.pdf)", "Text file (.txt)", "CSV spreadsheet (.csv)")
        AlertDialog.Builder(this).setTitle("Create a file").setItems(choices) { _, which ->
            val ext = arrayOf("docx", "xlsx", "pdf", "txt", "csv")[which]
            val form = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), 0)
                addView(name)
                addView(input, LinearLayout.LayoutParams(-1, dp(150)))
            }
            AlertDialog.Builder(this).setTitle("New " + ext.uppercase(Locale.getDefault())).setView(form)
                .setPositiveButton("Save") { _, _ ->
                    try {
                        val file = File(cacheDir, "created-" + System.currentTimeMillis() + "." + ext)
                        when (ext) { "docx" -> makeDocx(file, input.text.toString()); "xlsx" -> makeXlsx(file, input.text.toString()); "pdf" -> createPdfFile(file, input.text.toString()); else -> file.writeText(input.text.toString()) }
                        pendingCreatedFile = file
                        val mime = when (ext) { "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; "pdf" -> "application/pdf"; "csv" -> "text/csv"; else -> "text/plain" }
                        val save = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = mime
                            putExtra(Intent.EXTRA_TITLE, name.text.toString().trim().ifBlank { "Untitled" } + "." + ext)
                        }
                        startActivityForResult(save, createFileRequestCode)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not create file: " + e.message, Toast.LENGTH_LONG).show()
                    }
                }.setNegativeButton("Cancel", null).show()
        }.setNegativeButton("Cancel", null).show()
    }


    private fun writeOfficeZip(file: File, parts: Map<String, String>) {
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            parts.forEach { (path, xml) ->
                zip.putNextEntry(java.util.zip.ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace(String(charArrayOf(39)), "&apos;")

    private fun makeDocx(file: File, text: String) {
        val paragraphs = text.lines().joinToString("") { line ->
            "<w:p><w:r><w:t>" + xmlEscape(line) + "</w:t></w:r></w:p>"
        }
        writeOfficeZip(file, mapOf(
            "[Content_Types].xml" to """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/document.xml" to """<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr/></w:body></w:document>"""
        ))
    }

    private fun makeXlsx(file: File, text: String) {
        val rows = text.lines().mapIndexed { rowIndex, line ->
            val cells = line.split(",").mapIndexed { columnIndex, value ->
                var n = columnIndex + 1
                var column = ""
                while (n > 0) {
                    val remainder = (n - 1) % 26
                    column = ("A".first().code + remainder).toChar() + column
                    n = (n - 1) / 26
                }
                val reference = column + (rowIndex + 1)
                """<c r="$reference" t="inlineStr"><is><t>""" + xmlEscape(value.trim()) + """</t></is></c>"""
            }.joinToString("")
            """<row r="${rowIndex + 1}">$cells</row>"""
        }.joinToString("")
        writeOfficeZip(file, mapOf(
            "[Content_Types].xml" to """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>"""
        ))
    }

    private fun createPdfFile(file: File, text: String) {
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
        var pageNo = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var y = 48f
        text.lines().forEach { line ->
            var rest = line.ifEmpty { " " }
            while (rest.isNotEmpty()) {
                if (y > 790f) {
                    pdf.finishPage(page)
                    pageNo++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                    y = 48f
                }
                val count = paint.breakText(rest, true, 500f, null).coerceAtLeast(1)
                page.canvas.drawText(rest.substring(0, count), 48f, y, paint)
                rest = rest.substring(count)
                y += 18f
            }
        }
        pdf.finishPage(page)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
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
        documents.add(0, DocumentItem(
            uri.toString(), name, mime, System.currentTimeMillis(), false,
            contentHash = calculateHash(uri)
        ))
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

    private fun showDocumentMenu(anchor: View, item: DocumentItem) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("Share")
        if (!item.name.endsWith(".pdf", true)) menu.menu.add("Convert to PDF")
        if (!item.isPrivate && !item.isDeleted) menu.menu.add("Move to private folder")
        if (item.isDeleted) {
            menu.menu.add("Restore")
            menu.menu.add("Delete permanently")
        } else {
            menu.menu.add("Move to recently deleted")
        }
        menu.setOnMenuItemClickListener {
            when (it.title.toString()) {
                "Share" -> shareDocument(item)
                "Convert to PDF" -> convertDocumentToPdf(item)
                "Move to private folder" -> moveToPrivateFolder(item)
                "Move to recently deleted" -> moveToTrash(item)
                "Restore" -> updateItem(item) { copy(isDeleted = false) }
                "Delete permanently" -> permanentlyDelete(item)
            }
            true
        }
        menu.show()
    }

    private fun updateItem(item: DocumentItem, transform: DocumentItem.() -> DocumentItem) {
        val index = documents.indexOfFirst { it.uri == item.uri }
        if (index >= 0) {
            documents[index] = documents[index].transform()
            saveDocuments()
            renderDocuments()
        }
    }

    private fun moveToTrash(item: DocumentItem) {
        updateItem(item) { copy(isDeleted = true) }
        Toast.makeText(this, "Moved to recently deleted", Toast.LENGTH_SHORT).show()
    }

    private fun permanentlyDelete(item: DocumentItem) {
        if (item.privatePath.isNotBlank()) File(item.privatePath).delete()
        documents.removeAll { it.uri == item.uri }
        saveDocuments()
        renderDocuments()
        Toast.makeText(this, "Removed from FileFlow", Toast.LENGTH_SHORT).show()
    }

    private fun convertDocumentToPdf(item: DocumentItem) {
        val extension = item.name.substringAfterLast('.', "").lowercase(Locale.getDefault())
        if (extension !in listOf("txt", "csv", "png", "jpg", "jpeg", "webp")) {
            Toast.makeText(
                this,
                "This file needs an Office app to export to PDF. Text and image files are supported directly.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            val output = File(cacheDir, "FileFlow-${item.name.substringBeforeLast('.')}.pdf")
            val pdf = PdfDocument()
            if (extension in listOf("png", "jpg", "jpeg", "webp")) {
                val bitmap = contentResolver.openInputStream(Uri.parse(item.uri))?.use {
                    BitmapFactory.decodeStream(it)
                } ?: throw Exception("Could not read image")
                val pageWidth = 595
                val pageHeight = 842
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
                val scale = minOf((pageWidth - 48f) / bitmap.width, (pageHeight - 48f) / bitmap.height)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                val left = (pageWidth - width) / 2f
                val top = (pageHeight - height) / 2f
                page.canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + width, top + height), null)
                pdf.finishPage(page)
                bitmap.recycle()
            } else {
                val text = contentResolver.openInputStream(Uri.parse(item.uri))?.bufferedReader()?.use { it.readText() }
                    ?: throw Exception("Could not read text file")
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 10f
                }
                val pageWidth = 595
                val pageHeight = 842
                val margin = 42f
                val maxWidth = pageWidth - margin * 2
                val lineHeight = 14f
                var pageNumber = 1
                var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                var y = margin
                text.lines().forEach { sourceLine ->
                    var remaining = sourceLine
                    if (remaining.isEmpty()) remaining = " "
                    while (remaining.isNotEmpty()) {
                        if (y > pageHeight - margin) {
                            pdf.finishPage(page)
                            pageNumber++
                            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                            y = margin
                        }
                        val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                        page.canvas.drawText(remaining.substring(0, count), margin, y, paint)
                        remaining = remaining.substring(count)
                        y += lineHeight
                    }
                }
                pdf.finishPage(page)
            }
            output.outputStream().use { pdf.writeTo(it) }
            pdf.close()
            pendingConvertedFile = output
            val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, "${item.name.substringBeforeLast('.')}.pdf")
            }
            startActivityForResult(saveIntent, convertPdfRequestCode)
        } catch (e: Exception) {
            Toast.makeText(this, "PDF conversion failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareDocument(item: DocumentItem) {
        try {
            val uri = Uri.parse(item.uri)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = item.mime.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share document"))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to share this document.", Toast.LENGTH_LONG).show()
        }
    }

    private fun moveToPrivateFolder(item: DocumentItem) {
        verifyPrivatePin {
            try {
                val folder = File(filesDir, "private_documents")
                if (!folder.exists()) folder.mkdirs()
                val safeName = item.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val destination = File(folder, "${System.currentTimeMillis()}_$safeName")
                contentResolver.openInputStream(Uri.parse(item.uri))!!.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                val privateUri = FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", destination
                )
                val index = documents.indexOfFirst { it.uri == item.uri }
                if (index >= 0) {
                    documents[index] = item.copy(
                        uri = privateUri.toString(),
                        isPrivate = true,
                        privatePath = destination.absolutePath
                    )
                    saveDocuments()
                    renderDocuments()
                }
                Toast.makeText(this, "Private copy created inside FileFlow.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not create private copy: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun verifyPrivatePin(onVerified: () -> Unit) {
        val pinPrefs = getSharedPreferences("fileflow_security", MODE_PRIVATE)
        val stored = pinPrefs.getString("pin_hash", null)
        val field = EditText(this).apply {
            hint = if (stored == null) "Create a PIN (4–8 digits)" else "Enter your PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(if (stored == null) "Set private folder PIN" else "Unlock private folder")
            .setView(field)
            .setPositiveButton(if (stored == null) "Create PIN" else "Unlock") { _, _ ->
                val pin = field.text.toString()
                if (pin.length !in 4..8) {
                    Toast.makeText(this, "PIN must contain 4–8 digits.", Toast.LENGTH_LONG).show()
                } else {
                    val hash = MessageDigest.getInstance("SHA-256")
                        .digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
                    if (stored == null) {
                        pinPrefs.edit().putString("pin_hash", hash).apply()
                        onVerified()
                    } else if (hash == stored) {
                        onVerified()
                    } else {
                        Toast.makeText(this, "Incorrect PIN.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun calculateHash(uri: Uri): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    digest.update(buffer, 0, count)
                }
            } ?: return ""
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }
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
                    obj.optBoolean("favorite", false),
                    obj.optBoolean("isDeleted", false),
                    obj.optBoolean("isPrivate", false),
                    obj.optString("contentHash", ""),
                    obj.optString("privatePath", "")
                ))
            }
        } catch (_: Exception) { }
        var hashesChanged = false
        documents.indices.forEach { index ->
            val item = documents[index]
            if (item.contentHash.isBlank() && !item.isPrivate && !item.isDeleted) {
                val hash = calculateHash(Uri.parse(item.uri))
                if (hash.isNotBlank()) {
                    documents[index] = item.copy(contentHash = hash)
                    hashesChanged = true
                }
            }
        }
        if (hashesChanged) saveDocuments()
    }

    private fun saveDocuments() {
        val array = JSONArray()
        documents.forEach { item ->
            array.put(JSONObject().apply {
                put("uri", item.uri); put("name", item.name); put("mime", item.mime)
                put("openedAt", item.openedAt); put("favorite", item.favorite)
                put("isDeleted", item.isDeleted); put("isPrivate", item.isPrivate)
                put("contentHash", item.contentHash); put("privatePath", item.privatePath)
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
