package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dubbing_projects")
data class DubbingProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val videoUrl: String,
    val originalText: String, // String concatenated or JSON representational
    val targetText: String,   // String concatenated or JSON representational
    val sourceLanguage: String,
    val targetLanguage: String,
    val tone: String,
    val customPrompt: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Criado",
    val cuesJson: String = "" // List of SubtitleCue serialized as JSON
)

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String
)
