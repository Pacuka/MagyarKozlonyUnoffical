package com.pacuka.magyarkozlony.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Home : Screen("home", "Közlönyök", Icons.Default.Home)
    object Search : Screen("search", "Keresés", Icons.Default.Search)
    object Settings : Screen("settings", "Beállítások", Icons.Default.Settings)
    object PdfViewer : Screen("pdf_viewer/{pdfUrl}/{title}", "Megtekintés") {
        fun createRoute(pdfUrl: String, title: String): String {
            val encodedUrl = Uri.encode(pdfUrl)
            val encodedTitle = Uri.encode(title)
            return "pdf_viewer/$encodedUrl/$encodedTitle"
        }
    }
}
