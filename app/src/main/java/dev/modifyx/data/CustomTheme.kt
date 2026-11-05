package dev.modifyx.data

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** A theme the user created on this device: one seed colour, resolved into a full scheme. */
data class CustomTheme(
    val id: String,
    val name: String,
    val seed: Long,
) {
    companion object {
        fun create(name: String, seed: Long): CustomTheme =
            CustomTheme(UUID.randomUUID().toString().take(8), name.trim().ifBlank { "Custom" }, seed)

        fun fromJson(raw: String?): List<CustomTheme> = runCatching {
            if (raw.isNullOrBlank()) return emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                if (id.isBlank()) {
                    null
                } else {
                    CustomTheme(
                        id = id,
                        name = obj.optString("name", id).ifBlank { id },
                        seed = obj.optLong("seed", 0xFF00695C),
                    )
                }
            }
        }.getOrDefault(emptyList())

        fun toJson(themes: List<CustomTheme>): String = JSONArray().apply {
            themes.forEach { theme ->
                put(JSONObject().put("id", theme.id).put("name", theme.name).put("seed", theme.seed))
            }
        }.toString()
    }
}

object ThemeId {
    const val MATERIAL = "material"
    const val DYNAMIC = "dynamic"

    fun custom(id: String): String = "custom:$id"

    fun isCustom(id: String): Boolean = id.startsWith("custom:")
}
