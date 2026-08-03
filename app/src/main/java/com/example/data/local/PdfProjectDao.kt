package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfProjectDao {
    @Query("SELECT * FROM pdf_projects ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<PdfProjectEntity>>

    @Query("SELECT * FROM pdf_projects WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteProjects(): Flow<List<PdfProjectEntity>>

    @Query("SELECT * FROM pdf_projects WHERE operationType = :type ORDER BY timestamp DESC")
    fun getProjectsByType(type: PdfOperationType): Flow<List<PdfProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: PdfProjectEntity): Long

    @Query("UPDATE pdf_projects SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM pdf_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("DELETE FROM pdf_projects")
    suspend fun clearAll()
}
