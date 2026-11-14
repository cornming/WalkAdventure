package com.cornming.walkadventure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GitHub Releases 自動查版、安全連結檢查與 APK 完整性驗證邏輯。
 * 不依賴任何 Android API 或第三方 JSON 函式庫，方便在 JVM 中直接做單元測試。
 */
final class AppUpdate {
    static final String REPO_SLUG = "cornming/WalkAdventure";
    static final String LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/" + REPO_SLUG + "/releases/latest";
    static final String RELEASES_PAGE_URL =
        "https://github.com/" + REPO_SLUG + "/releases";
    static final long AUTO_CHECK_INTERVAL_MS = 3L * 60L * 60L * 1000L;
    static final long MAX_APK_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_JSON_BYTES = 512 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    private AppUpdate() {}

    static final class ReleaseInfo {
        final String tagName;
        final String versionName;
        final String htmlUrl;
        final String apkName;
        final String apkDownloadUrl;
        final long apkSize;
        final String apkSha256;

        ReleaseInfo(
            String tagName,
            String versionName,
            String htmlUrl,
            String apkName,
            String apkDownloadUrl,
            long apkSize,
            String apkSha256
        ) {
            this.tagName = tagName;
            this.versionName = versionName;
            this.htmlUrl = htmlUrl;
            this.apkName = apkName;
            this.apkDownloadUrl = apkDownloadUrl;
            this.apkSize = apkSize;
            this.apkSha256 = apkSha256;
        }
    }

    static final class CheckOutcome {
        static final int STATUS_OK = 0;
        static final int STATUS_NOT_MODIFIED = 1;
        static final int STATUS_RATE_LIMITED = 2;
        static final int STATUS_NO_RELEASE = 3;
        static final int STATUS_ERROR = 4;

        final int status;
        final String etag;
        final ReleaseInfo release;

        CheckOutcome(int status, String etag, ReleaseInfo release) {
            this.status = status;
            this.etag = etag;
            this.release = release;
        }
    }

    /** 距離上次檢查超過 {@link #AUTO_CHECK_INTERVAL_MS} 時才自動查版；時間倒退時重設視同需要檢查。 */
    static boolean shouldAutoCheck(long lastCheckMs, long nowMs) {
        if (lastCheckMs <= 0 || nowMs < lastCheckMs) return true;
        return (nowMs - lastCheckMs) >= AUTO_CHECK_INTERVAL_MS;
    }

    static String normalizeVersion(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private static final class ParsedVersion {
        final int[] core;
        final String prerelease;

        ParsedVersion(int[] core, String prerelease) {
            this.core = core;
            this.prerelease = prerelease;
        }
    }

    private static ParsedVersion parseVersion(String raw) {
        String norm = normalizeVersion(raw);
        if (norm.isEmpty()) return null;
        int plusIdx = norm.indexOf('+');
        if (plusIdx >= 0) {
            norm = norm.substring(0, plusIdx);
        }
        String prerelease = "";
        int dashIdx = norm.indexOf('-');
        if (dashIdx >= 0) {
            prerelease = norm.substring(dashIdx + 1).trim();
            norm = norm.substring(0, dashIdx);
        }
        String[] parts = norm.split("\\.");
        if (parts.length == 0 || parts.length > 4) return null;
        int[] nums = new int[3];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) return null;
            for (int j = 0; j < part.length(); j++) {
                if (!Character.isDigit(part.charAt(j))) return null;
            }
            try {
                int value = Integer.parseInt(part);
                if (i < 3) nums[i] = value;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return new ParsedVersion(nums, prerelease);
    }

    static boolean isValidSemVer(String raw) {
        return parseVersion(raw) != null;
    }

    /** 比較兩個語意化版本；若任一版本格式無法辨識則安全回傳 0。 */
    static int compareVersions(String leftRaw, String rightRaw) {
        ParsedVersion left = parseVersion(leftRaw);
        ParsedVersion right = parseVersion(rightRaw);
        if (left == null || right == null) return 0;
        for (int i = 0; i < 3; i++) {
            if (left.core[i] != right.core[i]) {
                return Integer.compare(left.core[i], right.core[i]);
            }
        }
        boolean leftPre = !left.prerelease.isEmpty();
        boolean rightPre = !right.prerelease.isEmpty();
        if (!leftPre && !rightPre) return 0;
        if (!leftPre) return 1;
        if (!rightPre) return -1;
        return left.prerelease.compareToIgnoreCase(right.prerelease);
    }

    static boolean isRemoteNewer(String remoteTagOrVersion, String currentVersion) {
        if (!isValidSemVer(remoteTagOrVersion) || !isValidSemVer(currentVersion)) {
            return false;
        }
        return compareVersions(remoteTagOrVersion, currentVersion) > 0;
    }

    static boolean isTrustedPageUrl(String url) {
        URI uri = parseHttpsUri(url);
        if (uri == null) return false;
        String host = lowerHost(uri);
        String path = uri.getPath();
        return "github.com".equals(host)
            && path != null
            && path.startsWith("/" + REPO_SLUG + "/");
    }

    static boolean isTrustedAssetUrl(String url) {
        URI uri = parseHttpsUri(url);
        if (uri == null) return false;
        String host = lowerHost(uri);
        String path = uri.getPath();
        return "github.com".equals(host)
            && path != null
            && path.startsWith("/" + REPO_SLUG + "/releases/download/")
            && path.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    static boolean isTrustedDownloadRedirectUrl(String url) {
        URI uri = parseHttpsUri(url);
        if (uri == null) return false;
        String host = lowerHost(uri);
        return "github.com".equals(host)
            || host.endsWith(".github.com")
            || "githubusercontent.com".equals(host)
            || host.endsWith(".githubusercontent.com");
    }

    private static URI parseHttpsUri(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            URI uri = new URI(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) return null;
            if (uri.getRawUserInfo() != null) return null;
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return null;
            return uri;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static String lowerHost(URI uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    static String parseSha256Digest(String digest) {
        if (digest == null) return "";
        String trimmed = digest.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("sha256:")) {
            trimmed = trimmed.substring("sha256:".length()).trim();
        }
        if (trimmed.length() != 64) return "";
        for (int i = 0; i < 64; i++) {
            char ch = trimmed.charAt(i);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f');
            if (!hex) return "";
        }
        return trimmed;
    }

    /** 解析 GitHub `/releases/latest` JSON；草稿、預覽版、缺少有效 APK 或非預期網址皆回傳 null。 */
    static ReleaseInfo parseLatestRelease(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Object parsed;
        try {
            parsed = new JsonParser(json).parseRoot();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (!(parsed instanceof Map<?, ?>)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;

        if (Boolean.TRUE.equals(root.get("draft")) || Boolean.TRUE.equals(root.get("prerelease"))) {
            return null;
        }
        String tagName = asString(root.get("tag_name"));
        String versionName = normalizeVersion(tagName);
        if (!isValidSemVer(versionName)) return null;

        String rawHtmlUrl = asString(root.get("html_url"));
        String htmlUrl = isTrustedPageUrl(rawHtmlUrl) ? rawHtmlUrl : RELEASES_PAGE_URL;

        Object assetsObj = root.get("assets");
        if (!(assetsObj instanceof List<?>)) return null;
        for (Object item : (List<?>) assetsObj) {
            if (!(item instanceof Map<?, ?>)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> asset = (Map<String, Object>) item;
            String name = asString(asset.get("name"));
            if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
            String downloadUrl = asString(asset.get("browser_download_url"));
            if (!isTrustedAssetUrl(downloadUrl)) continue;
            long size = asLong(asset.get("size"));
            if (size < 0 || size > MAX_APK_BYTES) continue;
            String sha256 = parseSha256Digest(asString(asset.get("digest")));
            return new ReleaseInfo(tagName, versionName, htmlUrl, name, downloadUrl, size, sha256);
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    static CheckOutcome fetchLatestRelease(String ifNoneMatch, ReleaseInfo cachedInfo) {
        HttpURLConnection conn = null;
        try {
            URL url = new URI(LATEST_RELEASE_API_URL).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "WalkAdventure-Updater");
            if (ifNoneMatch != null && !ifNoneMatch.trim().isEmpty()) {
                conn.setRequestProperty("If-None-Match", ifNoneMatch.trim());
            }

            int code = conn.getResponseCode();
            String responseEtag = conn.getHeaderField("ETag");
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && cachedInfo != null) {
                return new CheckOutcome(
                    CheckOutcome.STATUS_NOT_MODIFIED,
                    responseEtag != null ? responseEtag : ifNoneMatch,
                    cachedInfo
                );
            }
            if (code == HttpURLConnection.HTTP_FORBIDDEN || code == 429) {
                return new CheckOutcome(CheckOutcome.STATUS_RATE_LIMITED, ifNoneMatch, cachedInfo);
            }
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return new CheckOutcome(CheckOutcome.STATUS_NO_RELEASE, ifNoneMatch, null);
            }
            if (code != HttpURLConnection.HTTP_OK) {
                return new CheckOutcome(CheckOutcome.STATUS_ERROR, ifNoneMatch, cachedInfo);
            }

            String body = readUtf8Bounded(conn.getInputStream(), MAX_JSON_BYTES);
            ReleaseInfo info = parseLatestRelease(body);
            if (info == null) {
                return new CheckOutcome(CheckOutcome.STATUS_NO_RELEASE, responseEtag, null);
            }
            return new CheckOutcome(CheckOutcome.STATUS_OK, responseEtag, info);
        } catch (IOException | URISyntaxException | IllegalArgumentException ignored) {
            return new CheckOutcome(CheckOutcome.STATUS_ERROR, ifNoneMatch, cachedInfo);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readUtf8Bounded(InputStream in, int maxBytes) throws IOException {
        if (in == null) return "";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buf)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Response exceeds limit");
                }
                out.write(buf, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** 下載新版 APK 至暫存檔並核對大小與 SHA-256，全部通過才原子置換成目標檔。 */
    static boolean downloadVerifiedApk(ReleaseInfo release, File targetApkFile) {
        if (release == null || targetApkFile == null || !isTrustedAssetUrl(release.apkDownloadUrl)) {
            return false;
        }
        File parentDir = targetApkFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            return false;
        }
        File partFile = new File(targetApkFile.getPath() + ".part");
        HttpURLConnection conn = null;
        try {
            conn = openTrustedDownloadConnection(release.apkDownloadUrl);
            if (conn == null || conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            long headerLength = conn.getContentLengthLong();
            if (headerLength > MAX_APK_BYTES) {
                return false;
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(partFile)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_APK_BYTES) {
                        return false;
                    }
                    digest.update(buf, 0, read);
                    out.write(buf, 0, read);
                }
                out.flush();
            }

            if (total <= 0L) return false;
            if (release.apkSize > 0L && total != release.apkSize) return false;
            String actualSha256 = toHexLower(digest.digest());
            if (!release.apkSha256.isEmpty() && !release.apkSha256.equalsIgnoreCase(actualSha256)) {
                return false;
            }
            if (targetApkFile.exists() && !targetApkFile.delete()) {
                return false;
            }
            return partFile.renameTo(targetApkFile);
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
            if (partFile.exists()) {
                boolean deleted = partFile.delete();
                if (!deleted) {
                    // 下載暫存檔清理屬盡力清除
                }
            }
        }
    }

    private static HttpURLConnection openTrustedDownloadConnection(String initialUrl) throws IOException {
        String currentUrl = initialUrl;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isTrustedDownloadRedirectUrl(currentUrl)) {
                return null;
            }
            URL url;
            try {
                url = new URI(currentUrl).toURL();
            } catch (URISyntaxException ignored) {
                return null;
            }
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "WalkAdventure-Updater");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307
                || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    return null;
                }
                try {
                    currentUrl = url.toURI().resolve(location).toString();
                } catch (URISyntaxException ignored) {
                    return null;
                }
                continue;
            }
            return conn;
        }
        return null;
    }

    static boolean verifyDownloadedApk(File file, long expectedSize, String expectedSha256) {
        if (file == null || !file.isFile()) return false;
        long length = file.length();
        if (length <= 0L || length > MAX_APK_BYTES) return false;
        if (expectedSize > 0L && length != expectedSize) return false;
        if (expectedSha256 == null || expectedSha256.trim().isEmpty()) {
            return true;
        }
        String normalizedExpected = parseSha256Digest(expectedSha256);
        if (normalizedExpected.isEmpty()) return false;
        String actual = sha256Hex(file);
        return normalizedExpected.equals(actual);
    }

    static String sha256Hex(File file) {
        if (file == null || !file.isFile()) return "";
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
            return toHexLower(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ignored) {
            return "";
        }
    }

    static String toHexLower(byte[] bytes) {
        if (bytes == null) return "";
        char[] hexDigits = "0123456789abcdef".toCharArray();
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = hexDigits[v >>> 4];
            chars[i * 2 + 1] = hexDigits[v & 0x0F];
        }
        return new String(chars);
    }

    /** 輕量級純 Java 遞迴下降 JSON 解析器，避免單元測試環境碰到 org.json Android stub 例外。 */
    private static final class JsonParser {
        private final String src;
        private int pos;
        private int depth;

        JsonParser(String src) {
            this.src = src;
        }

        Object parseRoot() {
            Object val = parseValue();
            skipWhitespace();
            if (pos != src.length()) {
                throw new IllegalArgumentException("Trailing JSON content");
            }
            return val;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) throw new IllegalArgumentException("Unexpected end");
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw new IllegalArgumentException("Unexpected char: " + c);
        }

        private Map<String, Object> parseObject() {
            enter();
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                depth--;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                expect('}');
                depth--;
                return map;
            }
        }

        private List<Object> parseArray() {
            enter();
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                expect(']');
                depth--;
                return list;
            }
        }

        private void enter() {
            if (++depth > 32) throw new IllegalArgumentException("JSON too deep");
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalArgumentException("Bad escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw new IllegalArgumentException("Bad unicode");
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Bad escape: " + esc);
                    }
                } else if (c < 0x20) {
                    throw new IllegalArgumentException("Control character in JSON string");
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Bad boolean");
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Bad null");
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) pos++;
            if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
                throw new IllegalArgumentException("Bad number");
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            boolean isFloat = false;
            if (peek('.')) {
                isFloat = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (peek('e') || peek('E')) {
                isFloat = true;
                pos++;
                if (peek('+') || peek('-')) pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            String num = src.substring(start, pos);
            try {
                return isFloat ? Double.valueOf(num) : Long.valueOf(num);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number", e);
            }
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private boolean peek(char expected) {
            return pos < src.length() && src.charAt(pos) == expected;
        }

        private void expect(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "'");
            }
            pos++;
        }
    }
}
