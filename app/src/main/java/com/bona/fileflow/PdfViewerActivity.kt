package com.bona.fileflow

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import android.view.View
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
    private lateinit var sepiaButton: TextView
    private lateinit var fitButton: TextView
    private lateinit var rotateButton: TextView
    private lateinit var scrollButton: TextView
    private lateinit var spreadButton: TextView
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

        root.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(28)
        ))

        root.addView(counterRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(29)
        ))

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(5))
        }

        sepiaButton = createControlButton("Sepia")
        fitButton = createControlButton("Fit width")
        rotateButton = createControlButton("Auto rotate")
        scrollButton = createControlButton("Continuous")
        spreadButton = createControlButton("Two pages")

        sepiaButton.setOnClickListener {
            sepiaEnabled = !sepiaEnabled
            pageImage.setSepia(sepiaEnabled)
            sepiaButton.text = if (sepiaEnabled) "Normal" else "Sepia"
        }

        fitButton.setOnClickListener {
            fitWidthEnabled = !fitWidthEnabled
            pageImage.setFitWidth(fitWidthEnabled)
            fitButton.text = if (fitWidthEnabled) "Fit page" else "Fit width"
        }

        rotateButton.setOnClickListener {
            orientationLocked = !orientationLocked
            requestedOrientation = if (orientationLocked) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            rotateButton.text = if (orientationLocked) "Unlock rotate" else "Auto rotate"
        }

        scrollButton.setOnClickListener {
            continuousMode = !continuousMode
            if (!continuousMode) twoPageMode = false
            scrollButton.text = if (continuousMode) "Paged" else "Continuous"
            spreadButton.isEnabled = continuousMode
            spreadButton.alpha = if (continuousMode) 1f else 0.55f
            switchReaderContainer()
        }

        spreadButton.setOnClickListener {
            if (!continuousMode) return@setOnClickListener
            twoPageMode = !twoPageMode
            spreadButton.text = if (twoPageMode) "One page" else "Two pages"
            switchReaderContainer()
        }

        controlsRow.addView(sepiaButton, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
            marginEnd = dp(4)
        })
        controlsRow.addView(fitButton, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        controlsRow.addView(rotateButton, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
            marginStart = dp(3)
        })
        controlsRow.addView(scrollButton, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
            marginStart = dp(3)
        })
        controlsRow.addView(spreadButton, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
            marginStart = dp(3)
        })
        spreadButton.isEnabled = false
        spreadButton.alpha = 0.55f

        rootLayout.addView(controlsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(39)
        ))

        readerContainer = imageContainer
        rootLayout.addView(imageContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(rootLayout)
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
    }

    private fun buildContinuousReader(): ScrollView {
        val scroll = ScrollView(this).apply {
            fillViewport = false
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
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(panelColor, 8)
            isClickable = true
            isFocusable = true
        }
    }

    private fun updateCounter() {
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
