
package com.bona.fileflow

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.GestureDetector
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

    private val background = Color.rgb(13, 15, 25)
    private val surface = Color.rgb(29, 32, 46)
    private val purple = Color.rgb(135, 100, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(170, 174, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = background
        window.navigationBarColor = background

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

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(background)
        }

        val appTitle = TextView(this).apply {
            text = "FileFlow"
            textSize = 24f
            setTextColor(purple)
            typeface = Typeface.create(
                "sans-serif",
                Typeface.BOLD
            )
        }

        val subtitle = TextView(this).apply {
            text = "PDF READER"
            textSize = 10f
            setTextColor(muted)
            letterSpacing = 0.12f
            setPadding(10, 5, 0, 0)
        }

        val titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(appTitle)
            addView(subtitle)
        }

        val spacer = View(this)

        pageLabel = TextView(this).apply {
            text = "Page 1"
            textSize = 13f
            setTextColor(white)
            setPadding(14, 9, 14, 9)
            background = roundedBackground(surface, 18f)
        }

        toolbar.addView(titleGroup)

        toolbar.addView(
            spacer,
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        toolbar.addView(pageLabel)

        imageView = ZoomableImageView(this).apply {
            setBackgroundColor(Color.rgb(39, 42, 54))
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

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 20)
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
            textSize = 15f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(18, 10, 18, 10)
            background = roundedBackground(surface, 14f)
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
            val scale = minOf(
                2.5f,
                1800f / page.width.toFloat()
            )

            val bitmapWidth =
                (page.width * scale).toInt().coerceAtLeast(1)

            val bitmapHeight =
                (page.height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(
                bitmapWidth,
                bitmapHeight,
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

            pageLabel.text =
                "Page ${currentPage + 1} / ${pdf.pageCount}"

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
        renderer = null

        fileDescriptor?.close()
        fileDescriptor = null
    }
}

class ZoomableImageView(
    context: Context
) : ImageView(context) {

    private val imageMatrixValues = Matrix()

    private var currentScale = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {

                val desiredScale =
                    currentScale * detector.scaleFactor

                val newScale = desiredScale.coerceIn(1f, 5f)

                val factor = newScale / currentScale

                imageMatrixValues.postScale(
                    factor,
                    factor,
                    detector.focusX,
                    detector.focusY
                )

                currentScale = newScale
                imageMatrix = imageMatrixValues

                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDoubleTap(e: MotionEvent): Boolean {

                if (currentScale > 1f) {
                    fitImage()
                } else {
                    zoomTo(2.5f, e.x, e.y)
                }

                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        isFocusable = true
    }

    private fun zoomTo(
        targetScale: Float,
        x: Float,
        y: Float
    ) {
        val newScale = targetScale.coerceIn(1f, 5f)
        val factor = newScale / currentScale

        imageMatrixValues.postScale(
            factor,
            factor,
            x,
            y
        )

        currentScale = newScale
        imageMatrix = imageMatrixValues
    }

    fun resetZoom() {
        post {
            fitImage()
        }
    }

    private fun fitImage() {

        val image = drawable ?: return

        if (width <= 0 || height <= 0) return

        val imageWidth = image.intrinsicWidth.toFloat()
        val imageHeight = image.intrinsicHeight.toFloat()

        if (imageWidth <= 0 || imageHeight <= 0) return

        val scale = minOf(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )

        val left = (width - imageWidth * scale) / 2f
        val top = (height - imageHeight * scale) / 2f

        imageMatrixValues.reset()
        imageMatrixValues.setScale(scale, scale)
        imageMatrixValues.postTranslate(left, top)

        currentScale = 1f
        imageMatrix = imageMatrixValues
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w > 0 && h > 0) {
            fitImage()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                lastX = 0f
                lastY = 0f
                return true
            }

            MotionEvent.ACTION_MOVE -> {

                if (!scaleDetector.isInProgress &&
                    event.pointerCount == 1 &&
                    currentScale > 1f
                ) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    imageMatrixValues.postTranslate(dx, dy)
                    imageMatrix = imageMatrixValues
                }

                if (event.pointerCount == 1) {
                    lastX = event.x
                    lastY = event.y
                }

                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val remainingIndex =
                    if (event.actionIndex == 0) 1 else 0

                if (remainingIndex < event.pointerCount) {
                    lastX = event.getX(remainingIndex)
                    lastY = event.getY(remainingIndex)
                }

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
