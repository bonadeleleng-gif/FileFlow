
package com.bona.fileflow

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
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
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import java.util.concurrent.Executors
import kotlin.math.abs

class PdfViewerActivity : Activity() {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0

    private lateinit var pageImage: ZoomableImageView
    private lateinit var pageCounter: TextView

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

        createViewerInterface()

        val uri = intent.getParcelableExtra<Uri>("pdf_uri")

        if (uri == null) {
            Toast.makeText(this, "PDF file not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            fileDescriptor = contentResolver.openFileDescriptor(uri, "r")

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
            setTextColor(Color.WHITE)
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

        counterContainer.addView(pageCounter)

        pageImage = ZoomableImageView(this) { direction ->
            changePage(direction)
        }

        pageImage.setBackgroundColor(Color.rgb(35, 35, 48))

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

        val gestureHint = TextView(this).apply {
            text = "SWIPE TO TURN PAGES  •  PINCH TO ZOOM"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(10))
        }

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

        root.addView(gestureHint)

        setContentView(root)
    }

    private fun changePage(direction: Int) {

        val totalPages = renderer?.pageCount ?: return

        val newPage = currentPage - direction

        if (newPage !in 0 until totalPages) return

        currentPage = newPage

        pageCounter.text =
            "PAGE ${currentPage + 1} / $totalPages"

        renderPage()
    }

    private fun renderPage() {

        val requestedPage = currentPage

        pageCounter.text =
            "PAGE ${requestedPage + 1} / ${renderer?.pageCount ?: 1}"

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
                            targetWidth.toFloat() *
                                page.height / page.width
                        ).toInt().coerceAtLeast(1)

                        val pageBitmap = Bitmap.createBitmap(
                            targetWidth,
                            targetHeight,
                            Bitmap.Config.ARGB_8888
                        )

                        Canvas(pageBitmap).drawColor(Color.WHITE)

                        page.render(
                            pageBitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        pageBitmap

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

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
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

        private val imageMatrixValue = Matrix()
        private val displayRect = RectF()

        private var baseScale = 1f
        private var currentScale = 1f

        private var lastX = 0f
        private var lastY = 0f
        private var startX = 0f
        private var startY = 0f

        private var hadMultiplePointers = false
        private var velocityTracker: VelocityTracker? = null

        private val swipeThreshold =
            35f * resources.displayMetrics.density

        private val minimumSwipeVelocity =
            450f * resources.displayMetrics.density

        private val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {

                    val newScale = (
                        currentScale * detector.scaleFactor
                    ).coerceIn(baseScale, baseScale * 5f)

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

            // Fit the page to the available screen width.
            // Taller pages can be moved vertically.
            baseScale = width.toFloat() / imageWidth
            currentScale = baseScale

            val scaledHeight = imageHeight * baseScale

            val x = 0f
            val y = if (scaledHeight < height) {
                (height - scaledHeight) / 2f
            } else {
                0f
            }

            imageMatrixValue.reset()
            imageMatrixValue.setScale(baseScale, baseScale)
            imageMatrixValue.postTranslate(x, y)

            imageMatrix = imageMatrixValue
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()

                    velocityTracker?.addMovement(event)

                    startX = event.x
                    startY = event.y
                    lastX = event.x
                    lastY = event.y

                    hadMultiplePointers = false

                    scaleDetector.onTouchEvent(event)

                    return true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    hadMultiplePointers = true
                    velocityTracker?.addMovement(event)
                    scaleDetector.onTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {

                    velocityTracker?.addMovement(event)
                    scaleDetector.onTouchEvent(event)

                    if (!scaleDetector.isInProgress &&
                        event.pointerCount == 1
                    ) {

                        val dx = event.x - lastX
                        val dy = event.y - lastY

                        val zoomed =
                            currentScale > baseScale * 1.01f

                        val image = drawable

                        val pageIsTall =
                            image != null &&
                            image.intrinsicHeight * baseScale > height

                        if (zoomed ||
                            (pageIsTall && abs(dy) > abs(dx))
                        ) {
                            imageMatrixValue.postTranslate(dx, dy)
                            clampImage()
                            imageMatrix = imageMatrixValue
                        }
                    }

                    lastX = event.x
                    lastY = event.y

                    return true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    hadMultiplePointers = true
                    scaleDetector.onTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_UP -> {

                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)

                    val dx = event.x - startX
                    val dy = event.y - startY

                    val velocityX =
                        velocityTracker?.xVelocity ?: 0f

                    val horizontalSwipe =
                        abs(dx) > abs(dy) * 1.2f

                    val longEnough =
                        abs(dx) > swipeThreshold

                    val fastEnough =
                        abs(velocityX) > minimumSwipeVelocity

                    if (!hadMultiplePointers &&
                        currentScale <= baseScale * 1.01f &&
                        horizontalSwipe &&
                        (longEnough || fastEnough)
                    ) {

                        if (dx < 0 || velocityX < -minimumSwipeVelocity) {
                            onPageSwipe(-1)
                        } else {
                            onPageSwipe(1)
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
