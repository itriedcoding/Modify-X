package dev.modifyx.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Profile(
    val id: String,
    val name: String,
    val packages: List<String>,
    val createdAt: Long,
    val builtIn: Boolean = false,
    val group: CatalogGroup? = null,
)

/**
 * Saved selection sets. Built-in profiles are derived from the catalog and only include
 * packages that are actually installed; user profiles persist to JSON in files dir.
 */
class ProfileStore(context: Context) {

    private val file = File(context.filesDir, "profiles.json")
    private val mutex = Mutex()
    private val _custom = MutableStateFlow<List<Profile>>(emptyList())
    val custom: StateFlow<List<Profile>> = _custom.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            _custom.value = withContext(Dispatchers.IO) { readAll() }
            loaded = true
        }
    }

    suspend fun upsert(profile: Profile) {
        ensureLoaded()
        mutex.withLock {
            val next = _custom.value.filterNot { it.id == profile.id } + profile
            persist(next.sortedBy { it.name.lowercase() })
        }
    }

    suspend fun delete(id: String) {
        ensureLoaded()
        mutex.withLock {
            persist(_custom.value.filterNot { it.id == id })
        }
    }

    suspend fun create(name: String, packages: List<String>): Profile {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name,
            packages = packages.distinct().sorted(),
            createdAt = System.currentTimeMillis(),
        )
        upsert(profile)
        return profile
    }

    fun builtIns(installed: Set<String>): List<Profile> = CatalogGroup.entries.mapNotNull { group ->
        val packages = BloatCatalog.inGroup(group)
            .filter { it.pkg in installed }
            .map { it.pkg }
        if (packages.isEmpty()) return@mapNotNull null
        Profile(
            id = "builtin:${group.name}",
            name = group.label,
            packages = packages.distinct().sorted(),
            createdAt = 0L,
            builtIn = true,
            group = group,
        )
    }

    fun allProfiles(installed: Set<String>): List<Profile> = _custom.value + builtIns(installed)

    private suspend fun persist(next: List<Profile>) {
        _custom.value = next
        withContext(Dispatchers.IO) {
            val array = JSONArray()
            next.forEach { profile ->
                array.put(
                    JSONObject()
                        .put("id", profile.id)
                        .put("name", profile.name)
                        .put("createdAt", profile.createdAt)
                        .put("packages", JSONArray(profile.packages)),
                )
            }
            file.writeText(array.toString())
        }
    }

    private fun readAll(): List<Profile> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val packages = obj.optJSONArray("packages") ?: JSONArray()
                add(
                    Profile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Profile"),
                        packages = buildList {
                            for (pkgIndex in 0 until packages.length()) {
                                packages.optString(pkgIndex)?.takeIf { it.isNotBlank() }?.let(::add)
                            }
                        },
                        createdAt = obj.optLong("createdAt", 0L),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}
