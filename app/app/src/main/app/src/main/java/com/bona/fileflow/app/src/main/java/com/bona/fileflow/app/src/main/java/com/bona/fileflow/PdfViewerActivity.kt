

package com.bona.fileflow

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
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

    private val background = Color.rgb(12, 14, 25)
    private val surface = Color.rgb(27, 30, 47)
    private val purple = Color.rgb(139, 105, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(165, 169, 190)

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
            setBackgroundColor(background)
            setPadding(18, 12, 18, 12)
        }

        // Top header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 12, 4, 20)
        }

        val brand = TextView(this).apply {
            text = "FileFlow"
            textSize = 25f
            setTextColor(purple)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "  /  READER"
            textSize = 11f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
        }

        header.addView(brand)
        header.addView(subtitle)
        root.addView(header)

        // Page information
        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2, 0, 2, 14)
        }

        val readingTitle = TextView(this).apply {
            text = "Reading document"
            textSize = 15f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        }

        pageLabel = TextView(this).apply {
            text = "PAGE 1"
            textSize = 11f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(14, 8, 14, 8)
            background = roundedBackground(surface, 30f)
        }

        infoRow.addView(
            readingTitle,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        infoRow.addView(pageLabel)
        root.addView(infoRow)

        // Document viewing area
        imageView = ZoomableImageView(this).apply {
            setBackgroundColor(Color.rgb(38, 41, 57))
        }

        root.addView(
            imageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, 0, 0, 18)
            }
        )

        // Bottom navigation
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
        }

        previousButton = navigationButton("‹   Previous")
        nextButton = navigationButton("Next   ›")

        previousButton.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }

        nextButton.setOnClickListener {
            val count = renderer?.pageCount ?: 0
            if (currentPage < count - 1) {
                currentPage++
                renderPage()
            }
        }

        navigation.addView(
            previousButton,
            LinearLayout.LayoutParams(0, 54, 1f).apply {
                setMargins(0, 0, 7, 0)
            }
        )

        navigation.addView(
            nextButton,
            LinearLayout.LayoutParams(0, 54, 1f).apply {
                setMargins(7, 0, 0, 0)
            }
        )

        root.addView(navigation)

        val hint = TextView(this).apply {
            text = "PINCH TO ZOOM  •  DRAG TO MOVE"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 4)
            letterSpacing = 0.12f
        }

        root.addView(hint)
        setContentView(root)
    }

    private fun navigationButton(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(white)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBackground(surface, 16f)
            isClickable = true
            isFocusable = true
        }
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun renderPage() {
        val pdf = renderer ?: return

        if (currentPage !in 0 until pdf.pageCount) return

        val page = pdf.openPage(currentPage)

        try {
            val scale = minOf(2.5f, 1800f / page.width)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

            Canvas(bitmap).drawColor(Color.WHITE)

            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            imageView.setImageBitmap(bitmap)
            imageView.resetZoom()

            pageLabel.text = "PAGE ${currentPage + 1} / ${pdf.pageCount}"

            previousButton.isEnabled = currentPage > 0
            nextButton.isEnabled = currentPage < pdf.pageCount - 1

            previousButton.alpha = if (currentPage > 0) 1f else 0.4f
            nextButton.alpha = if (currentPage < pdf.pageCount - 1) 1f else 0.4f

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

class ZoomableImageView(context: Context) : ImageView(context) {

    private val imageMatrixValues = Matrix()
    private var currentZoom = 1f
    private var baseScale = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newZoom = (currentZoom * detector.scaleFactor)
                    .coerceIn(1f, 5f)

                val factor = newZoom / currentZoom

                if (factor != 1f) {
                    imageMatrixValues.postScale(
                        factor,
                        factor,
                        detector.focusX,
                        detector.focusY
                    )

                    currentZoom = newZoom
                    clampImagePosition()
                    imageMatrix = imageMatrixValues
                }

                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
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

        if (imageWidth <= 0f || imageHeight <= 0f ||
            width <= 0 || height <= 0
        ) return

        baseScale = minOf(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )

        val left = (width - imageWidth * baseScale) / 2f
        val top = (height - imageHeight * baseScale) / 2f

        imageMatrixValues.setValues(
            floatArrayOf(
                baseScale, 0f, left,
                0f, baseScale, top,
                0f, 0f, 1f
            )
        )

        currentZoom = 1f
        imageMatrix = imageMatrixValues
    }

    private fun clampImagePosition() {
        val drawable = drawable ?: return

        val values = FloatArray(9)
        imageMatrixValues.getValues(values)

        val scale = values[Matrix.MSCALE_X]
        val scaledWidth = drawable.intrinsicWidth * scale
        val scaledHeight = drawable.intrinsicHeight * scale

        val minX = if (scaledWidth <= width) {
            (width - scaledWidth) / 2f
        } else {
            width - scaledWidth
        }

        val maxX = if (scaledWidth <= width) {
            minX
        } else {
            0f
        }

        val minY = if (scaledHeight <= height) {
            (height - scaledHeight) / 2f
        } else {
            height - scaledHeight
        }

        val maxY = if (scaledHeight <= height) {
            minY
        } else {
            0f
        }

        val correctedX = values[Matrix.MTRANS_X].coerceIn(minX, maxX)
        val correctedY = values[Matrix.MTRANS_Y].coerceIn(minY, maxY)

        imageMatrixValues.setValues(
            floatArrayOf(
                scale, 0f, correctedX,
                0f, scale, correctedY,
                0f, 0f, 1f
            )
        )
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
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                lastX = event.getX(0)
                lastY = event.getY(0)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress &&
                    event.pointerCount == 1 &&
                    currentZoom > 1.01f
                ) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    imageMatrixValues.postTranslate(dx, dy)
                    clampImagePosition()
                    imageMatrix = imageMatrixValues
                }

                if (event.pointerCount == 1) {
                    lastX = event.x
                    lastY = event.y
                }

                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val remainingIndex = if (event.actionIndex == 0) 1 else 0

                if (remainingIndex < event.pointerCount) {
                    lastX = event.getX(remainingIndex)
                    lastY = event.getY(remainingIndex)
                }

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
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
