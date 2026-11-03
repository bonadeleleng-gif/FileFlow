package com.bona.fileflow

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class PdfViewerActivity : Activity() {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0

    private lateinit var imageView: ImageView
    private lateinit var pageLabel: TextView
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent.getParcelableExtra("pdf_uri")

        if (uri == null) {
            finish()
            return
        }

        try {
            fileDescriptor = contentResolver.openFileDescriptor(uri, "r")

            if (fileDescriptor == null) {
                finish()
                return
            }

            renderer = PdfRenderer(fileDescriptor!!)
            createInterface()
            renderPage()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to open PDF",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 11, 18))
            setPadding(12, 12, 12, 12)
        }

        pageLabel = TextView(this).apply {
            textColor = Color.WHITE
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
        }

        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        val scrollView = ScrollView(this).apply {
            addView(
                imageView,
                ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        previousButton = Button(this).apply {
            text = "Previous"
            setOnClickListener {
                if (currentPage > 0) {
                    currentPage--
                    renderPage()
                }
            }
        }

        nextButton = Button(this).apply {
            text = "Next"
            setOnClickListener {
                val count = renderer?.pageCount ?: 0

                if (currentPage < count - 1) {
                    currentPage++
                    renderPage()
                }
            }
        }

        navigation.addView(previousButton)
        navigation.addView(nextButton)

        root.addView(pageLabel)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(navigation)

        setContentView(root)
    }

    private fun renderPage() {
        val pdf = renderer ?: return

        if (currentPage !in 0 until pdf.pageCount) {
            return
        }

        val page = pdf.openPage(currentPage)

        try {
            val scale = minOf(1.5f, 1200f / page.width)
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            imageView.setImageBitmap(bitmap)
            pageLabel.text = "Page ${currentPage + 1} of ${pdf.pageCount}"

            previousButton.isEnabled = currentPage > 0
            nextButton.isEnabled = currentPage < pdf.pageCount - 1

        } finally {
            page.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.close()
        fileDescriptor?.close()
    }
}
