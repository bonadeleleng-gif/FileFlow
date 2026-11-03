
package com.bona.fileflow

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
            setBackgroundColor(Color.rgb(15, 17, 25))
        }

        pageLabel = TextView(this).apply {
            text = "Page 1"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
        }

        imageView = ZoomableImageView(this).apply {
            setBackgroundColor(Color.rgb(55, 57, 66))
        }

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 16)
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

        if (currentPage !in 0 until pdf.pageCount) return

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

            // Paint the PDF page white before rendering its content.
            Canvas(bitmap).drawColor(Color.WHITE)

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

class ZoomableImageView(context: Context) : ImageView(context) {

    private val imageMatrixValues = Matrix()
    private var currentScale = 1f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = currentScale * detector.scaleFactor

                if (newScale in 1f..5f) {
                    imageMatrixValues.postScale(
                        detector.scaleFactor,
                        detector.scaleFactor,
                        detector.focusX,
                        detector.focusY
                    )

                    currentScale = newScale
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

        if (imageWidth <= 0 || imageHeight <= 0 ||
            width <= 0 || height <= 0
        ) return

        val fitScale = minOf(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )

        val left = (width - imageWidth * fitScale) / 2f
        val top = (height - imageHeight * fitScale) / 2f

        imageMatrixValues.setValues(
            floatArrayOf(
                fitScale, 0f, left,
                0f, fitScale, top,
                0f, 0f, 1f
            )
        )

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

            MotionEvent.ACTION_POINTER_DOWN -> {
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

                    imageMatrixValues.postTranslate(dx, dy)
                    imageMatrix = imageMatrixValues
                }

                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                lastX = event.getX(0)
                lastY = event.getY(0)
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
