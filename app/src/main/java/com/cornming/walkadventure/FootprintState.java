package com.cornming.walkadventure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 隱私優先「本機足跡迷霧探索」與家庭秘密基地（純 Java，100% 離線）：
 * - 預設關閉；開啟後只使用約 200m × 200m 粗略網格，且只保存相對於起點的整數網格偏移 (rx, ry)，
 *   絕不儲存真實經緯度或街道門牌，更不上傳任何伺服器！
 * - 踩破新迷霧格立即獲得「首探迷霧大寶箱」，還可以把常去的公園或步道命名成「家庭秘密基地」！
 */
final class FootprintState {
    static final double GRID_LAT_DEG = 0.0018; // 約 200 公尺
    static final double GRID_LON_DEG = 0.0020; // 約 200 公尺（台灣緯度）
    static final int MAX_CELLS = 240;
    static final int NEW_TILE_COINS = 35;
    static final int NEW_TILE_STARS = 12;

    static final class Tile {
        final int rx;
        final int ry;
        String baseName;
        final String discoveredDate;
        int visitCount;

        Tile(int rx, int ry, String baseName, String discoveredDate, int visitCount) {
            this.rx = rx;
            this.ry = ry;
            this.baseName = baseName != null ? baseName.trim() : "";
            this.discoveredDate = discoveredDate != null ? discoveredDate : "";
            this.visitCount = Math.max(1, visitCount);
        }

        String key() {
            return rx + "," + ry;
        }

        boolean isNamedBase() {
            return !baseName.isEmpty();
        }
    }

    static final class DiscoveryOutcome {
        final boolean isNewTile;
        final Tile tile;
        final int coinsGained;
        final int starSandGained;
        final boolean droppedEgg;
        final String message;

        DiscoveryOutcome(
            boolean isNewTile,
            Tile tile,
            int coinsGained,
            int starSandGained,
            boolean droppedEgg,
            String message
        ) {
            this.isNewTile = isNewTile;
            this.tile = tile;
            this.coinsGained = coinsGained;
            this.starSandGained = starSandGained;
            this.droppedEgg = droppedEgg;
            this.message = message;
        }
    }

    boolean enabled = false;
    boolean hasAnchor = false;
    long anchorLatGrid = 0L;
    long anchorLonGrid = 0L;
    int lastRx = 0;
    int lastRy = 0;
    String lastManualBaseDate = "";

    final Map<String, Tile> tiles = new LinkedHashMap<>();

    int discoveredCount() {
        return tiles.size();
    }

    int namedBaseCount() {
        int c = 0;
        for (Tile t : tiles.values()) {
            if (t.isNamedBase()) c++;
        }
        return c;
    }

    Tile currentTile() {
        return tiles.get(lastRx + "," + lastRy);
    }

    List<Tile> allTiles() {
        return new ArrayList<>(tiles.values());
    }

    List<Tile> namedBases() {
        List<Tile> out = new ArrayList<>();
        for (Tile t : tiles.values()) {
            if (t.isNamedBase()) out.add(t);
        }
        return out;
    }

    /** 依粗略經緯度記錄一次本機網格探索（僅保存相對整數位移，絕不保留精確座標）。 */
    DiscoveryOutcome recordCoarseLocation(double lat, double lon, String today) {
        if (!enabled || Double.isNaN(lat) || Double.isNaN(lon)) {
            return new DiscoveryOutcome(false, null, 0, 0, false, "");
        }
        long gLat = Math.round(lat / GRID_LAT_DEG);
        long gLon = Math.round(lon / GRID_LON_DEG);
        if (!hasAnchor) {
            hasAnchor = true;
            anchorLatGrid = gLat;
            anchorLonGrid = gLon;
        }
        int rx = (int) Math.max(-5000L, Math.min(5000L, gLon - anchorLonGrid));
        int ry = (int) Math.max(-5000L, Math.min(5000L, gLat - anchorLatGrid));
        return recordRelativeTile(rx, ry, today, "");
    }

    /** 無定位權限或旅行打卡時，可每日手動開拓一格「家庭新據點」並直接命名。 */
    DiscoveryOutcome recordManualBaseCheckIn(String baseName, String today) {
        String clean = baseName == null ? "" : baseName.trim();
        if (clean.isEmpty()) {
            return new DiscoveryOutcome(false, null, 0, 0, false, "請幫這個家庭秘密基地取個可愛名字！");
        }
        if (clean.length() > 14) clean = clean.substring(0, 14);
        if (today != null && today.equals(lastManualBaseDate)) {
            // 同一天再打卡只更新目前基地名字，不重複刷獎勵
            Tile cur = currentTile();
            if (cur != null) {
                cur.baseName = clean;
                return new DiscoveryOutcome(false, cur, 0, 0, false, "📍 已更新秘密基地名稱為「" + clean + "」！");
            }
        }
        // 找一格鄰近未探索的相鄰格放置新基地
        int[] coord = nextSpiralOpenCoord();
        lastManualBaseDate = today != null ? today : "";
        return recordRelativeTile(coord[0], coord[1], today, clean);
    }

    DiscoveryOutcome recordRelativeTile(int rx, int ry, String today, String optionalBaseName) {
        lastRx = rx;
        lastRy = ry;
        String key = rx + "," + ry;
        Tile existing = tiles.get(key);
        if (existing != null) {
            existing.visitCount++;
            if (optionalBaseName != null && !optionalBaseName.trim().isEmpty()) {
                existing.baseName = optionalBaseName.trim();
            }
            return new DiscoveryOutcome(false, existing, 0, 0, false, "");
        }
        if (tiles.size() >= MAX_CELLS) {
            return new DiscoveryOutcome(false, null, 0, 0, false, "足跡地圖已滿載榮耀！");
        }
        Tile created = new Tile(rx, ry, optionalBaseName, today, 1);
        tiles.put(key, created);
        boolean eggBonus = (tiles.size() % 5 == 0);
        String msg = "🗺️ 踩破全新足跡迷霧（第 " + tiles.size() + " 格）"
            + (created.isNamedBase() ? "【" + created.baseName + "】" : "")
            + "！首探寶箱 +" + NEW_TILE_COINS + " 金幣、+" + NEW_TILE_STARS + " 星砂"
            + (eggBonus ? "，還挖出一顆霧林神祕蛋！" : "！");
        return new DiscoveryOutcome(true, created, NEW_TILE_COINS, NEW_TILE_STARS, eggBonus, msg);
    }

    boolean nameTile(int rx, int ry, String rawName) {
        Tile t = tiles.get(rx + "," + ry);
        if (t == null) return false;
        String clean = rawName == null ? "" : rawName.trim();
        if (clean.length() > 14) clean = clean.substring(0, 14);
        t.baseName = clean;
        return true;
    }

    private int[] nextSpiralOpenCoord() {
        if (!tiles.containsKey("0,0")) return new int[]{0, 0};
        for (int radius = 1; radius <= 12; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
                    if (!tiles.containsKey(dx + "," + dy)) {
                        return new int[]{dx, dy};
                    }
                }
            }
        }
        return new int[]{tiles.size(), 0};
    }
}
