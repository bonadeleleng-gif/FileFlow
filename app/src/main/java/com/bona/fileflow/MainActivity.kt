package com.bona.fileflow

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(15, 15, 28)
    private val accentColor = Color.rgb(124, 92, 255)
    private val textColor = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createHomeScreen()
    }

    private fun createHomeScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(backgroundColor)
            setPadding(32, 48, 32, 32)
        }

        val logo = TextView(this).apply {
            text = "FileFlow"
            textSize = 32f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Your documents. One place."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 48)
        }

        val heading = TextView(this).apply {
            text = "Read your files"
            textSize = 24f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }

        val description = TextView(this).apply {
            text = "Open a PDF document from your device."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val openButton = Button(this).apply {
            text = "Open a PDF"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(accentColor)
            setOnClickListener {
                openPdfPicker()
            }
        }

        val formats = TextView(this).apply {
            text = "PDF reader prototype"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
        }

        root.addView(logo, matchWidth())
        root.addView(subtitle, matchWidth())
        root.addView(heading, matchWidth())
        root.addView(description, matchWidth())
        root.addView(openButton, matchWidth())
        root.addView(formats, matchWidth())

        setContentView(root)
    }

    private fun openPdfPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }

        try {
            startActivityForResult(intent, REQUEST_PDF)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to open the file picker",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Using startActivityForResult for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_PDF && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data

            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // The file picker may not provide a persistable permission.
                }

                val viewerIntent = Intent(
                    this,
                    PdfViewerActivity::class.java
                ).apply {
                    putExtra("pdf_uri", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(viewerIntent)
            }
        }
    }

    private fun matchWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        private const val REQUEST_PDF = 1001
    }
}
