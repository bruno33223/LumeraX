package com.lumera.app.data.repository

import android.net.Uri
import com.google.gson.JsonElement
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.stremio.Manifest
import com.lumera.app.data.model.stremio.StreamSubtitle
import com.lumera.app.data.remote.StremioApiService
import com.lumera.app.domain.AddonSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleRepository @Inject constructor(
    private val api: StremioApiService,
    private val dao: AddonDao
) {

    companion object {
        private const val MANIFEST_TIMEOUT_MS = 6_000L
        private const val PER_ADDON_TIMEOUT_MS = 8_000L
    }

    private data class SubtitleResourceRule(
        val types: Set<String> = emptySet(),
        val idPrefixes: Set<String> = emptySet()
    )

    private data class SubtitleRequest(
        val contentType: String,
        val baseId: String,
        val requestId: String
    )

    private sealed interface SubtitleCapability {
        data class Known(val rules: List<SubtitleResourceRule>) : SubtitleCapability
        data object Unknown : SubtitleCapability
    }

    private val subtitleCapabilityCache = ConcurrentHashMap<String, SubtitleCapability>()

    suspend fun getSubtitles(
        type: String,
        playbackId: String,
        videoHash: String? = null,
        videoSize: Long? = null,
        filename: String? = null
    ): List<AddonSubtitle> = withContext(Dispatchers.IO) {
        val request = buildSubtitleRequest(type, playbackId)
        val addons = dao.getAllAddons().firstOrNull()?.filter { it.isEnabled } ?: emptyList()
        if (addons.isEmpty()) return@withContext emptyList()

        val jobs = addons.map { addon ->
            async {
                if (!shouldQueryAddonForSubtitles(addon, request.contentType, request.baseId, videoHash)) {
                    return@async emptyList()
                }
                fetchSubtitlesFromAddon(
                    addon = addon,
                    request = request,
                    videoHash = videoHash,
                    videoSize = videoSize,
                    filename = filename
                )
            }
        }

        jobs.awaitAll()
            .flatten()
            .distinctBy { subtitle ->
                val url = subtitle.url.lowercase(Locale.ROOT)
                val lang = subtitle.lang.orEmpty().lowercase(Locale.ROOT)
                val addon = subtitle.addonName.lowercase(Locale.ROOT)
                "$url|$lang|$addon"
            }
    }

    private fun buildSubtitleRequest(type: String, playbackId: String): SubtitleRequest {
        val normalizedType = type.trim().lowercase(Locale.ROOT)
        val normalizedId = playbackId.trim()

        if (normalizedType == "series") {
            val parts = normalizedId.split(":")
            if (
                parts.size >= 3 &&
                parts[parts.lastIndex - 1].toIntOrNull() != null &&
                parts.last().toIntOrNull() != null
            ) {
                val baseId = parts.dropLast(2).joinToString(":")
                if (baseId.isNotBlank()) {
                    return SubtitleRequest(
                        contentType = normalizedType,
                        baseId = baseId,
                        requestId = normalizedId
                    )
                }
            }
        }

        return SubtitleRequest(
            contentType = normalizedType,
            baseId = normalizedId,
            requestId = normalizedId
        )
    }

    private suspend fun shouldQueryAddonForSubtitles(
        addon: AddonEntity,
        contentType: String,
        baseId: String,
        videoHash: String?
    ): Boolean {
        val capability = getSubtitleCapability(addon.transportUrl)
        return when (capability) {
            SubtitleCapability.Unknown -> true
            is SubtitleCapability.Known -> {
                if (capability.rules.isEmpty()) {
                    false
                } else {
                    capability.rules.any { rule ->
                        val typeSupported = rule.types.isEmpty() || contentType in rule.types
                        val idSupported = rule.idPrefixes.isEmpty() || rule.idPrefixes.any { prefix ->
                            baseId.startsWith(prefix)
                        }
                        // If we have a videoHash, we can query the addon even if the idPrefix doesn't match,
                        // as many subtitle addons can resolve by hash alone.
                        typeSupported && (idSupported || !videoHash.isNullOrBlank())
                    }
                }
            }
        }
    }

    private suspend fun getSubtitleCapability(transportUrl: String): SubtitleCapability {
        subtitleCapabilityCache[transportUrl]?.let { return it }

        val manifestUrl = "${transportUrl.trimEnd('/')}/manifest.json"
        val capability = runCatching {
            val manifest = withTimeout(MANIFEST_TIMEOUT_MS) { api.getManifest(manifestUrl) }
            SubtitleCapability.Known(parseSubtitleResourceRules(manifest))
        }.getOrElse {
            SubtitleCapability.Unknown
        }

        subtitleCapabilityCache[transportUrl] = capability
        return capability
    }

    private fun parseSubtitleResourceRules(manifest: Manifest): List<SubtitleResourceRule> {
        return manifest.resources.orEmpty().mapNotNull { resource ->
            parseSubtitleResourceRule(resource)
        }
    }

    private fun parseSubtitleResourceRule(resource: JsonElement): SubtitleResourceRule? {
        if (resource.isJsonPrimitive && resource.asJsonPrimitive.isString) {
            val resourceName = resource.asString.trim().lowercase(Locale.ROOT)
            if (resourceName == "subtitle" || resourceName == "subtitles") {
                return SubtitleResourceRule()
            }
            return null
        }

        if (!resource.isJsonObject) {
            return null
        }

        val obj = resource.asJsonObject
        val name = obj.get("name")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return null

        if (name != "subtitle" && name != "subtitles") {
            return null
        }

        val types = parseStringSet(obj.get("types"))
        val idPrefixes = parseStringSet(obj.get("idPrefixes"))
        return SubtitleResourceRule(types = types, idPrefixes = idPrefixes)
    }

    private fun parseStringSet(value: JsonElement?): Set<String> {
        if (value == null || !value.isJsonArray) return emptySet()
        return value.asJsonArray.mapNotNull { element ->
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
                null
            } else {
                element.asString.trim().takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
            }
        }.toSet()
    }

    private suspend fun fetchSubtitlesFromAddon(
        addon: AddonEntity,
        request: SubtitleRequest,
        videoHash: String?,
        videoSize: Long?,
        filename: String?
    ): List<AddonSubtitle> {
        val baseUrl = addon.transportUrl.trimEnd('/')
        val pathType = request.contentType
        val pathId = request.requestId
        val extraParams = buildExtraParams(videoHash, videoSize, filename)
        val subtitleUrl = if (extraParams.isNotEmpty()) {
            val encodedExtra = URLEncoder.encode(extraParams, Charsets.UTF_8.name())
            "$baseUrl/subtitles/$pathType/$pathId/$encodedExtra.json"
        } else {
            "$baseUrl/subtitles/$pathType/$pathId.json"
        }

        val response = runCatching {
            withTimeout(PER_ADDON_TIMEOUT_MS) { api.getSubtitles(subtitleUrl) }
        }.getOrNull() ?: return emptyList()

        val addonName = addon.nickname?.takeIf { it.isNotBlank() } ?: addon.name
        return response.subtitles.mapNotNull { subtitle ->
            subtitle.toAddonSubtitle(addonName = addonName, addonBaseUrl = addon.transportUrl)
        }
    }

    private fun StreamSubtitle.toAddonSubtitle(addonName: String, addonBaseUrl: String): AddonSubtitle? {
        val resolvedUrl = resolveSubtitleUrl(url, addonBaseUrl) ?: return null
        val subtitleId = id?.trim()?.takeIf { it.isNotEmpty() } ?: "$addonName:${resolvedUrl.hashCode()}"
        return AddonSubtitle(
            id = subtitleId,
            url = resolvedUrl,
            lang = normalizeLanguageCode(lang),
            addonName = addonName
        )
    }

    private fun resolveSubtitleUrl(rawUrl: String?, addonBaseUrl: String): String? {
        val urlValue = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { Uri.parse(urlValue) }.getOrNull() ?: return null
        if (uri.isAbsolute) {
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            return urlValue
        }

        val base = addonBaseUrl.trimEnd('/')
        val path = urlValue.trimStart('/')
        return "$base/$path"
    }

    private fun normalizeLanguageCode(rawLanguage: String?): String? {
        val normalized = rawLanguage?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.replace('_', '-').lowercase(Locale.ROOT)
    }

    private fun buildExtraParams(
        videoHash: String?,
        videoSize: Long?,
        filename: String?
    ): String {
        val params = mutableListOf<String>()
        videoHash?.trim()?.takeIf { it.isNotEmpty() }?.let {
            params.add("videoHash=$it")
        }
        videoSize?.takeIf { it > 0L }?.let {
            params.add("videoSize=$it")
        }
        filename?.trim()?.takeIf { it.isNotEmpty() }?.let {
            params.add("filename=$it")
        }
        return params.joinToString("&")
    }

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
