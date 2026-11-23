package com.cornming.walkadventure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 家庭小隊護照與雙軌模式狀態（純本機、無須帳號）：
 * 支援同一台手機一鍵切換「平日日常累積」與「親子出任務」、管理 1～6 位家庭成員與防低頭口袋模式。
 */
final class FamilyProfile {
    static final int MAX_MEMBERS = 6;
    static final int AVATAR_COUNT = 6;

    static final String MODE_WEEKDAY = "WEEKDAY";
    static final String MODE_FAMILY = "FAMILY";

    static final String ROLE_ADULT = "ADULT";
    static final String ROLE_CHILD = "CHILD";

    static final int[] PRESET_COLORS = {
        0xFF4A8A56, // 森綠
        0xFFE07A3F, // 暖橘
        0xFF4B82C9, // 晴藍
        0xFFD65C7A, // 莓紅
        0xFF8C63B8, // 星紫
        0xFFD49A2A  // 陽光金
    };

    static final String[] AVATAR_LABELS = {
        "探險帽",
        "蝴蝶結",
        "小恐龍",
        "星星冠",
        "小貓耳",
        "小熊耳"
    };

    static final class Member {
        final String id;
        String name;
        int avatarIndex;
        int colorRgb;
        String birthday; // MM-dd 或空字串
        String role;     // ADULT 或 CHILD

        Member(String id, String name, int avatarIndex, int colorRgb, String birthday, String role) {
            this.id = id;
            this.name = sanitizeName(name, "探險隊員");
            this.avatarIndex = Math.max(0, Math.min(AVATAR_COUNT - 1, avatarIndex));
            this.colorRgb = colorRgb;
            this.birthday = WorldClock.normalizeBirthdayInput(birthday);
            this.role = ROLE_CHILD.equals(role) ? ROLE_CHILD : ROLE_ADULT;
        }
    }

    String playMode = MODE_WEEKDAY;
    boolean pocketMode = false;
    long pocketStepsCollected = 0L;
    boolean autoVaultBigRewards = true;

    final List<Member> members = new ArrayList<>();
    final Set<String> activeSquadIds = new LinkedHashSet<>();

    FamilyProfile() {
        ensureDefaultMember();
    }

    void ensureDefaultMember() {
        if (members.isEmpty()) {
            Member leader = new Member("m1", "探險隊長", 0, PRESET_COLORS[0], "", ROLE_ADULT);
            members.add(leader);
            activeSquadIds.add(leader.id);
        }
        pruneInvalidSquadIds();
    }

    boolean isFamilyMode() {
        return MODE_FAMILY.equals(playMode);
    }

    void setFamilyMode(boolean enabled) {
        playMode = enabled ? MODE_FAMILY : MODE_WEEKDAY;
        ensureDefaultMember();
    }

    void toggleMode() {
        setFamilyMode(!isFamilyMode());
    }

    void startPocketMode() {
        pocketMode = true;
        pocketStepsCollected = 0L;
    }

    long finishPocketMode() {
        long collected = pocketStepsCollected;
        pocketMode = false;
        pocketStepsCollected = 0L;
        return collected;
    }

    void recordPocketSteps(long deltaSteps) {
        if (pocketMode && deltaSteps > 0) {
            pocketStepsCollected += deltaSteps;
        }
    }

    AdventureState.ActionResult addMember(String name, int avatarIndex, int colorRgb, String birthday, String role) {
        if (members.size() >= MAX_MEMBERS) {
            return new AdventureState.ActionResult(false, "小隊最多可建立 " + MAX_MEMBERS + " 位成員");
        }
        String trimmed = sanitizeName(name, "");
        if (trimmed.isEmpty()) {
            return new AdventureState.ActionResult(false, "請輸入成員暱稱");
        }
        String nextId = generateNextMemberId();
        Member m = new Member(nextId, trimmed, avatarIndex, colorRgb, birthday, role);
        members.add(m);
        activeSquadIds.add(m.id);
        return new AdventureState.ActionResult(true, "👨‍👩‍👧 已加入小隊成員「" + m.name + "」！");
    }

    AdventureState.ActionResult updateMember(
        String id,
        String name,
        int avatarIndex,
        int colorRgb,
        String birthday,
        String role
    ) {
        Member target = findMember(id);
        if (target == null) {
            return new AdventureState.ActionResult(false, "找不到這位成員");
        }
        String trimmed = sanitizeName(name, "");
        if (trimmed.isEmpty()) {
            return new AdventureState.ActionResult(false, "暱稱不可空白");
        }
        target.name = trimmed;
        target.avatarIndex = Math.max(0, Math.min(AVATAR_COUNT - 1, avatarIndex));
        target.colorRgb = colorRgb;
        target.birthday = WorldClock.normalizeBirthdayInput(birthday);
        target.role = ROLE_CHILD.equals(role) ? ROLE_CHILD : ROLE_ADULT;
        return new AdventureState.ActionResult(true, "已更新成員「" + target.name + "」的護照資料");
    }

    AdventureState.ActionResult removeMember(String id) {
        if (members.size() <= 1) {
            return new AdventureState.ActionResult(false, "小隊至少需要保留一位成員");
        }
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).id.equals(id)) {
                String removedName = members.get(i).name;
                members.remove(i);
                activeSquadIds.remove(id);
                pruneInvalidSquadIds();
                return new AdventureState.ActionResult(true, "已移除成員「" + removedName + "」");
            }
        }
        return new AdventureState.ActionResult(false, "找不到這位成員");
    }

    boolean toggleSquadMember(String id) {
        if (findMember(id) == null) return false;
        if (activeSquadIds.contains(id)) {
            if (activeSquadIds.size() <= 1) return false; // 至少留一位出隊
            activeSquadIds.remove(id);
            return true;
        }
        activeSquadIds.add(id);
        return true;
    }

    Member findMember(String id) {
        if (id == null) return null;
        for (Member m : members) {
            if (m.id.equals(id)) return m;
        }
        return null;
    }

    List<Member> activeSquadMembers() {
        ensureDefaultMember();
        List<Member> out = new ArrayList<>();
        for (Member m : members) {
            if (activeSquadIds.contains(m.id)) {
                out.add(m);
            }
        }
        if (out.isEmpty() && !members.isEmpty()) {
            out.add(members.get(0));
        }
        return Collections.unmodifiableList(out);
    }

    String formatActiveSquadLabel() {
        List<Member> active = activeSquadMembers();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < active.size(); i++) {
            if (i > 0) sb.append("＋");
            sb.append(active.get(i).name);
        }
        return sb.toString();
    }

    /** 檢查今天是否有小隊成員生日，若有回傳第一位壽星姓名，否則回傳空字串。 */
    String birthdayCelebrantName(WorldClock.Snapshot snap) {
        if (snap == null) return "";
        for (Member m : members) {
            if (WorldClock.matchesBirthday(m.birthday, snap)) {
                return m.name;
            }
        }
        return "";
    }

    boolean hasChildMember() {
        for (Member m : members) {
            if (ROLE_CHILD.equals(m.role)) return true;
        }
        return false;
    }

    private void pruneInvalidSquadIds() {
        Set<String> validIds = new LinkedHashSet<>();
        for (Member m : members) {
            validIds.add(m.id);
        }
        activeSquadIds.retainAll(validIds);
        if (activeSquadIds.isEmpty() && !members.isEmpty()) {
            activeSquadIds.add(members.get(0).id);
        }
    }

    private String generateNextMemberId() {
        int maxNum = 0;
        for (Member m : members) {
            if (m.id != null && m.id.startsWith("m")) {
                try {
                    int n = Integer.parseInt(m.id.substring(1));
                    if (n > maxNum) maxNum = n;
                } catch (NumberFormatException ignored) {
                    // 忽略非標準 ID
                }
            }
        }
        return "m" + (maxNum + 1);
    }

    private static String sanitizeName(String raw, String fallback) {
        if (raw == null) return fallback;
        String clean = raw.trim().replace('\n', ' ').replace('\r', ' ');
        if (clean.length() > 12) {
            clean = clean.substring(0, 12);
        }
        return clean.isEmpty() ? fallback : clean;
    }
}
