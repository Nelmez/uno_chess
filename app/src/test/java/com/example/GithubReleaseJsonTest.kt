package com.example

import com.example.update.GithubReleaseJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubReleaseJsonTest {
    @Test
    fun parsesVersionCodeFromTagAndApkAsset() {
        val release = GithubReleaseJson.parse(
            """
            {
              "author": {"login": "Nelmez", "name": "Not the release"},
              "tag_name": "v2",
              "name": "1.1",
              "body": "Online lobbies.\nInstall from GitHub.",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://example.com/notes"},
                {"name": "unochess.apk", "browser_download_url": "https://github.com/Nelmez/uno_chess/releases/download/v2/unochess.apk"}
              ]
            }
            """.trimIndent()
        )

        checkNotNull(release)
        assertEquals(2, release.versionCode)
        assertEquals("1.1", release.versionName)
        assertEquals("Online lobbies.\nInstall from GitHub.", release.notes)
        assertEquals(
            "https://github.com/Nelmez/uno_chess/releases/download/v2/unochess.apk",
            release.apkUrl
        )
    }

    @Test
    fun ignoresReleaseWithoutTheApk() {
        val release = GithubReleaseJson.parse(
            """
            {"tag_name":"v3","name":"1.2","body":"","assets":[]}
            """.trimIndent()
        )
        assertNull(release)
    }

    @Test
    fun parsesSemanticVersionTag() {
        val release = GithubReleaseJson.parse(
            """
            {
              "tag_name": "v1.1.1",
              "name": "1.1.1",
              "body": "Fix online multiplayer.",
              "assets": [
                {"name": "unochess.apk", "browser_download_url": "https://github.com/Nelmez/uno_chess/releases/download/v1.1.1/unochess.apk"}
              ]
            }
            """.trimIndent()
        )
        checkNotNull(release)
        assertEquals(3, release.versionCode)
        assertEquals("1.1.1", release.versionName)
    }

    @Test
    fun parsesVersion13Tag() {
        val release = GithubReleaseJson.parse(
            """
            {
              "tag_name": "v4",
              "name": "1.3",
              "body": "Card pity system.",
              "assets": [
                {"name": "unochess.apk", "browser_download_url": "https://github.com/Nelmez/uno_chess/releases/download/v4/unochess.apk"}
              ]
            }
            """.trimIndent()
        )
        checkNotNull(release)
        assertEquals(4, release.versionCode)
        assertEquals("1.3", release.versionName)
    }
}
