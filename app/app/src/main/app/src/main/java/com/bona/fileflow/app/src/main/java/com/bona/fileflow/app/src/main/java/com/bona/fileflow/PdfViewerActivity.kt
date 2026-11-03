package com.bona.fileflow

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.Button
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
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
        }

        imageView = ZoomableImageView(this).apply {
            setBackgroundColor(Color.rgb(25, 25, 35))
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
            imageView,
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
            val scale = minOf(2.5f, 1800f / page.width)

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
            imageView.resetZoom()

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

class ZoomableImageView(
    context: android.content.Context
) : ImageView(context) {

    private val imageMatrix = Matrix()
    private val fitMatrix = Matrix()

    private var currentScale = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = currentScale * detector.scaleFactor

                if (newScale in 1f..5f) {
                    imageMatrix.postScale(
                        detector.scaleFactor,
                        detector.scaleFactor,
                        detector.focusX,
                        detector.focusY
                    )

                    currentScale = newScale
                    setImageMatrix(imageMatrix)
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

        fitMatrix.reset()
        fitMatrix.setScale(scale, scale)
        fitMatrix.postTranslate(left, top)

        imageMatrix.set(fitMatrix)
        currentScale = 1f

        setImageMatrix(imageMatrix)
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
                if (!scaleDetector.isInProgress && currentScale > 1f) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    imageMatrix.postTranslate(dx, dy)
                    setImageMatrix(imageMatrix)
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
