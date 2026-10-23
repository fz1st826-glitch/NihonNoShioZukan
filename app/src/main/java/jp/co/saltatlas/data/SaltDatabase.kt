package jp.co.saltatlas.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "salts")
data class SaltEntity(
    @PrimaryKey val id: String,
    val name: String,
    val maker: String,
    val region: String,
    val method: String,
    val ingredients: String,
    val description: String,
    val sodiumMg100g: String?,
    val magnesiumMg100g: String?,
    val potassiumMg100g: String?,
    val calciumMg100g: String?,
    val sourceUrl: String,
    val lastChecked: String,
    val microplasticsStatus: String = "公表データなし",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val manufacturingLocation: String? = null,
    val rawMaterialLocation: String? = null,
    val locationSourceUrl: String? = null
)

@Entity(tableName = "my_salts")
data class MySaltEntity(@PrimaryKey val saltId: String, val isFavorite: Boolean = false, val purchased: Boolean = false, val tasted: Boolean = false, val rating: Int = 0, val note: String = "")

@Dao interface SaltDao {
    @Query("SELECT * FROM salts ORDER BY name") fun all(): Flow<List<SaltEntity>>
    @Query("SELECT * FROM salts WHERE name LIKE '%' || :q || '%' OR region LIKE '%' || :q || '%' OR method LIKE '%' || :q || '%' OR maker LIKE '%' || :q || '%' ORDER BY name") fun search(q: String): Flow<List<SaltEntity>>
    @Query("SELECT * FROM salts WHERE id = :id") suspend fun byId(id: String): SaltEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSalts(rows: List<SaltEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMine(row: MySaltEntity)
    @Query("SELECT * FROM my_salts WHERE saltId = :id") suspend fun mineById(id: String): MySaltEntity?
    @Transaction suspend fun updateMine(id: String, favorite: Boolean? = null, purchased: Boolean? = null, tasted: Boolean? = null, rating: Int? = null, note: String? = null) {
        val old = mineById(id) ?: MySaltEntity(id)
        putMine(old.copy(isFavorite = favorite ?: old.isFavorite, purchased = purchased ?: old.purchased,
            tasted = tasted ?: old.tasted, rating = (rating ?: old.rating).coerceIn(0, 5), note = note ?: old.note))
    }
    @Query("SELECT * FROM my_salts") fun mine(): Flow<List<MySaltEntity>>
}

@Database(entities = [SaltEntity::class, MySaltEntity::class], version = 1, exportSchema = false)
abstract class SaltDatabase : RoomDatabase() {
    abstract fun saltDao(): SaltDao
    companion object {
        @Volatile private var instance: SaltDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context, SaltDatabase::class.java, "salt-atlas.db").build().also { instance = it } }
    }
}
