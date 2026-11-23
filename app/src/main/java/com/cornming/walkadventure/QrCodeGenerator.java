package com.cornming.walkadventure;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.nio.charset.StandardCharsets;

/**
 * 純 Java ISO/IEC 18004 標準 QR Code 編碼產生器（零外部套件、支援 Byte 模式與 Error Correction Level M，版本 1–10）：
 * 可將離線實體藏寶碼與家庭交換禮物卡轉成真實可掃描的 QR 碼點陣與高對比 Bitmap。
 */
final class QrCodeGenerator {
    static final class Matrix {
        final int version;
        final int size;
        final boolean[][] modules; // [row][col], true=dark

        Matrix(int version, int size, boolean[][] modules) {
            this.version = version;
            this.size = size;
            this.modules = modules;
        }
    }

    // Version 1..10 Error Correction Level M 規格表（ISO/IEC 18004 Table 9）
    // {totalCodewords, dataCodewords, ecPerBlock, g1Blocks, g1Data, g2Blocks, g2Data}
    private static final int[][] SPEC_M = {
        null, // 1-indexed
        {26, 16, 10, 1, 16, 0, 0},
        {44, 28, 16, 1, 28, 0, 0},
        {70, 44, 26, 1, 44, 0, 0},
        {100, 64, 18, 2, 32, 0, 0},
        {134, 86, 24, 2, 43, 0, 0},
        {172, 108, 16, 4, 27, 0, 0},
        {196, 124, 18, 4, 31, 0, 0},
        {242, 154, 22, 2, 38, 2, 39},
        {292, 182, 22, 3, 36, 2, 37},
        {346, 216, 26, 4, 43, 1, 44}
    };

    private static final int[][] ALIGN_CENTERS = {
        null,
        {},
        {6, 18},
        {6, 22},
        {6, 26},
        {6, 30},
        {6, 34},
        {6, 22, 38},
        {6, 24, 42},
        {6, 26, 46},
        {6, 28, 50}
    };

    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = x;
            GF_LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
        }
        for (int i = 255; i < 512; i++) {
            GF_EXP[i] = GF_EXP[i - 255];
        }
    }

    private QrCodeGenerator() {}

    /** 將 UTF-8 文字編碼為標準 QR Code 點陣（自動挑選最小版本 V1–V10）。 */
    static Matrix encode(String text) {
        byte[] payload = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        int version = pickVersion(payload.length);
        if (version < 0) {
            // 超出 V10 容量時安全截斷至 210 bytes
            byte[] clipped = new byte[210];
            System.arraycopy(payload, 0, clipped, 0, clipped.length);
            payload = clipped;
            version = 10;
        }

        int[] spec = SPEC_M[version];
        int dataCodewords = spec[1];
        byte[] dataBytes = buildDataCodewords(payload, version, dataCodewords);
        byte[] finalCodewords = interleaveWithEc(dataBytes, spec);

        int size = 17 + version * 4;
        boolean[][] func = new boolean[size][size];
        boolean[][] base = new boolean[size][size];
        placeFunctionPatterns(base, func, version, size);
        placeDataBits(base, func, finalCodewords, size);

        // 評估全部 8 種標準遮罩（Mask 0..7），挑選最低懲罰分者確保最佳掃描率
        int bestMask = 0;
        int bestPenalty = Integer.MAX_VALUE;
        boolean[][] bestModules = null;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] candidate = copyMatrix(base);
            applyMask(candidate, func, mask, size);
            placeFormatBits(candidate, mask, size);
            int penalty = computePenalty(candidate, size);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
                bestModules = candidate;
            }
        }
        if (bestModules == null) {
            bestModules = base;
            placeFormatBits(bestModules, bestMask, size);
        }
        return new Matrix(version, size, bestModules);
    }

    /** 將 QR Code 點陣渲染成含標準 4-module 留白邊框的銳利 Android Bitmap。 */
    static Bitmap renderBitmap(String text, int targetPx, int darkColor, int lightColor) {
        Matrix qr = encode(text);
        int quiet = 4;
        int totalModules = qr.size + quiet * 2;
        int cellPx = Math.max(2, targetPx / totalModules);
        int bmpSize = totalModules * cellPx;
        int[] pixels = new int[bmpSize * bmpSize];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = lightColor;
        }
        for (int r = 0; r < qr.size; r++) {
            for (int c = 0; c < qr.size; c++) {
                if (!qr.modules[r][c]) continue;
                int y0 = (r + quiet) * cellPx;
                int x0 = (c + quiet) * cellPx;
                for (int dy = 0; dy < cellPx; dy++) {
                    int rowOff = (y0 + dy) * bmpSize + x0;
                    for (int dx = 0; dx < cellPx; dx++) {
                        pixels[rowOff + dx] = darkColor;
                    }
                }
            }
        }
        Bitmap bmp = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, bmpSize, 0, 0, bmpSize, bmpSize);
        return bmp;
    }

    static Bitmap renderBitmap(String text, int targetPx) {
        return renderBitmap(text, targetPx, Color.rgb(42, 30, 22), Color.WHITE);
    }

    private static int pickVersion(int byteLen) {
        for (int v = 1; v <= 10; v++) {
            int dataBits = SPEC_M[v][1] * 8;
            int headerBits = 4 + (v <= 9 ? 8 : 16);
            if (headerBits + byteLen * 8 <= dataBits) {
                return v;
            }
        }
        return -1;
    }

    private static byte[] buildDataCodewords(byte[] payload, int version, int dataCodewords) {
        int totalBits = dataCodewords * 8;
        boolean[] bits = new boolean[totalBits];
        int pos = 0;
        pos = appendBits(bits, pos, 0b0100, 4); // Byte mode
        pos = appendBits(bits, pos, payload.length, version <= 9 ? 8 : 16);
        for (byte b : payload) {
            pos = appendBits(bits, pos, b & 0xFF, 8);
        }
        // Terminator (up to 4 zero bits)
        int term = Math.min(4, totalBits - pos);
        pos += term;
        // Align to byte boundary
        while ((pos % 8) != 0 && pos < totalBits) {
            pos++;
        }
        // Pad codewords alternating 0xEC, 0x11
        int padIdx = 0;
        while (pos + 8 <= totalBits) {
            pos = appendBits(bits, pos, (padIdx % 2 == 0) ? 0xEC : 0x11, 8);
            padIdx++;
        }

        byte[] out = new byte[dataCodewords];
        for (int i = 0; i < dataCodewords; i++) {
            int val = 0;
            for (int b = 0; b < 8; b++) {
                val = (val << 1) | (bits[i * 8 + b] ? 1 : 0);
            }
            out[i] = (byte) val;
        }
        return out;
    }

    private static int appendBits(boolean[] bits, int pos, int value, int count) {
        for (int i = count - 1; i >= 0 && pos < bits.length; i--) {
            bits[pos++] = ((value >> i) & 1) != 0;
        }
        return pos;
    }

    private static byte[] interleaveWithEc(byte[] data, int[] spec) {
        int total = spec[0];
        int ecLen = spec[2];
        int g1Count = spec[3];
        int g1Len = spec[4];
        int g2Count = spec[5];
        int g2Len = spec[6];
        int totalBlocks = g1Count + g2Count;

        byte[][] dataBlocks = new byte[totalBlocks][];
        byte[][] ecBlocks = new byte[totalBlocks][];
        int[] gen = rsGeneratorPoly(ecLen);

        int offset = 0;
        int maxDataLen = 0;
        for (int b = 0; b < totalBlocks; b++) {
            int len = b < g1Count ? g1Len : g2Len;
            maxDataLen = Math.max(maxDataLen, len);
            byte[] block = new byte[len];
            System.arraycopy(data, offset, block, 0, len);
            offset += len;
            dataBlocks[b] = block;
            ecBlocks[b] = rsComputeRemainder(block, gen, ecLen);
        }

        byte[] out = new byte[total];
        int idx = 0;
        for (int i = 0; i < maxDataLen; i++) {
            for (int b = 0; b < totalBlocks; b++) {
                if (i < dataBlocks[b].length) {
                    out[idx++] = dataBlocks[b][i];
                }
            }
        }
        for (int i = 0; i < ecLen; i++) {
            for (int b = 0; b < totalBlocks; b++) {
                out[idx++] = ecBlocks[b][i];
            }
        }
        return out;
    }

    private static int gfMul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return GF_EXP[GF_LOG[a] + GF_LOG[b]];
    }

    private static int[] rsGeneratorPoly(int degree) {
        int[] poly = new int[]{1};
        for (int i = 0; i < degree; i++) {
            int factor = GF_EXP[i];
            int[] next = new int[poly.length + 1];
            for (int j = 0; j < poly.length; j++) {
                next[j] ^= poly[j];
                next[j + 1] ^= gfMul(poly[j], factor);
            }
            poly = next;
        }
        return poly;
    }

    private static byte[] rsComputeRemainder(byte[] data, int[] gen, int ecLen) {
        int[] rem = new int[ecLen];
        for (byte b : data) {
            int factor = (b & 0xFF) ^ rem[0];
            System.arraycopy(rem, 1, rem, 0, ecLen - 1);
            rem[ecLen - 1] = 0;
            for (int i = 0; i < ecLen; i++) {
                rem[i] ^= gfMul(gen[i + 1], factor);
            }
        }
        byte[] out = new byte[ecLen];
        for (int i = 0; i < ecLen; i++) {
            out[i] = (byte) rem[i];
        }
        return out;
    }

    private static void placeFunctionPatterns(boolean[][] m, boolean[][] func, int version, int size) {
        // 3 個定位方塊＋分隔白邊
        placeFinder(m, func, 0, 0, size);
        placeFinder(m, func, 0, size - 7, size);
        placeFinder(m, func, size - 7, 0, size);

        // 定時線
        for (int i = 8; i < size - 8; i++) {
            boolean dark = (i % 2) == 0;
            setFunc(m, func, 6, i, dark);
            setFunc(m, func, i, 6, dark);
        }

        // 對齊圖案（V2+）
        int[] centers = ALIGN_CENTERS[version];
        for (int r : centers) {
            for (int c : centers) {
                if ((r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6)) {
                    continue;
                }
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int dist = Math.max(Math.abs(dr), Math.abs(dc));
                        setFunc(m, func, r + dr, c + dc, dist != 1);
                    }
                }
            }
        }

        // 保留格式資訊區與固定黑點
        for (int i = 0; i <= 8; i++) {
            if (i != 6) {
                func[8][i] = true;
                func[i][8] = true;
            }
        }
        for (int i = 0; i < 8; i++) {
            func[8][size - 1 - i] = true;
            func[size - 1 - i][8] = true;
        }
        setFunc(m, func, size - 8, 8, true);

        // 版本資訊區（V7+）
        if (version >= 7) {
            int rem = version << 12;
            for (int i = 17; i >= 12; i--) {
                if (((rem >> i) & 1) != 0) {
                    rem ^= 0x1F25 << (i - 12);
                }
            }
            int bits = (version << 12) | rem;
            for (int i = 0; i < 18; i++) {
                boolean dark = ((bits >> i) & 1) != 0;
                int a = size - 11 + (i % 3);
                int b = i / 3;
                setFunc(m, func, a, b, dark);
                setFunc(m, func, b, a, dark);
            }
        }
    }

    private static void placeFinder(boolean[][] m, boolean[][] func, int top, int left, int size) {
        for (int dr = -1; dr <= 7; dr++) {
            for (int dc = -1; dc <= 7; dc++) {
                int r = top + dr;
                int c = left + dc;
                if (r < 0 || r >= size || c < 0 || c >= size) continue;
                boolean inside = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
                boolean dark = inside && (
                    dr == 0 || dr == 6 || dc == 0 || dc == 6
                        || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4)
                );
                setFunc(m, func, r, c, dark);
            }
        }
    }

    private static void setFunc(boolean[][] m, boolean[][] func, int r, int c, boolean dark) {
        m[r][c] = dark;
        func[r][c] = true;
    }

    private static void placeDataBits(boolean[][] m, boolean[][] func, byte[] codewords, int size) {
        int totalBits = codewords.length * 8;
        int bitIdx = 0;
        boolean upward = true;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5; // 跳過直向定時線
            for (int step = 0; step < size; step++) {
                int r = upward ? (size - 1 - step) : step;
                for (int dx = 0; dx < 2; dx++) {
                    int c = right - dx;
                    if (func[r][c]) continue;
                    boolean dark = false;
                    if (bitIdx < totalBits) {
                        dark = ((codewords[bitIdx >> 3] >> (7 - (bitIdx & 7))) & 1) != 0;
                        bitIdx++;
                    }
                    m[r][c] = dark;
                }
            }
            upward = !upward;
        }
    }

    private static void applyMask(boolean[][] m, boolean[][] func, int mask, int size) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (func[r][c]) continue;
                boolean invert;
                switch (mask) {
                    case 0: invert = ((r + c) % 2) == 0; break;
                    case 1: invert = (r % 2) == 0; break;
                    case 2: invert = (c % 3) == 0; break;
                    case 3: invert = ((r + c) % 3) == 0; break;
                    case 4: invert = (((r / 2) + (c / 3)) % 2) == 0; break;
                    case 5: invert = ((r * c) % 2 + (r * c) % 3) == 0; break;
                    case 6: invert = (((r * c) % 2 + (r * c) % 3) % 2) == 0; break;
                    default: invert = (((r + c) % 2 + (r * c) % 3) % 2) == 0; break;
                }
                if (invert) {
                    m[r][c] = !m[r][c];
                }
            }
        }
    }

    private static void placeFormatBits(boolean[][] m, int mask, int size) {
        int data = (0b00 << 3) | (mask & 7); // EC Level M = 00
        int rem = data << 10;
        for (int i = 14; i >= 10; i--) {
            if (((rem >> i) & 1) != 0) {
                rem ^= 0x537 << (i - 10);
            }
        }
        int bits = ((data << 10) | rem) ^ 0x5412;

        // Top-left around finder
        for (int i = 0; i <= 5; i++) m[i][8] = ((bits >> i) & 1) != 0;
        m[7][8] = ((bits >> 6) & 1) != 0;
        m[8][8] = ((bits >> 7) & 1) != 0;
        m[8][7] = ((bits >> 8) & 1) != 0;
        for (int i = 9; i < 15; i++) m[8][14 - i] = ((bits >> i) & 1) != 0;

        // Top-right & bottom-left
        for (int i = 0; i < 8; i++) m[8][size - 1 - i] = ((bits >> i) & 1) != 0;
        for (int i = 8; i < 15; i++) m[size - 15 + i][8] = ((bits >> i) & 1) != 0;
    }

    private static int computePenalty(boolean[][] m, int size) {
        int penalty = 0;
        // 規則 1：同色連續 5 格以上
        for (int r = 0; r < size; r++) {
            int run = 1;
            for (int c = 1; c < size; c++) {
                if (m[r][c] == m[r][c - 1]) {
                    run++;
                    if (run == 5) penalty += 3;
                    else if (run > 5) penalty += 1;
                } else {
                    run = 1;
                }
            }
        }
        for (int c = 0; c < size; c++) {
            int run = 1;
            for (int r = 1; r < size; r++) {
                if (m[r][c] == m[r - 1][c]) {
                    run++;
                    if (run == 5) penalty += 3;
                    else if (run > 5) penalty += 1;
                } else {
                    run = 1;
                }
            }
        }
        // 規則 2：2x2 同色方塊
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                boolean v = m[r][c];
                if (v == m[r][c + 1] && v == m[r + 1][c] && v == m[r + 1][c + 1]) {
                    penalty += 3;
                }
            }
        }
        // 規則 4：黑點比例偏離 50%
        int dark = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (m[r][c]) dark++;
            }
        }
        int total = size * size;
        int k = Math.abs(dark * 20 - total * 10) / total;
        penalty += k * 10;
        return penalty;
    }

    private static boolean[][] copyMatrix(boolean[][] src) {
        boolean[][] out = new boolean[src.length][];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i].clone();
        }
        return out;
    }
}
