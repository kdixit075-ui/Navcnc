package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cnc_recipes")
data class CncRecipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val toolName: String,
    val material: String,
    val isMetric: Boolean,
    val cuttingSpeed: Double, // Vc (m/min or SFM)
    val diameter: Double, // D (mm or inches)
    val rpm: Int, // Calculated RPM
    val feedPerTooth: Double, // fz (mm/tooth or IPT)
    val flutes: Int, // Number of teeth
    val feedRate: Double, // Calculated Feed Rate (mm/min or IPM)
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface CncRecipeDao {
    @Query("SELECT * FROM cnc_recipes ORDER BY timestamp DESC")
    fun getAllRecipes(): Flow<List<CncRecipe>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: CncRecipe)

    @Delete
    suspend fun deleteRecipe(recipe: CncRecipe)

    @Query("DELETE FROM cnc_recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Int)
}

@Database(entities = [CncRecipe::class], version = 1, exportSchema = false)
abstract class CncDatabase : RoomDatabase() {
    abstract fun cncRecipeDao(): CncRecipeDao

    companion object {
        @Volatile
        private var INSTANCE: CncDatabase? = null

        fun getDatabase(context: Context): CncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CncDatabase::class.java,
                    "cnc_recipes_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CncRecipeRepository(private val dao: CncRecipeDao) {
    val allRecipes: Flow<List<CncRecipe>> = dao.getAllRecipes()

    suspend fun insert(recipe: CncRecipe) {
        dao.insertRecipe(recipe)
    }

    suspend fun delete(recipe: CncRecipe) {
        dao.deleteRecipe(recipe)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteRecipeById(id)
    }
}
