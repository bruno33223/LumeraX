package com.lumera.app.data.repository

import com.google.gson.Gson
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.stremio.CatalogManifest
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.remote.StremioApiService
import com.lumera.app.domain.HomeRow
import com.lumera.app.domain.HubGroupRow
import com.lumera.app.domain.HubItem
import com.lumera.app.domain.HubShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepository @Inject constructor(
    private val api: StremioApiService,
    private val dao: AddonDao
) {
    private val gson = Gson()
    private val MAX_CATALOG_PAGES = 30

    /** Filter out MetaItems where Gson injected null into non-null Kotlin fields */
    private fun List<MetaItem>.sanitize(): List<MetaItem> = filter { item ->
        @Suppress("SENSELESS_COMPARISON")
        item.id != null && item.name != null && item.type != null
    }
    /** Validate a single MetaItem from getMeta() — returns null if Gson injected nulls */
    @Suppress("SENSELESS_COMPARISON")
    private fun MetaItem?.sanitize(): MetaItem? =
        this?.takeIf { it.id != null && it.name != null && it.type != null }
    private val CATALOG_TIMEOUT_MS = 10_000L // 10 seconds per catalog request
    private val STREAM_TIMEOUT_MS = 20_000L  // 20 seconds per stream request (torrent addons need more time)

    /**
     * Fetches a single page of catalog items at the given skip offset.
     * Used for on-demand lazy loading as the user scrolls.
     */
    suspend fun fetchNextCatalogPage(baseUrl: String, skip: Int): List<MetaItem> = withContext(Dispatchers.IO) {
        try {
            val url = if (skip == 0) baseUrl else {
                baseUrl.replace(".json", "/skip=$skip.json")
            }
            withTimeout(CATALOG_TIMEOUT_MS) { api.getCatalog(url) }.metas.orEmpty().sanitize()
        } catch (e: Exception) { emptyList() }
    }


    suspend fun searchMovies(query: String): List<MetaItem> = withContext(Dispatchers.IO) {
        if (query.length < 3) return@withContext emptyList()

        val movieJob = async {
            try {
                withTimeout(CATALOG_TIMEOUT_MS) {
                    api.getCatalog("https://v3-cinemeta.strem.io/catalog/movie/top/search=$query.json")
                }.metas.orEmpty().sanitize()
            } catch (e: Exception) { emptyList() }
        }

        val seriesJob = async {
            try {
                withTimeout(CATALOG_TIMEOUT_MS) {
                    api.getCatalog("https://v3-cinemeta.strem.io/catalog/series/top/search=$query.json")
                }.metas.orEmpty().sanitize()
            } catch (e: Exception) { emptyList() }
        }

        val cinemeta = "https://v3-cinemeta.strem.io"
        return@withContext (movieJob.await() + seriesJob.await())
            .map { it.copy(addonBaseUrl = cinemeta) }
    }

    /**
     * Checks whether a specific catalog in an addon's manifest declares "skip" extra support.
     */
    private fun catalogSupportsSkip(addon: AddonEntity, catalogType: String, catalogId: String): Boolean {
        val catalogs: List<CatalogManifest> = try {
            gson.fromJson(addon.catalogsJson, Array<CatalogManifest>::class.java)?.toList()?.filterNotNull() ?: return false
        } catch (_: Exception) { return false }
        val catalog = catalogs.find { it.type == catalogType && it.id == catalogId } ?: return false
        return catalog.extra?.any { it.name == "skip" } ?: false
    }

    data class DiscoverCatalog(
        val transportUrl: String,
        val addonName: String,
        val type: String,
        val catalogId: String,
        val catalogName: String = "",
        val genres: List<String> = emptyList(),
        val genreKey: String? = null,
        val supportsSkip: Boolean = false
    )

    suspend fun getDiscoverCatalogs(): List<DiscoverCatalog> = withContext(Dispatchers.IO) {
        val addons = dao.getAllAddons().firstOrNull()?.filter { it.isEnabled } ?: emptyList()
        val result = mutableListOf<DiscoverCatalog>()

        for (addon in addons) {
            val catalogs: List<CatalogManifest> = try {
                gson.fromJson(addon.catalogsJson, Array<CatalogManifest>::class.java)?.toList()?.filterNotNull() ?: continue
            } catch (e: Exception) { continue }

            for (catalog in catalogs) {
                // Skip catalogs with null fields injected by Gson
                @Suppress("SENSELESS_COMPARISON")
                if (catalog.name == null || catalog.id == null || catalog.type == null) continue

                // Exclude search-only catalogs (required search extra)
                val hasRequiredSearch = catalog.extra?.any { it.name == "search" && it.isRequired } ?: false
                if (hasRequiredSearch) continue

                val genreExtra = catalog.extra?.firstOrNull { !it.options.isNullOrEmpty() && it.name != "search" && it.name != "skip" }
                val genres = genreExtra?.options ?: emptyList()
                val genreKey = genreExtra?.name

                val supportsSkip = catalog.extra?.any { it.name == "skip" } ?: false

                result.add(DiscoverCatalog(
                    transportUrl = addon.transportUrl,
                    addonName = addon.nickname ?: addon.name,
                    type = catalog.type,
                    catalogId = catalog.id,
                    catalogName = catalog.name,
                    genres = genres,
                    genreKey = genreKey,
                    supportsSkip = supportsSkip
                ))
            }
        }
        result
    }

    suspend fun fetchDiscoverPage(
        transportUrl: String,
        type: String,
        catalogId: String,
        genreKey: String? = null,
        genre: String? = null,
        skip: Int = 0
    ): List<MetaItem> = withContext(Dispatchers.IO) {
        // Build extras as a single path segment joined by '&', matching Stremio protocol
        val extras = mutableListOf<String>()
        val key = genreKey ?: "genre"
        if (!genre.isNullOrEmpty()) extras.add("$key=${java.net.URLEncoder.encode(genre, "UTF-8")}")
        if (skip > 0) extras.add("skip=$skip")

        val url = if (extras.isEmpty()) {
            "$transportUrl/catalog/$type/$catalogId.json"
        } else {
            "$transportUrl/catalog/$type/$catalogId/${extras.joinToString("&")}.json"
        }

        try {
            withTimeout(CATALOG_TIMEOUT_MS) { api.getCatalog(url) }.metas.orEmpty().sanitize()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getDashboardRows(
        screen: String,
        skipConfigs: Int = 0,
        maxConfigs: Int = Int.MAX_VALUE,
        catalogTimeoutMs: Long = CATALOG_TIMEOUT_MS
    ): List<HomeRow> = withContext(Dispatchers.IO) {
        val addons = dao.getAllAddons().firstOrNull()?.filter { it.isEnabled } ?: emptyList()
        val configs = dao.getAllCatalogConfigs().firstOrNull() ?: emptyList()
        val addonMap = addons.associateBy { it.transportUrl }

        val filteredConfigs = configs
            .filter { config ->
                when(screen) {
                    "home" -> config.showInHome
                    "movies" -> config.showInMovies
                    "series" -> config.showInSeries
                    else -> false
                }
            }
            .sortedBy { config ->
                when(screen) {
                    "home" -> config.homeOrder
                    "movies" -> config.moviesOrder
                    "series" -> config.seriesOrder
                    else -> 0
                }
            }
            .drop(skipConfigs.coerceAtLeast(0))
            .let { sliced ->
                if (maxConfigs == Int.MAX_VALUE) sliced else sliced.take(maxConfigs.coerceAtLeast(0))
            }

        val deferredJobs = filteredConfigs.mapNotNull { config ->
            val addon = addonMap[config.transportUrl] ?: return@mapNotNull null
            async {
                try {
                    val url = "${config.transportUrl}/catalog/${config.catalogType}/${config.catalogId}.json"
                    // Fetch only the first page for fast initial load
                    val rawMetas = try { withTimeout(catalogTimeoutMs) { api.getCatalog(url) }.metas.orEmpty().sanitize() } catch (e: Exception) { emptyList() }
                    if (rawMetas.isNotEmpty()) {
                        val metas = rawMetas.map { it.copy(addonBaseUrl = config.transportUrl) }
                        val typeSuffix = config.catalogType.replaceFirstChar { it.uppercase() }
                        val defaultTitle = if (config.catalogName != null) "${config.catalogName} - ${typeSuffix}" else "${config.addonName} - ${config.catalogId.replaceFirstChar { it.uppercase() }}"
                        val finalTitle = config.customTitle ?: defaultTitle
                        HomeRow(
                            configId = config.uniqueId,
                            title = finalTitle,
                            items = metas,
                            catalogUrl = url,
                            isInfiniteLoopEnabled = config.isInfiniteLoopEnabled,
                            visibleItemCount = config.visibleItemCount,
                            isInfiniteScrollingEnabled = config.isInfiniteScrollingEnabled,
                            order = when(screen) {
                                "home" -> config.homeOrder
                                "movies" -> config.moviesOrder
                                "series" -> config.seriesOrder
                                else -> 999
                            },
                            supportsSkip = catalogSupportsSkip(addon, config.catalogType, config.catalogId)
                        )
                    } else null
                } catch (e: Exception) { null }
            }
        }
        deferredJobs.awaitAll().filterNotNull()
    }

    /**
     * Fast single-page category fetch for lightweight surfaces like Hero Carousel.
     * Avoids paginating through the full catalog when only a handful of items are needed.
     */
    suspend fun getCategoryRowPreview(
        configId: String,
        maxItems: Int,
        timeoutMs: Long = CATALOG_TIMEOUT_MS
    ): HomeRow? = withContext(Dispatchers.IO) {
        val config = dao.getCatalogConfig(configId) ?: return@withContext null
        val addon = dao.getAddon(config.transportUrl) ?: return@withContext null
        if (!addon.isEnabled) return@withContext null

        try {
            val url = "${config.transportUrl}/catalog/${config.catalogType}/${config.catalogId}.json"
            val metas = try {
                withTimeout(timeoutMs) { api.getCatalog(url) }.metas.orEmpty().sanitize()
            } catch (_: Exception) {
                emptyList()
            }.take(maxItems.coerceAtLeast(1))

            if (metas.isNotEmpty()) {
                val typeSuffix = config.catalogType.replaceFirstChar { it.uppercase() }
                val defaultTitle = if (config.catalogName != null) "${config.catalogName} - ${typeSuffix}" else "${config.addonName} - ${config.catalogId.replaceFirstChar { it.uppercase() }}"
                val finalTitle = config.customTitle ?: defaultTitle
                HomeRow(
                    configId = config.uniqueId,
                    title = finalTitle,
                    items = metas,
                    catalogUrl = url,
                    isInfiniteLoopEnabled = config.isInfiniteLoopEnabled,
                    visibleItemCount = config.visibleItemCount,
                    isInfiniteScrollingEnabled = config.isInfiniteScrollingEnabled,
                    order = 999,
                    supportsSkip = catalogSupportsSkip(addon, config.catalogType, config.catalogId)
                )
            } else null
        } catch (_: Exception) { null }
    }

    suspend fun getHubRows(screen: String = "home"): List<HubGroupRow> = withContext(Dispatchers.IO) {
        val hubRows = dao.getAllHubRows().firstOrNull() ?: emptyList()
        val allItems = dao.getAllHubRowItems().firstOrNull() ?: emptyList()
        val itemsByRow = allItems.groupBy { it.hubRowId }

        hubRows
            .filter { row ->
                when(screen) {
                    "home" -> row.showInHome
                    "movies" -> row.showInMovies
                    "series" -> row.showInSeries
                    else -> false
                }
            }
            .sortedBy { row ->
                when(screen) {
                    "home" -> row.homeOrder
                    "movies" -> row.moviesOrder
                    "series" -> row.seriesOrder
                    else -> 0
                }
            }
            .map { row ->
                val items = (itemsByRow[row.id] ?: emptyList())
                    .sortedBy { it.itemOrder }
                    .map { item ->
                        HubItem(
                            id = "${row.id}:${item.configUniqueId}",
                            title = item.title,
                            categoryId = item.configUniqueId,
                            customImageUrl = item.customImageUrl
                        )
                    }
                HubGroupRow(
                    id = row.id,
                    title = row.title,
                    items = items,
                    shape = try { HubShape.valueOf(row.shape) } catch (_: Exception) { HubShape.HORIZONTAL },
                    order = when(screen) {
                        "home" -> row.homeOrder
                        "movies" -> row.moviesOrder
                        "series" -> row.seriesOrder
                        else -> 0
                    }
                )
            }
    }

    suspend fun getStreams(type: String, id: String): List<Stream> = withContext(Dispatchers.IO) {
        val addons = dao.getAllAddons().firstOrNull()
            ?.filter { it.isEnabled && it.supportsStream }
            ?: emptyList()

        val jobs = addons.map { addon ->
            async {
                try {
                    val url = "${addon.transportUrl}/stream/$type/$id.json"
                    val response = withTimeout(STREAM_TIMEOUT_MS) { api.getStreams(url) }
                    val sourceLabel = addon.nickname ?: addon.name
                    response.streams.orEmpty().map { stream ->
                        stream.copy(
                            name = "[$sourceLabel] ${stream.name ?: ""}".trim(),
                            addonTransportUrl = addon.transportUrl
                        )
                    }
                } catch (e: Exception) { emptyList<Stream>() }
            }
        }

        jobs.awaitAll().flatten()
    }

    suspend fun getAddonSortOrders(): Map<String, Int> = withContext(Dispatchers.IO) {
        dao.getAllAddons().firstOrNull()
            ?.associate { it.transportUrl to it.sortOrder }
            ?: emptyMap()
    }

    suspend fun installAddonWithConfig(url: String, home: Boolean, movies: Boolean, series: Boolean) = withContext(Dispatchers.IO) {
        val manifest = api.getManifest(url)
        val transportUrl = url.removeSuffix("/manifest.json")
        val catalogsJson = gson.toJson(manifest.catalogs.orEmpty())
        val supportsMeta = manifest.resources?.any { element ->
            when {
                element.isJsonPrimitive -> element.asString == "meta"
                element.isJsonObject -> element.asJsonObject.get("name")?.asString == "meta"
                else -> false
            }
        } ?: false
        val supportsStream = manifest.resources?.any { element ->
            when {
                element.isJsonPrimitive -> element.asString == "stream"
                element.isJsonObject -> element.asJsonObject.get("name")?.asString == "stream"
                else -> false
            }
        } ?: false

        val entity = AddonEntity(
            transportUrl = transportUrl, id = manifest.id, name = manifest.name, version = manifest.version,
            description = manifest.description, iconUrl = manifest.logo, isTrusted = false, isEnabled = true,
            nickname = null, catalogsJson = catalogsJson,
            supportsMeta = supportsMeta,
            supportsStream = supportsStream,
            typesJson = gson.toJson(manifest.types.orEmpty()),
            idPrefixesJson = gson.toJson(manifest.idPrefixes.orEmpty())
        )
        dao.insertAddon(entity)

        val newConfigs = manifest.catalogs.orEmpty().map { catalog ->
            val uniqueId = "${transportUrl}/${catalog.type}/${catalog.id}"
            val isMovieCat = catalog.type == "movie"
            val isSeriesCat = catalog.type == "series"
            CatalogConfigEntity(
                uniqueId = uniqueId, transportUrl = transportUrl, addonName = manifest.name,
                catalogType = catalog.type, catalogId = catalog.id,
                catalogName = catalog.name, customTitle = null,
                showInHome = home, showInMovies = movies && isMovieCat, showInSeries = series && isSeriesCat,
                homeOrder = 999, moviesOrder = 999, seriesOrder = 999
            )
        }
        dao.saveCatalogConfigs(newConfigs)
    }

    suspend fun installAddon(url: String) = installAddonWithConfig(url, true, true, true)

    suspend fun renameAddon(transportUrl: String, newName: String) = withContext(Dispatchers.IO) {
        val addons = dao.getAllAddons().firstOrNull()
        val target = addons?.find { it.transportUrl == transportUrl }
        if (target != null) dao.insertAddon(target.copy(nickname = newName))
    }

    suspend fun deleteAddon(transportUrl: String) = withContext(Dispatchers.IO) {
        dao.deleteCatalogConfigs(transportUrl)
        dao.deleteAddonByUrl(transportUrl)
    }

    suspend fun fetchManifest(url: String) = withContext(Dispatchers.IO) { api.getManifest(url) }

    fun getAddons() = dao.getAllAddons()
    suspend fun updateAddons(addons: List<AddonEntity>) = withContext(Dispatchers.IO) { dao.insertAddons(addons) }

    suspend fun getMetaDetails(url: String) = withContext(Dispatchers.IO) { api.getMeta(url).meta.sanitize() }

    /**
     * Resolves meta details using a priority system:
     * 1. Try the preferred addon (the one the catalog item came from) first
     * 2. Fall back to all meta addons with type-based priority ordering
     * 3. Last resort: Cinemeta for standard types
     */
    suspend fun resolveMetaDetails(
        type: String,
        id: String,
        preferredAddonBaseUrl: String? = null
    ): MetaItem? = withContext(Dispatchers.IO) {
        val allAddons = dao.getAllAddons().firstOrNull()?.filter { it.isEnabled } ?: emptyList()
        val preferredTimeout = 5_000L

        // 1) Try preferred addon first (the addon the catalog item came from).
        // Don't check supportsMeta flag — it may be stale from older DB migrations.
        // If the addon can't serve meta, the request fails fast and we fall through.
        if (!preferredAddonBaseUrl.isNullOrBlank()) {
            try {
                val url = "${preferredAddonBaseUrl.trimEnd('/')}/meta/$type/$id.json"
                val meta = withTimeout(preferredTimeout) { api.getMeta(url) }.meta.sanitize()
                if (meta != null) return@withContext meta
            } catch (_: Exception) { /* try fallback */ }
        }

        // 2) Priority-based fallback across all meta addons
        val addons = allAddons.filter { it.supportsMeta }

        val requestedType = type.trim()
        val inferredType = inferCanonicalType(requestedType, id)

        // Build prioritized candidate list (LinkedHashSet preserves insertion order, deduplicates)
        val candidates = linkedSetOf<Pair<AddonEntity, String>>()

        // Priority 1: Addons that explicitly support the requested type
        for (addon in addons) {
            if (addon.supportsMetaType(requestedType)) {
                candidates.add(addon to requestedType)
            }
        }

        // Priority 2: Addons that support the inferred canonical type (for custom catalog types)
        if (!inferredType.equals(requestedType, ignoreCase = true)) {
            for (addon in addons) {
                if (addon.supportsMetaType(inferredType)) {
                    candidates.add(addon to inferredType)
                }
            }
        }

        // Priority 3: First meta addon as ultimate addon fallback
        addons.firstOrNull()?.let { fallback ->
            val fallbackType = when {
                fallback.supportsMetaType(requestedType) -> requestedType
                fallback.supportsMetaType(inferredType) -> inferredType
                else -> inferredType.ifBlank { requestedType }
            }
            candidates.add(fallback to fallbackType)
        }

        // Try each candidate, skip the preferred addon (already tried above).
        // Validate that returned meta ID matches the request — addons may return
        // meta with a different ID (e.g. tmdb:12345 instead of tt1234567), which causes
        // the details screen to appear stuck since it checks movie.id == requestedId.
        for ((addon, candidateType) in candidates) {
            if (addon.transportUrl == preferredAddonBaseUrl) continue
            try {
                val url = "${addon.transportUrl}/meta/$candidateType/$id.json"
                val meta = withTimeout(CATALOG_TIMEOUT_MS) { api.getMeta(url) }.meta.sanitize()
                if (meta != null && meta.id == id) return@withContext meta
            } catch (_: Exception) { /* try next */ }
        }

        // Last resort: Cinemeta for standard types
        try {
            val url = "https://v3-cinemeta.strem.io/meta/$type/$id.json"
            return@withContext withTimeout(CATALOG_TIMEOUT_MS) { api.getMeta(url) }.meta.sanitize()
        } catch (_: Exception) { null }
    }

    private fun AddonEntity.supportsMetaType(type: String): Boolean {
        if (!supportsMeta) return false
        val types: List<String> = try {
            gson.fromJson(typesJson, Array<String>::class.java)?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
        if (types.isEmpty()) return true
        return types.any { it.equals(type, ignoreCase = true) }
    }

    private fun inferCanonicalType(type: String, id: String): String {
        val known = setOf("movie", "series", "tv", "channel", "anime")
        if (type.lowercase() in known) return type
        val normalizedId = id.lowercase()
        return when {
            ":movie:" in normalizedId -> "movie"
            ":series:" in normalizedId -> "series"
            ":tv:" in normalizedId -> "tv"
            ":anime:" in normalizedId -> "anime"
            else -> type
        }
    }
}
