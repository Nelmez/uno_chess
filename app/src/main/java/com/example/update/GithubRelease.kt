package com.example.update

data class GithubRelease(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
)

object AppUpdateSource {
    const val REPO = "Nelmez/uno_chess"
    const val ASSET_NAME = "unochess.apk"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
}

/**
 * Reads a GitHub release JSON document. The published tag is `v` plus the Android
 * versionCode (`v2`), and the installable file is the `unochess.apk` asset.
 */
internal object GithubReleaseJson {
    fun parse(json: String, assetName: String = AppUpdateSource.ASSET_NAME): GithubRelease? {
        if (json.isBlank()) return null
        val tag = rootString(json, "tag_name") ?: return null
        val raw = tag.trim().removePrefix("v").removePrefix("V")
        val versionCode = raw.toIntOrNull()
            ?: when (raw) {
                "1.1.1" -> 3
                "1.3" -> 4
                else -> null
            }
            ?: return null
        if (versionCode <= 0) return null
        val versionName = rootString(json, "name")?.takeIf { it.isNotBlank() } ?: tag
        val notes = rootString(json, "body").orEmpty().trim()
        val apkUrl = assetDownloadUrl(json, assetName) ?: return null
        return GithubRelease(versionCode, versionName, notes, apkUrl)
    }

    private fun assetDownloadUrl(json: String, assetName: String): String? {
        val assets = rootArray(json, "assets") ?: return null
        for (item in objectsInArray(assets)) {
            if (rootString(item, "name") == assetName) {
                return rootString(item, "browser_download_url")?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}

internal fun rootString(json: String, key: String): String? {
    val start = rootValueStart(json, key) ?: return null
    if (start >= json.length || json[start] != '"') return null
    return readJsonString(json, start).first
}

internal fun rootArray(json: String, key: String): String? {
    val start = rootValueStart(json, key) ?: return null
    if (start >= json.length || json[start] != '[') return null
    val end = matchingBracket(json, start, '[', ']') ?: return null
    return json.substring(start, end + 1)
}

private fun rootValueStart(json: String, key: String): Int? {
    var i = 0
    var depth = 0
    while (i < json.length) {
        val c = json[i]
        if (c == '"') {
            val (text, next) = readJsonString(json, i)
            if (depth == 1 && text == key) {
                var cursor = next
                while (cursor < json.length && json[cursor].isWhitespace()) cursor++
                if (cursor >= json.length || json[cursor] != ':') return null
                cursor++
                while (cursor < json.length && json[cursor].isWhitespace()) cursor++
                return cursor
            }
            i = next
            continue
        }
        when (c) {
            '{', '[' -> depth++
            '}', ']' -> depth--
        }
        i++
    }
    return null
}

private fun objectsInArray(arrayJson: String): List<String> {
    val objects = mutableListOf<String>()
    var i = 0
    var depth = 0
    var objectStart = -1
    var inString = false
    var escape = false
    while (i < arrayJson.length) {
        val c = arrayJson[i]
        if (inString) {
            if (escape) {
                escape = false
            } else if (c == '\\') {
                escape = true
            } else if (c == '"') {
                inString = false
            }
            i++
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> {
                if (depth == 1) objectStart = i
                depth++
            }
            '}' -> {
                depth--
                if (depth == 1 && objectStart >= 0) {
                    objects += arrayJson.substring(objectStart, i + 1)
                    objectStart = -1
                }
            }
            '[' -> depth++
            ']' -> depth--
        }
        i++
    }
    return objects
}

private fun matchingBracket(json: String, openIndex: Int, open: Char, close: Char): Int? {
    var depth = 0
    var i = openIndex
    var inString = false
    var escape = false
    while (i < json.length) {
        val c = json[i]
        if (inString) {
            if (escape) {
                escape = false
            } else if (c == '\\') {
                escape = true
            } else if (c == '"') {
                inString = false
            }
            i++
            continue
        }
        when (c) {
            '"' -> inString = true
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return i
            }
        }
        i++
    }
    return null
}

private fun readJsonString(json: String, quoteIndex: Int): Pair<String, Int> {
    val out = StringBuilder()
    var i = quoteIndex + 1
    while (i < json.length) {
        val c = json[i]
        when (c) {
            '\\' -> {
                if (i + 1 >= json.length) break
                when (val escaped = json[i + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000c')
                    'u' -> {
                        val hex = json.substring(i + 2, (i + 6).coerceAtMost(json.length))
                        val code = hex.toIntOrNull(16)
                        if (code != null && hex.length == 4) {
                            out.append(code.toChar())
                            i += 6
                            continue
                        }
                        out.append(escaped)
                    }
                    else -> out.append(escaped)
                }
                i += 2
            }
            '"' -> return out.toString() to (i + 1)
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString() to json.length
}
