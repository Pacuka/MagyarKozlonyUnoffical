package com.pacuka.magyarkozlony.models

data class Kozlony(
    val year: Int,
    val issue: Int,
    val date: String,
    val title: String,
    val pdfUrl: String, // Keep for backward compatibility or main link
    val documents: List<KozlonyDocument> = emptyList()
)

data class KozlonyDocument(
    val label: String,
    val url: String
)
