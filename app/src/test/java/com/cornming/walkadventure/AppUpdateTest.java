package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class AppUpdateTest {
    private static final String SAMPLE_RELEASE_JSON = "{"
        + "\"tag_name\":\"v0.3.0\","
        + "\"draft\":false,"
        + "\"prerelease\":false,"
        + "\"html_url\":\"https://github.com/cornming/WalkAdventure/releases/tag/v0.3.0\","
        + "\"body\":\"說明內文包含干擾文字 \\\"tag_name\\\":\\\"v9.9.9\\\" 與換行\\n第二行\\u2728\","
        + "\"assets\":["
        + "  {\"name\":\"checksums.txt\",\"size\":120,\"browser_download_url\":\"https://github.com/cornming/WalkAdventure/releases/download/v0.3.0/checksums.txt\"},"
        + "  {\"name\":\"WalkAdventure-v0.3.0.apk\",\"size\":24576,\"digest\":\"sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\"browser_download_url\":\"https://github.com/cornming/WalkAdventure/releases/download/v0.3.0/WalkAdventure-v0.3.0.apk\"}"
        + "]"
        + "}";

    @Test public void comparesSemanticVersionsCorrectly() {
        assertTrue(AppUpdate.isRemoteNewer("v0.2.2", "0.2.1"));
        assertTrue(AppUpdate.isRemoteNewer("v0.10.0", "0.2.9"));
        assertTrue(AppUpdate.isRemoteNewer("v1.0.0", "0.99.99"));
        assertTrue(AppUpdate.isRemoteNewer("0.3.0", "0.3.0-rc1"));

        assertFalse(AppUpdate.isRemoteNewer("v0.2.1", "0.2.1"));
        assertFalse(AppUpdate.isRemoteNewer("v0.2.0", "0.2.1"));
        assertFalse(AppUpdate.isRemoteNewer("0.3.0-rc1", "0.3.0"));
        assertFalse(AppUpdate.isRemoteNewer("", "0.2.1"));
        assertFalse(AppUpdate.isRemoteNewer("latest", "0.2.1"));
        assertFalse(AppUpdate.isRemoteNewer("v0.2.2", null));
    }

    @Test public void parsesLatestReleaseJsonIgnoringBodyDistractors() {
        AppUpdate.ReleaseInfo info = AppUpdate.parseLatestRelease(SAMPLE_RELEASE_JSON);
        assertNotNull(info);
        assertEquals("v0.3.0", info.tagName);
        assertEquals("0.3.0", info.versionName);
        assertEquals("https://github.com/cornming/WalkAdventure/releases/tag/v0.3.0", info.htmlUrl);
        assertEquals("WalkAdventure-v0.3.0.apk", info.apkName);
        assertEquals(
            "https://github.com/cornming/WalkAdventure/releases/download/v0.3.0/WalkAdventure-v0.3.0.apk",
            info.apkDownloadUrl
        );
        assertEquals(24576L, info.apkSize);
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", info.apkSha256);
    }

    @Test public void rejectsDraftPrereleaseMalformedJsonAndMissingApk() {
        assertNull(AppUpdate.parseLatestRelease(null));
        assertNull(AppUpdate.parseLatestRelease("{bad json"));
        assertNull(AppUpdate.parseLatestRelease(
            SAMPLE_RELEASE_JSON.replace("\"draft\":false", "\"draft\":true")
        ));
        assertNull(AppUpdate.parseLatestRelease(
            SAMPLE_RELEASE_JSON.replace("\"prerelease\":false", "\"prerelease\":true")
        ));
        assertNull(AppUpdate.parseLatestRelease(
            SAMPLE_RELEASE_JSON.replace("WalkAdventure-v0.3.0.apk", "notes.txt")
        ));
    }

    @Test public void enforcesTrustedUrlsAndRejectsSpoofedHosts() {
        assertTrue(AppUpdate.isTrustedAssetUrl(
            "https://github.com/cornming/WalkAdventure/releases/download/v0.2.1/WalkAdventure-v0.2.1.apk"
        ));
        assertFalse(AppUpdate.isTrustedAssetUrl(
            "http://github.com/cornming/WalkAdventure/releases/download/v0.2.1/WalkAdventure-v0.2.1.apk"
        ));
        assertFalse(AppUpdate.isTrustedAssetUrl(
            "https://github.com/other/repo/releases/download/v0.2.1/app.apk"
        ));
        assertFalse(AppUpdate.isTrustedAssetUrl(
            "https://evil.example@github.com/cornming/WalkAdventure/releases/download/v0.2.1/app.apk"
        ));

        assertTrue(AppUpdate.isTrustedDownloadRedirectUrl(
            "https://objects.githubusercontent.com/github-production-release-asset-123/app.apk"
        ));
        assertTrue(AppUpdate.isTrustedDownloadRedirectUrl(
            "https://release-assets.githubusercontent.com/some/path"
        ));
        assertFalse(AppUpdate.isTrustedDownloadRedirectUrl(
            "https://badgithubusercontent.com/evil.apk"
        ));
        assertFalse(AppUpdate.isTrustedDownloadRedirectUrl(
            "http://objects.githubusercontent.com/evil.apk"
        ));
    }

    @Test public void parsesSha256DigestAndRespectsAutoCheckInterval() {
        String valid = "sha256:AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899";
        assertEquals(
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
            AppUpdate.parseSha256Digest(valid)
        );
        assertEquals("", AppUpdate.parseSha256Digest("sha256:short"));
        assertEquals("", AppUpdate.parseSha256Digest("md5:012345"));

        long base = 1_700_000_000_000L;
        assertTrue(AppUpdate.shouldAutoCheck(0L, base));
        assertFalse(AppUpdate.shouldAutoCheck(base, base + 60_000L));
        assertTrue(AppUpdate.shouldAutoCheck(base, base + AppUpdate.AUTO_CHECK_INTERVAL_MS));
        assertTrue(AppUpdate.shouldAutoCheck(base, base - 10_000L));
    }

    @Test public void verifiesDownloadedApkSizeAndSha256() throws Exception {
        File tmp = File.createTempFile("walkadv-update-test", ".apk");
        try {
            byte[] payload = "WalkAdventure-Test-APK".getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(payload);
            }
            String expectedHash = AppUpdate.sha256Hex(tmp);
            assertEquals(64, expectedHash.length());
            assertTrue(AppUpdate.verifyDownloadedApk(tmp, payload.length, expectedHash));
            assertTrue(AppUpdate.verifyDownloadedApk(tmp, payload.length, ""));
            assertFalse(AppUpdate.verifyDownloadedApk(tmp, payload.length + 1, expectedHash));
            assertFalse(AppUpdate.verifyDownloadedApk(
                tmp,
                payload.length,
                "0000000000000000000000000000000000000000000000000000000000000000"
            ));
        } finally {
            boolean deleted = tmp.delete();
            if (!deleted && tmp.exists()) {
                tmp.deleteOnExit();
            }
        }
    }
}
