package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class PdfConverters {
    @TypeConverter
    fun fromOperationType(value: PdfOperationType): String = value.name

    @TypeConverter
    fun toOperationType(value: String): PdfOperationType = try {
        PdfOperationType.valueOf(value)
    } catch (e: Exception) {
        PdfOperationType.MERGE
    }
}

@Database(entities = [PdfProjectEntity::class, RecentFileEntity::class], version = 2, exportSchema = false)
@TypeConverters(PdfConverters::class)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun pdfProjectDao(): PdfProjectDao
    abstract fun recentFileDao(): RecentFileDao

    companion object {
        @Volatile
        private var INSTANCE: PdfDatabase? = null

        fun getInstance(context: Context): PdfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PdfDatabase::class.java,
                    "pdfcraft_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
