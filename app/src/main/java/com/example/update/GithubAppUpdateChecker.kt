package com.example.update

import java.net.HttpURLConnection
import java.net.URL

class GithubAppUpdateChecker(
    private val latestUrl: String = AppUpdateSource.LATEST_URL,
    private val fetch: (String) -> String = ::fetchReleaseJson,
) {
    fun check(currentVersionCode: Int): GithubRelease? {
        val release = runCatching { GithubReleaseJson.parse(fetch(latestUrl)) }.getOrNull() ?: return null
        return release.takeIf { it.versionCode > currentVersionCode }
    }
}

internal fun fetchReleaseJson(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "UnoChess")
    }
    try {
        if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return ""
        if (connection.responseCode !in 200..299) error("GitHub returned ${connection.responseCode}")
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
