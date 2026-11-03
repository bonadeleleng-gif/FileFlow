
package com.bona.fileflow

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class PdfViewerActivity : Activity() {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0

    private lateinit var imageView: ZoomableImageView
    private lateinit var pageLabel: TextView
    private lateinit var previousButton: TextView
    private lateinit var nextButton: TextView

    private val background = Color.rgb(15, 17, 25)
    private val surface = Color.rgb(29, 32, 44)
    private val purple = Color.rgb(135, 100, 255)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent.getParcelableExtra("pdf_uri")

        if (uri == null) {
            finish()
            return
        }

        try {
            fileDescriptor =
                contentResolver.openFileDescriptor(uri, "r")

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
            setBackgroundColor(background)
        }

        // TOP TOOLBAR

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(background)
        }

        val appTitle = TextView(this).apply {
            text = "FileFlow"
            textSize = 23f
            setTextColor(purple)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val spacer = View(this)

        pageLabel = TextView(this).apply {
            text = "Page 1"
            textSize = 14f
            setTextColor(white)
            setPadding(12, 8, 12, 8)
            background = roundedBackground(surface, 20)
        }

        toolbar.addView(
            appTitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        toolbar.addView(
            spacer,
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        toolbar.addView(pageLabel)

        // PDF VIEWING AREA

        imageView = ZoomableImageView(this).apply {
            setBackgroundColor(Color.rgb(55, 57, 66))
        }

        root.addView(toolbar)

        root.addView(
            imageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // ZOOM CONTROLS

        val zoomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(12, 12, 12, 12)
            setBackgroundColor(background)
        }

        val zoomOut = controlButton("−") {
            imageView.zoomBy(0.8f)
        }

        val fitPage = controlButton("Fit page") {
            imageView.resetZoom()
        }

        val zoomIn = controlButton("+") {
            imageView.zoomBy(1.25f)
        }

        zoomBar.addView(zoomOut)
        zoomBar.addView(fitPage)
        zoomBar.addView(zoomIn)

        root.addView(zoomBar)

        // PAGE NAVIGATION

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 20)
            setBackgroundColor(background)
        }

        previousButton = controlButton("‹  Previous") {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }

        nextButton = controlButton("Next  ›") {
            val count = renderer?.pageCount ?: 0

            if (currentPage < count - 1) {
                currentPage++
                renderPage()
            }
        }

        navigation.addView(
            previousButton,
            LinearLayout.LayoutParams(0, 52, 1f).apply {
                setMargins(4, 0, 8, 0)
            }
        )

        navigation.addView(
            nextButton,
            LinearLayout.LayoutParams(0, 52, 1f).apply {
                setMargins(8, 0, 4, 0)
            }
        )

        root.addView(navigation)

        setContentView(root)
    }

    private fun controlButton(
        label: String,
        action: () -> Unit
    ): TextView {

        return TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(18, 10, 18, 10)
            background = roundedBackground(surface, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun renderPage() {

        val pdf = renderer ?: return

        if (currentPage !in 0 until pdf.pageCount) {
            return
        }

        val page = pdf.openPage(currentPage)

        try {

            // Render at a higher resolution for clearer text.

            val scale = minOf(2f, 1600f / page.width)

            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

            // IMPORTANT: Give the PDF a white background.

            Canvas(bitmap).drawColor(Color.WHITE)

            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            imageView.setImageBitmap(bitmap)

            imageView.resetZoom()

            pageLabel.text =
                "Page ${currentPage + 1} of ${pdf.pageCount}"

            previousButton.alpha =
                if (currentPage > 0) 1f else 0.4f

            nextButton.alpha =
                if (currentPage < pdf.pageCount - 1) 1f else 0.4f

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

class ZoomableImageView(
    context: Context
) : ImageView(context) {

    private val matrixValues = Matrix()

    private var currentScale = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {

                val newScale =
                    currentScale * detector.scaleFactor

                if (newScale in 1f..5f) {

                    matrixValues.postScale(
                        detector.scaleFactor,
                        detector.scaleFactor,
                        detector.focusX,
                        detector.focusY
                    )

                    currentScale = newScale
                    imageMatrix = matrixValues
                }

                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    fun zoomBy(factor: Float) {

        val newScale = currentScale * factor

        if (newScale in 1f..5f) {

            matrixValues.postScale(
                factor,
                factor,
                width / 2f,
                height / 2f
            )

            currentScale = newScale
            imageMatrix = matrixValues
        }
    }

    fun resetZoom() {
        post {
            fitImage()
        }
    }

    private fun fitImage() {

        val drawable = drawable ?: return

        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        if (width <= 0 || height <= 0 ||
            imageWidth <= 0 || imageHeight <= 0
        ) {
            return
        }

        val scale = minOf(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )

        val left = (width - imageWidth * scale) / 2f
        val top = (height - imageHeight * scale) / 2f

        matrixValues.reset()
        matrixValues.setScale(scale, scale)
        matrixValues.postTranslate(left, top)

        currentScale = 1f
        imageMatrix = matrixValues
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitImage()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {

                if (!scaleDetector.isInProgress &&
                    event.pointerCount == 1 &&
                    currentScale > 1f
                ) {

                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    matrixValues.postTranslate(dx, dy)
                    imageMatrix = matrixValues
                }

                lastX = event.x
                lastY = event.y

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                performClick()
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
