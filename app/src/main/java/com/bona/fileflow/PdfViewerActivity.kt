package com.bona.fileflow

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import java.util.concurrent.Executors
import java.io.File
import java.io.FileOutputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlin.math.abs

class PdfViewerActivity : Activity() {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0

    private lateinit var pageImage: ZoomableImageView
    private lateinit var pageCounter: TextView
    private lateinit var sepiaButton: TextView
    private lateinit var fitButton: TextView
    private lateinit var rotateButton: TextView
    private lateinit var scrollButton: TextView
    private lateinit var spreadButton: TextView
    private lateinit var toolsButton: TextView
    private var currentUri: Uri? = null
    private var pendingExportFile: File? = null
    private val exportRequestCode = 4202
    private val imageExportRequestCode = 4203
    private var lastOcrText: String = ""
    private lateinit var rootLayout: LinearLayout
    private lateinit var imageContainer: LinearLayout
    private var readerContainer: View? = null
    private var continuousMode = false
    private var twoPageMode = false
    private val continuousPageViews = mutableListOf<ZoomableImageView>()
    private val renderingPages = mutableSetOf<Int>()
    private var sepiaEnabled = false
    private var fitWidthEnabled = false
    private var orientationLocked = false

    private val backgroundColor = Color.rgb(13, 13, 27)
    private val panelColor = Color.rgb(25, 25, 43)
    private val purpleColor = Color.rgb(139, 92, 246)
    private val mutedColor = Color.rgb(165, 165, 185)

    private val renderExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rendererLock = Any()

    @Volatile
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        createViewerInterface()

        val uri = intent.getParcelableExtra<Uri>("pdf_uri")
        currentUri = uri

        if (uri == null) {
            Toast.makeText(this, "PDF file not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            if (fileDescriptor == null) throw Exception("Unable to open PDF")

            renderer = PdfRenderer(fileDescriptor!!)
            updateCounter()
            renderPage()

        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open this PDF", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun createViewerInterface() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(6), dp(3), dp(6), dp(2))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(this).apply {
            text = "FileFlow"
            textSize = 19f
            setTextColor(purpleColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val label = TextView(this).apply {
            text = "  /  READER"
            textSize = 10f
            setTextColor(mutedColor)
        }

        header.addView(brand)
        header.addView(label)

        pageCounter = TextView(this).apply {
            text = "PAGE 1 / 1"
            textSize = 10f
            setTextColor(purpleColor)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundedBackground(panelColor, 20)
        }

        val counterRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(2), dp(3), dp(5))
        }

        counterRow.addView(pageCounter)

        pageImage = ZoomableImageView(this) { direction ->
            changePage(direction)
        }

        imageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(panelColor, 8)
            clipToOutline = true
            setPadding(dp(1), dp(1), dp(1), dp(1))
        }

        imageContainer.addView(
            pageImage,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        rootLayout.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(28)
        ))

        rootLayout.addView(counterRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(29)
        ))

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }

        sepiaButton = createControlButton("Sepia")
        fitButton = createControlButton("Fit width")
        rotateButton = createControlButton("Auto rotate")
        scrollButton = createControlButton("Continuous")
        spreadButton = createControlButton("Two pages")
        toolsButton = createControlButton("Tools")

        sepiaButton.setOnClickListener {
            sepiaEnabled = !sepiaEnabled
            pageImage.setSepia(sepiaEnabled)
            continuousPageViews.forEach { it.setSepia(sepiaEnabled) }
            sepiaButton.text = if (sepiaEnabled) "Sepia: On" else "Sepia"
            sepiaButton.isSelected = sepiaEnabled
        }

        fitButton.setOnClickListener {
            fitWidthEnabled = !fitWidthEnabled
            pageImage.setFitWidth(fitWidthEnabled)
            continuousPageViews.forEach { it.setFitWidth(fitWidthEnabled) }
            fitButton.text = if (fitWidthEnabled) "Fit width: On" else "Fit width"
            fitButton.isSelected = fitWidthEnabled
        }

        rotateButton.setOnClickListener {
            orientationLocked = !orientationLocked
            requestedOrientation = if (orientationLocked) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            rotateButton.text = if (orientationLocked) "Portrait lock: On" else "Auto rotate"
            rotateButton.isSelected = orientationLocked
        }

        scrollButton.setOnClickListener {
            continuousMode = !continuousMode
            if (!continuousMode) twoPageMode = false
            scrollButton.text = if (continuousMode) "Continuous: On" else "Continuous"
            scrollButton.isSelected = continuousMode
            spreadButton.isEnabled = continuousMode
            spreadButton.alpha = if (continuousMode) 1f else 0.55f
            spreadButton.isSelected = twoPageMode
            switchReaderContainer()
        }

        spreadButton.setOnClickListener {
            if (!continuousMode) return@setOnClickListener
            twoPageMode = !twoPageMode
            spreadButton.text = if (twoPageMode) "Two pages: On" else "Two pages"
            spreadButton.isSelected = twoPageMode
            switchReaderContainer()
        }

        listOf(sepiaButton, fitButton, rotateButton, scrollButton, spreadButton, toolsButton)
            .forEachIndexed { index, button ->
                controlsRow.addView(button, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)
                ).apply {
                    if (index > 0) marginStart = dp(6)
                })
            }

        spreadButton.isEnabled = false
        spreadButton.alpha = 0.55f
        toolsButton.setOnClickListener { showToolsMenu(toolsButton) }

        val controlsScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(controlsRow, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        rootLayout.addView(controlsScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ))

        readerContainer = imageContainer
        rootLayout.addView(imageContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(rootLayout)
    }

    private fun showToolsMenu(anchor: View) {
        val items = arrayOf(
            "Save this section", "View saved sections", "Add note", "View notes",
            "Draw on page", "Highlight", "Underline", "Stop annotating",
            "OCR this page", "OCR entire PDF", "Translate OCR text",
            "Screen brightness", "Export this page as PDF",
            "Export this page as image", "Protect entire PDF with password"
        )
        AlertDialog.Builder(this)
            .setTitle("Reader tools")
            .setItems(items) { _, which ->
                when (items[which]) {
                    "Save this section" -> saveCurrentSection()
                    "View saved sections" -> viewSavedSections()
                    "Add note" -> addPageNote()
                    "View notes" -> viewPageNotes()
                    "Draw on page" -> setAnnotationMode("draw")
                    "Highlight" -> setAnnotationMode("highlight")
                    "Underline" -> setAnnotationMode("underline")
                    "Stop annotating" -> setAnnotationMode(null)
                    "OCR this page" -> runOcrOnCurrentPage()
                    "OCR entire PDF" -> runOcrOnWholeDocument()
                    "Translate OCR text" -> translateLastOcrText()
                    "Screen brightness" -> showBrightnessControl()
                    "Export this page as PDF" -> exportCurrentPage(false)
                    "Export this page as image" -> exportCurrentPageAsImage()
                    "Protect entire PDF with password" -> protectEntirePdf()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun setAnnotationMode(mode: String?) {
        val target = if (continuousMode) {
            continuousPageViews.getOrNull(currentPage)
        } else pageImage
        target?.setAnnotationMode(mode)
        Toast.makeText(
            this,
            when (mode) {
                "draw" -> "Draw mode: drag your finger over the page."
                "highlight" -> "Highlight mode: drag over text."
                "underline" -> "Underline mode: drag under text."
                else -> "Annotation mode off."
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveCurrentSection() {
        val key = "saved_sections_" + (currentUri?.toString() ?: "unknown")
        val saved = getSharedPreferences("fileflow_reader", MODE_PRIVATE)
            .getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        saved.add(currentPage.toString())
        getSharedPreferences("fileflow_reader", MODE_PRIVATE).edit()
            .putStringSet(key, saved).apply()
        Toast.makeText(this, "Section saved (page ${currentPage + 1})", Toast.LENGTH_SHORT).show()
    }

    private fun viewSavedSections() {
        val key = "saved_sections_" + (currentUri?.toString() ?: "unknown")
        val saved = getSharedPreferences("fileflow_reader", MODE_PRIVATE)
            .getStringSet(key, emptySet())?.mapNotNull { it.toIntOrNull() }?.sorted() ?: emptyList()
        if (saved.isEmpty()) {
            Toast.makeText(this, "No saved sections yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = saved.map { "Page ${it + 1}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Saved sections")
            .setItems(labels) { _, which ->
                currentPage = saved[which]
                if (continuousMode) {
                    val target = continuousPageViews.getOrNull(currentPage)
                    val scroll = readerContainer as? ScrollView
                    if (target != null && scroll != null) {
                        val targetLocation = IntArray(2)
                        val scrollLocation = IntArray(2)
                        target.getLocationOnScreen(targetLocation)
                        scroll.getLocationOnScreen(scrollLocation)
                        scroll.smoothScrollBy(0, targetLocation[1] - scrollLocation[1])
                    }
                } else {
                    updateCounter()
                    renderPage()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun viewPageNotes() {
        val key = "notes_" + (currentUri?.toString() ?: "unknown")
        val notes = getSharedPreferences("fileflow_reader", MODE_PRIVATE).getString(key, "")
        AlertDialog.Builder(this)
            .setTitle("Document notes")
            .setMessage(if (notes.isNullOrBlank()) "No notes saved yet." else notes)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showBrightnessControl() {
        val seek = android.widget.SeekBar(this).apply {
            max = 100
            progress = ((window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.8f) * 100).toInt()
        }
        AlertDialog.Builder(this)
            .setTitle("Screen brightness")
            .setView(seek)
            .setPositiveButton("Apply") { _, _ ->
                window.attributes = window.attributes.apply {
                    screenBrightness = (seek.progress / 100f).coerceIn(0.05f, 1f)
                }
            }
            .setNeutralButton("System default") { _, _ ->
                window.attributes = window.attributes.apply { screenBrightness = -1f }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runOcrOnWholeDocument() {
        val total = renderer?.pageCount ?: return
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val output = StringBuilder()
        Toast.makeText(this, "Extracting text from ${total} pages…", Toast.LENGTH_LONG).show()

        fun processPage(index: Int) {
            if (destroyed) {
                recognizer.close()
                return
            }
            if (index >= total) {
                recognizer.close()
                lastOcrText = output.toString().trim()
                if (lastOcrText.isBlank()) {
                    Toast.makeText(this, "No readable text was found.", Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Extracted document text")
                        .setMessage(lastOcrText)
                        .setPositiveButton("Copy") { _, _ ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OCR text", lastOcrText))
                        }
                        .setNeutralButton("Translate") { _, _ -> translateLastOcrText() }
                        .setNegativeButton("Close", null)
                        .show()
                }
                return
            }
            renderExecutor.execute {
                val bitmap = try { renderBitmap(index, 1200) } catch (_: Exception) { null }
                mainHandler.post {
                    if (bitmap == null) {
                        output.append("\n[Page ${index + 1}: could not render]\n")
                        processPage(index + 1)
                    } else {
                        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener { result ->
                                if (result.text.isNotBlank()) {
                                    output.append("\n--- Page ${index + 1} ---\n")
                                    output.append(result.text).append('\n')
                                }
                            }
                            .addOnCompleteListener {
                                if (!bitmap.isRecycled) bitmap.recycle()
                                processPage(index + 1)
                            }
                    }
                }
            }
        }
        processPage(0)
    }

    private fun activePageImage(): ZoomableImageView? =
        if (continuousMode) continuousPageViews.getOrNull(currentPage) else pageImage

    private fun exportCurrentPageAsImage() {
        val bitmap = (activePageImage()?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (bitmap == null) {
            Toast.makeText(this, "The current page is not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val output = File(cacheDir, "FileFlow-page-${currentPage + 1}.png")
            FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            pendingExportFile = output
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/png"
                putExtra(Intent.EXTRA_TITLE, output.name)
            }
            startActivityForResult(intent, imageExportRequestCode)
        } catch (e: Exception) {
            Toast.makeText(this, "Image export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addPageNote() {
        val input = EditText(this).apply {
            hint = "Write a note for this page"
            minLines = 3
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle("Note for page ${currentPage + 1}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val key = "notes_" + (currentUri?.toString() ?: "unknown")
                val old = getSharedPreferences("fileflow_reader", MODE_PRIVATE)
                    .getString(key, "") ?: ""
                val entry = "Page ${currentPage + 1}: ${input.text.toString().trim()}"
                getSharedPreferences("fileflow_reader", MODE_PRIVATE).edit()
                    .putString(key, if (old.isBlank()) entry else old + "\n\n" + entry)
                    .apply()
                Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runOcrOnCurrentPage() {
        val bitmap = (activePageImage()?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (bitmap == null) {
            Toast.makeText(this, "Display the page first, then try OCR.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Reading text from this page…", Toast.LENGTH_SHORT).show()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                lastOcrText = result.text
                if (lastOcrText.isBlank()) {
                    Toast.makeText(this, "No readable text found on this page.", Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Extracted text")
                        .setMessage(lastOcrText)
                        .setPositiveButton("Copy") { _, _ ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OCR text", lastOcrText))
                            Toast.makeText(this, "Text copied", Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton("Translate") { _, _ -> translateLastOcrText() }
                        .setNegativeButton("Close", null)
                        .show()
                }
                recognizer.close()
            }
            .addOnFailureListener {
                recognizer.close()
                Toast.makeText(this, "OCR failed on this page.", Toast.LENGTH_LONG).show()
            }
    }

    private fun translateLastOcrText() {
        if (lastOcrText.isBlank()) {
            Toast.makeText(this, "Run OCR on a page first.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastOcrText)
        }
        startActivity(Intent.createChooser(intent, "Send extracted text to a translation app"))
    }

    private fun protectEntirePdf() {
        val passwordInput = EditText(this).apply {
            hint = "Set a password (at least 4 characters)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Password-protect the full PDF")
            .setMessage("This creates a separate encrypted copy. Your original PDF will not be changed.")
            .setView(passwordInput)
            .setPositiveButton("Protect") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.length < 4) {
                    Toast.makeText(this, "Use at least 4 characters.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                renderExecutor.execute {
                    var loaded: PDDocument? = null
                    try {
                        PDFBoxResourceLoader.init(applicationContext)
                        val uri = currentUri ?: throw Exception("PDF location is unavailable")
                        val input = contentResolver.openInputStream(uri) ?: throw Exception("Cannot read original PDF")
                        input.use { loaded = PDDocument.load(it) }
                        val document = loaded ?: throw Exception("Cannot open PDF")
                        val permissions = AccessPermission()
                        val policy = StandardProtectionPolicy(
                            java.util.UUID.randomUUID().toString(), password, permissions
                        )
                        policy.encryptionKeyLength = 128
                        document.protect(policy)
                        val output = File(cacheDir, "FileFlow-protected-document.pdf")
                        document.save(output)
                        document.close()
                        loaded = null
                        mainHandler.post {
                            if (destroyed) {
                                output.delete()
                            } else {
                                pendingExportFile = output
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_TITLE, "FileFlow-protected-document.pdf")
                                }
                                startActivityForResult(intent, exportRequestCode)
                            }
                        }
                    } catch (e: Exception) {
                        loaded?.close()
                        mainHandler.post {
                            Toast.makeText(
                                this,
                                "Could not protect this PDF: ${e.message ?: "unsupported PDF"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportCurrentPage(protect: Boolean) {
        val bitmap = (pageImage.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (bitmap == null) {
            Toast.makeText(this, "The current page is not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        if (protect) {
            val passwordInput = EditText(this).apply {
                hint = "Set a PDF password"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("Protect exported PDF")
                .setMessage("This exports only the current page and encrypts that new PDF.")
                .setView(passwordInput)
                .setPositiveButton("Continue") { _, _ ->
                    val password = passwordInput.text.toString()
                    if (password.length < 4) {
                        Toast.makeText(this, "Use at least 4 characters.", Toast.LENGTH_LONG).show()
                    } else {
                        createPagePdf(bitmap, password)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            createPagePdf(bitmap, null)
        }
    }

    private fun createPagePdf(bitmap: Bitmap, password: String?) {
        try {
            val output = File(cacheDir, "FileFlow-page-${currentPage + 1}.pdf")
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)
            FileOutputStream(output).use { document.writeTo(it) }
            document.close()

            if (password != null) {
                PDFBoxResourceLoader.init(applicationContext)
                val loaded = PDDocument.load(output)
                try {
                    val permissions = AccessPermission()
                    val policy = StandardProtectionPolicy(
                        java.util.UUID.randomUUID().toString(), password, permissions
                    )
                    policy.encryptionKeyLength = 128
                    loaded.protect(policy)
                    val protectedOutput = File(cacheDir, "FileFlow-protected-${System.currentTimeMillis()}.pdf")
                    loaded.save(protectedOutput)
                    protectedOutput.copyTo(output, overwrite = true)
                    protectedOutput.delete()
                } finally {
                    loaded.close()
                }
            }
            pendingExportFile = output
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, output.name)
            }
            startActivityForResult(intent, exportRequestCode)
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == exportRequestCode || requestCode == imageExportRequestCode) && resultCode == RESULT_OK) {
            val destination = data?.data ?: return
            val source = pendingExportFile ?: return
            try {
                contentResolver.openOutputStream(destination)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(this, "File saved successfully", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not save PDF: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                source.delete()
                pendingExportFile = null
            }
        }
    }

    private fun switchReaderContainer() {
        readerContainer?.let { rootLayout.removeView(it) }
        continuousPageViews.clear()
        renderingPages.clear()

        val next: View = if (continuousMode) buildContinuousReader() else imageContainer
        readerContainer = next
        rootLayout.addView(next, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        if (!continuousMode) renderPage()
    }

    private fun buildContinuousReader(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            isSmoothScrollingEnabled = true
            setBackgroundColor(panelColor)
        }
        val pages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        val total = renderer?.pageCount ?: 0
        val ratios = (0 until total).map { index ->
            synchronized(rendererLock) {
                val pdf = renderer ?: return@synchronized 1f
                val page = pdf.openPage(index)
                try { page.height.toFloat() / page.width.toFloat() }
                finally { page.close() }
            }
        }

        var index = 0
        while (index < total) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            val count = if (twoPageMode && index + 1 < total) 2 else 1
            repeat(count) {
                val pageIndex = index + it
                val image = ZoomableImageView(this) { direction ->
                    if (!continuousMode) changePage(direction)
                }.apply {
                    setSwipeNavigationEnabled(false)
                    setBackgroundColor(Color.WHITE)
                    scaleType = ImageView.ScaleType.MATRIX
                }
                image.setAnnotationStorageKey(annotationKey(pageIndex))
                continuousPageViews.add(image)
                val availableWidth = resources.displayMetrics.widthPixels -
                    dp(20) - if (count == 2) dp(12) else 0
                val pageWidth = if (count == 2) availableWidth / 2 else availableWidth
                val pageHeight = (pageWidth * ratios[pageIndex]).toInt().coerceAtLeast(dp(180))
                row.addView(image, LinearLayout.LayoutParams(
                    0, pageHeight, 1f
                ).apply {
                    if (count == 2 && it == 0) marginEnd = dp(3)
                    if (count == 2 && it == 1) marginStart = dp(3)
                })
            }
            pages.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) })
            index += count
        }
        scroll.addView(pages)
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> renderVisibleContinuousPages(scroll) }
        pages.post { renderVisibleContinuousPages(scroll) }
        return scroll
    }

    private fun renderVisibleContinuousPages(scroll: ScrollView) {
        if (!continuousMode || destroyed) return
        val viewport = IntArray(2)
        scroll.getLocationOnScreen(viewport)
        val top = viewport[1] - dp(250)
        val bottom = viewport[1] + scroll.height + dp(250)
        var firstVisible = -1

        continuousPageViews.forEachIndexed { index, image ->
            val location = IntArray(2)
            image.getLocationOnScreen(location)
            val visible = location[1] + image.height >= top && location[1] <= bottom
            if (visible && firstVisible == -1) firstVisible = index
            if (visible && image.drawable == null) renderContinuousPage(index, image)
            if (!visible && image.drawable != null) {
                val drawable = image.drawable
                image.setImageDrawable(null)
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap?.let { if (!it.isRecycled) it.recycle() }
                }
            }
        }
        if (firstVisible >= 0) {
            currentPage = firstVisible
            updateCounter()
        }
    }

    private fun renderContinuousPage(index: Int, image: ZoomableImageView) {
        if (!renderingPages.add(index)) return
        renderExecutor.execute {
            var bitmap: Bitmap? = null
            try {
                bitmap = renderBitmap(index, (image.width.takeIf { it > 0 }
                    ?: resources.displayMetrics.widthPixels).coerceIn(500, 1800))
                mainHandler.post {
                    renderingPages.remove(index)
                    if (!destroyed && continuousMode && image.parent != null && bitmap != null) {
                        image.displayBitmap(bitmap)
                        image.setSepia(sepiaEnabled)
                        image.setFitWidth(fitWidthEnabled)
                        (readerContainer as? ScrollView)?.let { renderVisibleContinuousPages(it) }
                    } else {
                        bitmap?.recycle()
                    }
                }
            } catch (_: Exception) {
                mainHandler.post { renderingPages.remove(index) }
            }
        }
    }

    private fun renderBitmap(pageIndex: Int, targetWidth: Int): Bitmap? {
        return synchronized(rendererLock) {
            val pdf = renderer ?: return@synchronized null
            if (pageIndex !in 0 until pdf.pageCount) return@synchronized null
            val page = pdf.openPage(pageIndex)
            try {
                val width = targetWidth.coerceIn(500, 1800)
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                Canvas(result).drawColor(Color.WHITE)
                page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                result
            } finally {
                page.close()
            }
        }
    }

    private fun createControlButton(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            minWidth = dp(88)
            minHeight = dp(48)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(65, 167, 139, 250)),
                android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_selected), roundedBackground(purpleColor, 12))
                    addState(intArrayOf(), roundedBackground(panelColor, 12))
                },
                null
            )
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = label
        }
    }

    private fun annotationKey(page: Int): String =
        "annotations_" + (currentUri?.toString() ?: "unknown") + "_" + page

    private fun updateCounter() {
        if (::pageImage.isInitialized && !continuousMode) {
            pageImage.setAnnotationStorageKey(annotationKey(currentPage))
        }
        val total = renderer?.pageCount ?: 1
        pageCounter.text = "PAGE ${currentPage + 1} / $total"
    }

    private fun changePage(direction: Int) {
        val total = renderer?.pageCount ?: return
        val newPage = currentPage + direction

        if (newPage !in 0 until total) return

        currentPage = newPage
        updateCounter()
        renderPage()
    }

    private fun renderPage() {
        val requestedPage = currentPage

        renderExecutor.execute {
            var bitmap: Bitmap? = null

            try {
                bitmap = synchronized(rendererLock) {
                    val pdf = renderer ?: return@synchronized null

                    if (requestedPage !in 0 until pdf.pageCount) {
                        return@synchronized null
                    }

                    val page = pdf.openPage(requestedPage)

                    try {
                        val targetWidth = (
                            resources.displayMetrics.widthPixels * 2
                        ).coerceIn(1200, 2400)

                        val targetHeight = (
                            targetWidth.toFloat() * page.height / page.width
                        ).toInt().coerceAtLeast(1)

                        val result = Bitmap.createBitmap(
                            targetWidth,
                            targetHeight,
                            Bitmap.Config.ARGB_8888
                        )

                        Canvas(result).drawColor(Color.WHITE)

                        page.render(
                            result,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        result
                    } finally {
                        page.close()
                    }
                }

                mainHandler.post {
                    if (!destroyed &&
                        requestedPage == currentPage &&
                        bitmap != null
                    ) {
                        pageImage.displayBitmap(bitmap)
                        pageImage.setSepia(sepiaEnabled)
                        pageImage.setFitWidth(fitWidthEnabled)
                    } else {
                        bitmap?.recycle()
                    }
                }

            } catch (e: Exception) {
                mainHandler.post {
                    if (!destroyed) {
                        Toast.makeText(
                            this,
                            "Unable to display this page",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        destroyed = true
        renderExecutor.shutdownNow()

        synchronized(rendererLock) {
            renderer?.close()
            renderer = null
            fileDescriptor?.close()
            fileDescriptor = null
        }

        super.onDestroy()
    }

    class ZoomableImageView(
        context: Context,
        private val onPageSwipe: (Int) -> Unit
    ) : ImageView(context) {

        private val matrixValue = Matrix()
        private val displayRect = RectF()

        private var baseScale = 1f
        private var currentScale = 1f
        private var fitWidthMode = false
        private var swipeNavigationEnabled = true

        private var startX = 0f
        private var startY = 0f
        private var lastX = 0f
        private var lastY = 0f

        private var multiTouch = false
        private var velocityTracker: VelocityTracker? = null

        private val swipeThreshold =
            35f * resources.displayMetrics.density

        private val minimumVelocity =
            400f * resources.displayMetrics.density

        private val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val newScale = (currentScale * detector.scaleFactor)
                        .coerceIn(baseScale, baseScale * 5f)

                    val factor = newScale / currentScale

                    matrixValue.postScale(
                        factor,
                        factor,
                        detector.focusX,
                        detector.focusY
                    )

                    currentScale = newScale
                    clampImage()
                    imageMatrix = matrixValue
                    return true
                }
            }
        )

        init {
            scaleType = ScaleType.MATRIX
            setBackgroundColor(Color.rgb(35, 35, 48))
        }

        private var annotationMode: String? = null
        private var annotationStorageKey: String? = null
        private data class SavedStroke(val mode: String, val points: MutableList<PointF>)
        private val savedStrokes = mutableListOf<SavedStroke>()
        private var activeStroke: SavedStroke? = null

        fun setAnnotationStorageKey(key: String) {
            if (annotationStorageKey == key) return
            annotationStorageKey = key
            savedStrokes.clear()
            val raw = context.getSharedPreferences("fileflow_reader", Context.MODE_PRIVATE)
                .getString(key, null) ?: return
            try {
                val array = org.json.JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val pointsArray = item.getJSONArray("points")
                    val points = mutableListOf<PointF>()
                    for (j in 0 until pointsArray.length()) {
                        val p = pointsArray.getJSONObject(j)
                        points.add(PointF(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                    }
                    savedStrokes.add(SavedStroke(item.getString("mode"), points))
                }
            } catch (_: Exception) { }
            invalidate()
        }

        fun setAnnotationMode(mode: String?) {
            annotationMode = mode
            isClickable = mode != null
            invalidate()
        }

        private fun saveAnnotations() {
            val array = org.json.JSONArray()
            savedStrokes.forEach { stroke ->
                val points = org.json.JSONArray()
                stroke.points.forEach { point ->
                    points.put(org.json.JSONObject().put("x", point.x).put("y", point.y))
                }
                array.put(org.json.JSONObject().put("mode", stroke.mode).put("points", points))
            }
            annotationStorageKey?.let { key ->
                context.getSharedPreferences("fileflow_reader", Context.MODE_PRIVATE)
                    .edit().putString(key, array.toString()).apply()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val all = savedStrokes.toMutableList()
            activeStroke?.let { all.add(it) }
            all.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    when (stroke.mode) {
                        "highlight" -> {
                            color = Color.argb(95, 255, 220, 40)
                            strokeWidth = dpLocal(18f)
                        }
                        "underline" -> {
                            color = Color.rgb(255, 90, 90)
                            strokeWidth = dpLocal(2.5f)
                        }
                        else -> {
                            color = Color.rgb(255, 70, 70)
                            strokeWidth = dpLocal(3.5f)
                        }
                    }
                }
                val path = Path()
                stroke.points.forEachIndexed { index, point ->
                    val x = point.x * width
                    val y = point.y * height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
            }
        }

        private fun dpLocal(value: Float): Float = value * resources.displayMetrics.density

        fun displayBitmap(bitmap: Bitmap) {
            setImageBitmap(bitmap)
            applySepiaFilter()
            fitImage()
        }

        fun setSepia(enabled: Boolean) {
            sepiaEnabledForView = enabled
            applySepiaFilter()
        }

        private var sepiaEnabledForView = false

        private fun applySepiaFilter() {
            if (!sepiaEnabledForView) {
                colorFilter = null
                return
            }

            val sepia = ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            colorFilter = ColorMatrixColorFilter(sepia)
        }

        fun setFitWidth(enabled: Boolean) {
            fitWidthMode = enabled
            fitImage()
        }

        fun setSwipeNavigationEnabled(enabled: Boolean) {
            swipeNavigationEnabled = enabled
        }

        override fun onSizeChanged(
            w: Int, h: Int, oldw: Int, oldh: Int
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (drawable != null) fitImage()
        }

        private fun fitImage() {
            val image = drawable ?: return

            val imageWidth = image.intrinsicWidth.toFloat()
            val imageHeight = image.intrinsicHeight.toFloat()

            if (imageWidth <= 0 || imageHeight <= 0 ||
                width <= 0 || height <= 0
            ) return

            baseScale = if (fitWidthMode) {
                width.toFloat() / imageWidth
            } else {
                minOf(
                    width.toFloat() / imageWidth,
                    height.toFloat() / imageHeight
                )
            }

            currentScale = baseScale

            val x = (width - imageWidth * baseScale) / 2f
            val y = (height - imageHeight * baseScale) / 2f

            matrixValue.reset()
            matrixValue.setScale(baseScale, baseScale)
            matrixValue.postTranslate(x, y)

            imageMatrix = matrixValue
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (annotationMode != null && event.pointerCount == 1) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activeStroke = SavedStroke(
                            annotationMode!!,
                            mutableListOf(PointF(
                                (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            ))
                        )
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        activeStroke?.points?.add(PointF(
                            (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                            (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        ))
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        activeStroke?.points?.add(PointF(
                            (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                            (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        ))
                        activeStroke?.let { savedStrokes.add(it) }
                        activeStroke = null
                        saveAnnotations()
                        invalidate()
                        performClick()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        activeStroke = null
                        invalidate()
                        return true
                    }
                }
            }
            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)

                    startX = event.x
                    startY = event.y
                    lastX = event.x
                    lastY = event.y
                    multiTouch = false

                    scaleDetector.onTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    multiTouch = true
                    velocityTracker?.addMovement(event)
                    scaleDetector.onTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    scaleDetector.onTouchEvent(event)

                    if (!scaleDetector.isInProgress &&
                        event.pointerCount == 1 &&
                        currentScale > baseScale * 1.01f
                    ) {
                        matrixValue.postTranslate(
                            event.x - lastX,
                            event.y - lastY
                        )
                        clampImage()
                        imageMatrix = matrixValue
                    }

                    lastX = event.x
                    lastY = event.y
                    return true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    multiTouch = true
                    scaleDetector.onTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)

                    val dx = event.x - startX
                    val dy = event.y - startY
                    val velocityY = velocityTracker?.yVelocity ?: 0f

                    val verticalSwipe = abs(dy) > abs(dx) * 1.2f
                    val longEnough = abs(dy) > swipeThreshold
                    val fastEnough = abs(velocityY) > minimumVelocity

                    if (swipeNavigationEnabled && !multiTouch &&
                        currentScale <= baseScale * 1.01f &&
                        verticalSwipe &&
                        (longEnough || fastEnough)
                    ) {
                        if (dy < 0 || velocityY < -minimumVelocity) {
                            onPageSwipe(1)
                        } else {
                            onPageSwipe(-1)
                        }
                    } else {
                        performClick()
                    }

                    velocityTracker?.recycle()
                    velocityTracker = null
                    scaleDetector.onTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }
            }

            scaleDetector.onTouchEvent(event)
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun clampImage() {
            val image = drawable ?: return

            displayRect.set(
                0f,
                0f,
                image.intrinsicWidth.toFloat(),
                image.intrinsicHeight.toFloat()
            )

            matrixValue.mapRect(displayRect)

            var dx = 0f
            var dy = 0f

            if (displayRect.width() <= width) {
                dx = width / 2f - displayRect.centerX()
            } else if (displayRect.left > 0f) {
                dx = -displayRect.left
            } else if (displayRect.right < width) {
                dx = width - displayRect.right
            }

            if (displayRect.height() <= height) {
                dy = height / 2f - displayRect.centerY()
            } else if (displayRect.top > 0f) {
                dy = -displayRect.top
            } else if (displayRect.bottom < height) {
                dy = height - displayRect.bottom
            }

            matrixValue.postTranslate(dx, dy)
        }
    }
}
