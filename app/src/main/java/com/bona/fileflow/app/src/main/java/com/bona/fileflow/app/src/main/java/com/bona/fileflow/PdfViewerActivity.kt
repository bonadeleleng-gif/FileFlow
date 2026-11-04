
package com.bona.fileflow

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
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
import android.graphics.drawable.GradientDrawable

class PdfViewerActivity : Activity() {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0

    private lateinit var pageImage: ZoomableImageView
    private lateinit var pageCounter: TextView
    private lateinit var previousButton: TextView
    private lateinit var nextButton: TextView

    private val backgroundColor = Color.rgb(13, 13, 27)
    private val panelColor = Color.rgb(25, 25, 43)
    private val purpleColor = Color.rgb(139, 92, 246)
    private val whiteColor = Color.WHITE
    private val mutedColor = Color.rgb(165, 165, 185)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createViewerInterface()

        val uri = intent.getParcelableExtra<Uri>("pdf_uri")

        if (uri == null) {
            Toast.makeText(this, "PDF file not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            fileDescriptor =
                contentResolver.openFileDescriptor(uri, "r")

            if (fileDescriptor == null) {
                throw Exception("Unable to open PDF")
            }

            renderer = PdfRenderer(fileDescriptor!!)
            renderPage()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to open this PDF",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun createViewerInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(this).apply {
            text = "FileFlow"
            textSize = 23f
            setTextColor(purpleColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val readerLabel = TextView(this).apply {
            text = "  /  READER"
            textSize = 12f
            setTextColor(mutedColor)
            letterSpacing = 0.12f
        }

        header.addView(brand)
        header.addView(readerLabel)

        val readingLabel = TextView(this).apply {
            text = "Reading document"
            textSize = 18f
            setTextColor(whiteColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(25), 0, dp(12))
        }

        pageCounter = TextView(this).apply {
            text = "PAGE 1 / 1"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(purpleColor)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = roundedBackground(panelColor, 30)
        }

        val counterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }

        counterContainer.addView(
            pageCounter,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        pageImage = ZoomableImageView(this).apply {
            setBackgroundColor(Color.rgb(35, 35, 48))
        }

        val imageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(panelColor, 18)
            clipToOutline = true
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

        imageContainer.addView(
            pageImage,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val zoomHint = TextView(this).apply {
            text = "PINCH TO ZOOM  •  DRAG TO MOVE"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(14))
        }

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        previousButton = createNavigationButton("‹  Previous")
        nextButton = createNavigationButton("Next  ›")

        previousButton.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }

        nextButton.setOnClickListener {
            val pdf = renderer
            if (pdf != null && currentPage < pdf.pageCount - 1) {
                currentPage++
                renderPage()
            }
        }

        val buttonParams = LinearLayout.LayoutParams(
            0,
            dp(48),
            1f
        )

        buttonParams.setMargins(dp(5), 0, dp(5), 0)

        navigation.addView(previousButton, buttonParams)
        navigation.addView(nextButton, buttonParams)

        root.addView(header)

        root.addView(readingLabel)

        root.addView(counterContainer)

        root.addView(
            imageContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(zoomHint)

        root.addView(navigation)

        setContentView(root)
    }

    private fun createNavigationButton(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(whiteColor)
            background = roundedBackground(purpleColor, 12)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun renderPage() {

        val pdf = renderer ?: return

        if (currentPage !in 0 until pdf.pageCount) return

        try {
            val page = pdf.openPage(currentPage)

            val targetWidth = (
                resources.displayMetrics.widthPixels * 2
            ).coerceIn(1200, 2400)

            val targetHeight = (
                targetWidth.toFloat() *
                    page.height / page.width
            ).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(
                targetWidth,
                targetHeight,
                Bitmap.Config.ARGB_8888
            )

            Canvas(bitmap).drawColor(Color.WHITE)

            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            page.close()

            pageImage.displayBitmap(bitmap)

            pageCounter.text =
                "PAGE ${currentPage + 1} / ${pdf.pageCount}"

            previousButton.isEnabled = currentPage > 0
            nextButton.isEnabled = currentPage < pdf.pageCount - 1

            previousButton.alpha =
                if (currentPage > 0) 1f else 0.4f

            nextButton.alpha =
                if (currentPage < pdf.pageCount - 1) 1f else 0.4f

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to display this page",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()

        renderer?.close()
        renderer = null

        fileDescriptor?.close()
        fileDescriptor = null
    }

    class ZoomableImageView(context: android.content.Context) :
        ImageView(context) {

        private val imageMatrixValue = Matrix()
        private val displayRect = RectF()

        private var baseScale = 1f
        private var currentScale = 1f

        private var lastX = 0f
        private var lastY = 0f

        private val scaleDetector =
            ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        val newScale =
                            (currentScale * detector.scaleFactor)
                                .coerceIn(baseScale, baseScale * 5f)

                        val factor = newScale / currentScale

                        imageMatrixValue.postScale(
                            factor,
                            factor,
                            detector.focusX,
                            detector.focusY
                        )

                        currentScale = newScale

                        clampImage()

                        imageMatrix = imageMatrixValue

                        return true
                    }
                }
            )

        init {
            scaleType = ScaleType.MATRIX
            setBackgroundColor(Color.rgb(35, 35, 48))
        }

        fun displayBitmap(bitmap: Bitmap) {
            setImageBitmap(bitmap)

            if (width > 0 && height > 0) {
                fitImage()
            }
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int
        ) {
            super.onSizeChanged(w, h, oldw, oldh)

            if (drawable != null) {
                fitImage()
            }
        }

        private fun fitImage() {

            val image = drawable ?: return

            val imageWidth = image.intrinsicWidth.toFloat()
            val imageHeight = image.intrinsicHeight.toFloat()

            if (imageWidth <= 0 || imageHeight <= 0 ||
                width <= 0 || height <= 0
            ) return

            baseScale = minOf(
                width.toFloat() / imageWidth,
                height.toFloat() / imageHeight
            )

            currentScale = baseScale

            val x = (width - imageWidth * baseScale) / 2f
            val y = (height - imageHeight * baseScale) / 2f

            imageMatrixValue.reset()

            imageMatrixValue.setScale(baseScale, baseScale)

            imageMatrixValue.postTranslate(x, y)

            imageMatrix = imageMatrixValue
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
                        currentScale > baseScale * 1.01f
                    ) {
                        val dx = event.x - lastX
                        val dy = event.y - lastY

                        imageMatrixValue.postTranslate(dx, dy)

                        clampImage()

                        imageMatrix = imageMatrixValue
                    }

                    lastX = event.x
                    lastY = event.y

                    return true
                }

                MotionEvent.ACTION_UP -> {
                    performClick()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> return true
            }

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

            imageMatrixValue.mapRect(displayRect)

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

            imageMatrixValue.postTranslate(dx, dy)
        }
    }
}
