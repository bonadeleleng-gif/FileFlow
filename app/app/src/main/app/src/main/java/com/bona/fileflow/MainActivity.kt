package com.bona.fileflow

import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF0B0B12)
private val SurfaceDark = Color(0xFF171722)
private val Purple = Color(0xFF9575FF)
private val Muted = Color(0xFF9292A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FileFlowApp()
        }
    }
}

@Composable
    var selectedDocument by remember {
        mutableStateOf<Uri?>(null)
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedDocument = uri
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            background = Background,
            surface = SurfaceDark,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark
                ) {
                    val tabs = listOf(
                        "Home", "Files", "Tools",
                        "Favorites", "Settings"
                    )
                    val icons = listOf("⌂", "▤", "⚒", "♡", "⚙")

                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Text(
                                    icons[index],
                                    fontSize = 21.sp
                                )
                            },
                            label = { Text(title) }
                        )
                    }
                }
            }
        ) { padding ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Background),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "FILEFLOW",
                                color = Purple,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            )
                            Text(
                                "Your documents, organized.",
                                color = Muted,
                                fontSize = 13.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    SurfaceDark,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("B", color = Purple, fontSize = 20.sp)
                        }
                    }
                }

                item {
                    Column(verticalArrangement =
                        Arrangement.spacedBy(12.dp)) {

                        Text(
                            "Good day!",
                            color = Color.White,
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Everything you need to read, in one place.",
                            color = Muted,
                            fontSize = 14.sp
                        )

                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Search your documents...")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                item {
    item {
    selectedDocument?.let { uri ->
        Text(
            text = "Selected: ${uri.lastPathSegment}",
            color = Purple,
            fontSize = 12.sp
        )
    }
}
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            "＋  Open a document",
            modifier = Modifier.padding(8.dp)
        )
    }
}
                        color = Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                item {
                    val formats = listOf(
                        "PDF" to "📕",
                        "Word" to "📝",
                        "Excel" to "📊",
                        "PowerPoint" to "📽",
                        "EPUB" to "📚"
                    )

                    Column(verticalArrangement =
                        Arrangement.spacedBy(10.dp)) {

                        formats.chunked(3).forEach { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(10.dp)
                            ) {
                                group.forEach { (name, emoji) ->
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = SurfaceDark
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 17.dp),
                                            horizontalAlignment =
                                                Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                emoji,
                                                fontSize = 25.sp
                                            )
                                            Spacer(Modifier.height(7.dp))
                                            Text(
                                                name,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                repeat(3 - group.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent documents",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "See all",
                            color = Purple,
                            fontSize = 13.sp
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceDark
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {
                            Text("📂", fontSize = 42.sp)

                            Spacer(Modifier.height(12.dp))

                            Text(
                                "No documents yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                "Your recently opened files will appear here.",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                item {
                    Text(
                        "POWERED BY FILEFLOW",
                        color = Muted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}
