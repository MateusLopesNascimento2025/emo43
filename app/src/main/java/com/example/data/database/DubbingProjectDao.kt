package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DubbingProjectDao {
    @Query("SELECT * FROM dubbing_projects ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<DubbingProject>>

    @Query("SELECT * FROM dubbing_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Int): DubbingProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: DubbingProject): Long

    @Update
    suspend fun updateProject(project: DubbingProject)

    @Delete
    suspend fun deleteProject(project: DubbingProject)
}
