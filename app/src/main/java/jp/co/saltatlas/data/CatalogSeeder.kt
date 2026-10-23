package jp.co.saltatlas.data

import android.content.Context
import org.json.JSONObject

/** JSON in assets is the canonical source for catalog rows. User collection rows are never touched. */
object CatalogSeeder {
    suspend fun synchronize(context: Context, dao: SaltDao) {
        val root = context.assets.open("salts.json").bufferedReader().use { JSONObject(it.readText()) }
        require(root.optInt("schemaVersion") == 1) { "Unsupported catalog schema" }
        val items = root.getJSONArray("items")
        val rows = (0 until items.length()).map { items.getJSONObject(it).toSalt() }
        require(rows.map { it.id }.distinct().size == rows.size) { "Duplicate salt IDs" }
        require(rows.all { it.id.isNotBlank() && it.name.isNotBlank() && it.sourceUrl.isNotBlank() }) { "Catalog item is missing required fields" }
        require(rows.all { (it.latitude == null) == (it.longitude == null) }) { "Coordinates must be supplied as a pair" }
        require(rows.all { it.latitude == null || (it.latitude in -90.0..90.0 && it.longitude!! in -180.0..180.0) }) { "Invalid coordinates" }
        dao.insertSalts(rows)
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().removeSuffix(" mg").takeIf { it.isNotBlank() }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONObject.toSalt() = SaltEntity(
        id = getString("id"), name = getString("name"), maker = optString("maker"), region = optString("region"),
        method = optString("method"), ingredients = optString("ingredients"), description = optString("description", optString("note")),
        sodiumMg100g = stringOrNull("sodiumMg100g"), magnesiumMg100g = stringOrNull("magnesiumMg100g"),
        potassiumMg100g = stringOrNull("potassiumMg100g"), calciumMg100g = stringOrNull("calciumMg100g"),
        sourceUrl = getString("sourceUrl"), lastChecked = optString("lastChecked", "未確認"),
        microplasticsStatus = optString("microplasticsStatus", "公表データなし"), latitude = doubleOrNull("latitude"),
        longitude = doubleOrNull("longitude"), manufacturingLocation = stringOrNull("manufacturingLocation"),
        rawMaterialLocation = stringOrNull("rawMaterialLocation"), locationSourceUrl = stringOrNull("locationSourceUrl")
    )
}
