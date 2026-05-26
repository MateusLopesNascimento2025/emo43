package com.example.data.repository

import com.example.data.database.DubbingProject
import com.example.data.database.DubbingProjectDao
import com.example.data.database.SubtitleCue
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

class DubbingRepository(private val dao: DubbingProjectDao) {

    val allProjects: Flow<List<DubbingProject>> = dao.getAllProjects()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val cueListType = Types.newParameterizedType(List::class.java, SubtitleCue::class.java)
    private val jsonAdapter = moshi.adapter<List<SubtitleCue>>(cueListType)

    suspend fun getProjectById(id: Int): DubbingProject? {
        return dao.getProjectById(id)
    }

    suspend fun insertProject(project: DubbingProject): Long {
        return dao.insertProject(project)
    }

    suspend fun updateProject(project: DubbingProject) {
        dao.updateProject(project)
    }

    suspend fun deleteProject(project: DubbingProject) {
        dao.deleteProject(project)
    }

    fun serializeCues(cues: List<SubtitleCue>): String {
        return try {
            jsonAdapter.toJson(cues)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun deserializeCues(json: String): List<SubtitleCue> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            jsonAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
