package com.cornming.walkadventure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 走走冒險 2.0 結構化存檔編解碼與舊版（v0.1.0–v0.4.0）無縫遷移模組（純 Java，零外部依賴）。
 * 自帶輕量嚴格 JSON Parser/Writer，確保 Android JVM 單元測試與裝置端表現完全一致。
 */
final class SaveCodec {
    static final int CURRENT_SCHEMA_VERSION = 2;

    static final class MigrationResult {
        final boolean migrated;
        final boolean wasVeteran;
        final String welcomeMessage;

        MigrationResult(boolean migrated, boolean wasVeteran, String welcomeMessage) {
            this.migrated = migrated;
            this.wasVeteran = wasVeteran;
            this.welcomeMessage = welcomeMessage;
        }
    }

    private SaveCodec() {}

    /**
     * 確保舊版（v0.1.0–v0.4.0）或全新安裝的存檔正確初始化 v2.0 狀態，並保證冪等（重複呼叫不重複發獎）。
     */
    static MigrationResult migrateIfNeeded(
        StepProgress progress,
        AdventureState state,
        boolean hasExistingV2File,
        String today
    ) {
        if (progress == null || state == null) {
            return new MigrationResult(false, false, "");
        }
        String safeToday = today != null ? today : "";
        state.family.ensureDefaultMember();
        state.syncLegacyCompanionsIntoV2(safeToday);

        if (hasExistingV2File && state.migratedFrom != null && !state.migratedFrom.isEmpty()) {
            // 已經完成過遷移，只做狀態自我修正即可（冪等保護）
            state.syncV2EquippedMirror();
            return new MigrationResult(false, state.pioneerBadge, "");
        }

        boolean hasPriorProgress = progress.lifetime > 0L
            || progress.coins > 0L
            || state.bootsLevel > 0
            || state.charmLevel > 0
            || state.compassLevel > 0
            || (state.unlockedCompanionsMask & ~1) != 0;

        if (!hasPriorProgress) {
            // 全新安裝
            state.migratedFrom = "fresh";
            state.pioneerBadge = false;
            if (state.companions.eggs.isEmpty()) {
                state.companions.addEgg(CompanionSystem.EGG_STARTER_MINI, 0);
            }
            return new MigrationResult(
                true,
                false,
                "🌱 歡迎來到《走走冒險 2.0》！蛋巢裡有一顆 500 步就會破殼的迎新小葉蛋，快一起散步孵化吧！"
            );
        }

        // 老玩家（v0.1.0–v0.4.0）無縫升級禮遇：絕不沒收既有金幣或步數！
        boolean hadV04Data = state.bootsLevel > 0
            || state.charmLevel > 0
            || state.compassLevel > 0
            || (state.unlockedCompanionsMask & ~1) != 0
            || state.encountersResolved > 0;
        state.migratedFrom = hadV04Data ? "0.4.0" : "0.3.0";
        state.pioneerBadge = true;

        // 依既有累計步數為已擁有的元老旅伴換算啟動羈絆 XP（最多 360 XP，直接體會成長進化！）
        int starterBond = (int) Math.min(360L, progress.lifetime / 200L);
        for (CompanionSystem.OwnedCompanion oc : state.companions.owned.values()) {
            if (oc.bondXp < starterBond) {
                oc.addBondXp(starterBond - oc.bondXp);
            }
        }

        if (hadV04Data) {
            state.camp.starSand += 50;
            state.camp.snacks += 5;
            state.camp.warmBlankets += 1;
            if (state.companions.hasFreeEggSlot()) {
                state.companions.addEgg(CompanionSystem.EGG_STAR, 3);
            }
            if (state.companions.hasFreeEggSlot()) {
                state.companions.addEgg(CompanionSystem.EGG_NIGHT, 1);
            }
        } else {
            state.camp.starSand += 30;
            state.camp.snacks += 3;
            if (state.companions.hasFreeEggSlot()) {
                state.companions.addEgg(CompanionSystem.EGG_MIST, 1);
            }
        }

        state.syncV2EquippedMirror();
        state.appendLog(safeToday, "🏅 升級至走走冒險 2.0！獲頒元老開拓者徽章與營地啟動禮包");
        String welcome = "🏅 歡迎元老冒險者回歸 2.0！原有步數、金幣、裝備與旅伴 100% 保留，"
            + "另贈元老開拓者徽章、星砂、點心與稀有神祕蛋，快去秘密營地展開新冒險吧！";
        return new MigrationResult(true, true, welcome);
    }

    static String serialize(AdventureState state) {
        if (state == null) return "{}";
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", (long) CURRENT_SCHEMA_VERSION);
        root.put("migratedFrom", nonNull(state.migratedFrom));
        root.put("pioneerBadge", state.pioneerBadge);
        root.put("extraAchievementsMask", (long) state.extraAchievementsMask);
        root.put("campfireUnboxCount", (long) state.campfireUnboxCount);
        root.put("eggsHatchedCount", (long) state.eggsHatchedCount);
        root.put("gardenHarvestCount", (long) state.gardenHarvestCount);
        root.put("lastBirthdayGiftDate", nonNull(state.lastBirthdayGiftDate));
        root.put("lastPenguinSnackDate", nonNull(state.lastPenguinSnackDate));
        root.put("rainbowCollectedMask", (long) state.rainbowCollectedMask);
        root.put("rainbowCompletedSets", (long) state.rainbowCompletedSets);
        root.put("photoHuntsCompleted", (long) state.photoHuntsCompleted);
        root.put("voiceMemoriesCount", (long) state.voiceMemoriesCount);

        // family
        Map<String, Object> fam = new LinkedHashMap<>();
        fam.put("playMode", nonNull(state.family.playMode));
        fam.put("pocketMode", state.family.pocketMode);
        fam.put("pocketStepsCollected", state.family.pocketStepsCollected);
        fam.put("autoVaultBigRewards", state.family.autoVaultBigRewards);
        List<Object> memberList = new ArrayList<>();
        for (FamilyProfile.Member m : state.family.members) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", nonNull(m.id));
            mm.put("name", nonNull(m.name));
            mm.put("avatar", (long) m.avatarIndex);
            mm.put("colorRgb", (long) m.colorRgb);
            mm.put("birthday", nonNull(m.birthday));
            mm.put("role", nonNull(m.role));
            memberList.add(mm);
        }
        fam.put("members", memberList);
        List<Object> squadIds = new ArrayList<>(state.family.activeSquadIds);
        fam.put("activeSquadIds", squadIds);
        root.put("family", fam);

        // vault
        Map<String, Object> vaultObj = new LinkedHashMap<>();
        List<Object> vaultItems = new ArrayList<>();
        for (CampfireVault.VaultItem item : state.vault.items) {
            Map<String, Object> vi = new LinkedHashMap<>();
            vi.put("id", nonNull(item.id));
            vi.put("kind", nonNull(item.kind));
            vi.put("title", nonNull(item.title));
            vi.put("coins", (long) item.coins);
            vi.put("starSand", (long) item.starSand);
            vi.put("eggType", (long) item.eggType);
            vi.put("createdDate", nonNull(item.createdDate));
            vaultItems.add(vi);
        }
        vaultObj.put("items", vaultItems);
        root.put("vault", vaultObj);

        // companions
        Map<String, Object> compObj = new LinkedHashMap<>();
        compObj.put("equippedId", (long) state.companions.equippedId);
        compObj.put("unlockedAccessoriesMask", (long) state.companions.unlockedAccessoriesMask);
        List<Object> ownedList = new ArrayList<>();
        for (CompanionSystem.OwnedCompanion oc : state.companions.owned.values()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", (long) oc.id);
            o.put("bondXp", (long) oc.bondXp);
            o.put("stage", (long) oc.stage);
            o.put("shiny", oc.shiny);
            o.put("accessory", (long) oc.accessory);
            o.put("unlockedDate", nonNull(oc.unlockedDate));
            o.put("lastPetDate", nonNull(oc.lastPetDate));
            ownedList.add(o);
        }
        compObj.put("owned", ownedList);
        List<Object> eggList = new ArrayList<>();
        for (CompanionSystem.EggSlot e : state.companions.eggs) {
            Map<String, Object> eo = new LinkedHashMap<>();
            eo.put("slot", (long) e.slot);
            eo.put("eggType", (long) e.eggType);
            eo.put("stepsWalked", (long) e.stepsWalked);
            eo.put("stepsRequired", (long) e.stepsRequired);
            eo.put("lastWarmDate", nonNull(e.lastWarmDate));
            eo.put("biomeOrigin", (long) e.biomeOrigin);
            eggList.add(eo);
        }
        compObj.put("eggs", eggList);
        root.put("companions", compObj);

        // camp
        Map<String, Object> campObj = new LinkedHashMap<>();
        campObj.put("starSand", (long) state.camp.starSand);
        campObj.put("snacks", (long) state.camp.snacks);
        campObj.put("warmBlankets", (long) state.camp.warmBlankets);
        campObj.put("wood", (long) state.camp.wood);
        campObj.put("petals", (long) state.camp.petals);
        Map<String, Object> blds = new LinkedHashMap<>();
        for (CampState.BuildingDef def : CampState.BUILDINGS) {
            blds.put(def.key, (long) state.camp.buildingLevel(def.id));
        }
        campObj.put("buildings", blds);
        List<Object> plotList = new ArrayList<>();
        for (CampState.GardenPlot gp : state.camp.gardenPlots) {
            Map<String, Object> po = new LinkedHashMap<>();
            po.put("slot", (long) gp.slot);
            po.put("seedId", (long) gp.seedId);
            po.put("waterSteps", (long) gp.waterSteps);
            po.put("requiredSteps", (long) gp.requiredSteps);
            plotList.add(po);
        }
        campObj.put("gardenPlots", plotList);
        root.put("camp", campObj);

        // stories
        Map<String, Object> stObj = new LinkedHashMap<>();
        stObj.put("activeArcId", (long) state.stories.activeArcId);
        stObj.put("activeChapter", (long) state.stories.activeChapter);
        stObj.put("completedArcMask", (long) state.stories.completedArcMask);
        stObj.put("bossClearedMask", (long) state.stories.bossClearedMask);
        stObj.put("bossStepProgress", (long) state.stories.bossStepProgress);
        stObj.put("bossLookUpDone", state.stories.bossLookUpDone);
        root.put("stories", stObj);

        // lookUpQuests
        Map<String, Object> luObj = new LinkedHashMap<>();
        luObj.put("date", nonNull(state.stories.lookUpDate));
        luObj.put("ageFilter", nonNull(state.stories.lookUpAgeFilter));
        luObj.put("offeredIds", toLongList(state.stories.offeredLookUpIds));
        luObj.put("completedIds", toLongList(state.stories.completedLookUpIds));
        luObj.put("totalCompleted", (long) state.stories.totalLookUpCompleted);
        List<Object> noteList = new ArrayList<>();
        for (StoryCatalog.LookUpNote note : state.stories.recentNotes) {
            Map<String, Object> no = new LinkedHashMap<>();
            no.put("date", nonNull(note.date));
            no.put("questTitle", nonNull(note.questTitle));
            no.put("squadLabel", nonNull(note.squadLabel));
            no.put("note", nonNull(note.note));
            noteList.add(no);
        }
        luObj.put("recentNotes", noteList);
        root.put("lookUpQuests", luObj);

        // seasonPassport
        Map<String, Object> spObj = new LinkedHashMap<>();
        spObj.put("seasonKey", nonNull(state.stories.seasonKey));
        spObj.put("stampsMask", (long) state.stories.seasonStampsMask);
        root.put("seasonPassport", spObj);

        // Phase 2 memories
        List<Object> memList = new ArrayList<>();
        for (StoryCatalog.MemoryEntry mem : state.stories.memories) {
            Map<String, Object> mo = new LinkedHashMap<>();
            mo.put("id", nonNull(mem.id));
            mo.put("date", nonNull(mem.date));
            mo.put("title", nonNull(mem.title));
            mo.put("squadLabel", nonNull(mem.squadLabel));
            mo.put("photoPath", nonNull(mem.photoPath));
            mo.put("audioPath", nonNull(mem.audioPath));
            mo.put("doodlePath", nonNull(mem.doodlePath));
            mo.put("note", nonNull(mem.note));
            mo.put("huntId", (long) mem.huntId);
            mo.put("colorName", nonNull(mem.colorName));
            mo.put("dominantHue", (long) mem.dominantHue);
            mo.put("stars", (long) mem.stars);
            mo.put("stepsOnDay", mem.stepsOnDay);
            mo.put("companionId", (long) mem.companionId);
            mo.put("companionStage", (long) mem.companionStage);
            mo.put("companionShiny", mem.companionShiny);
            mo.put("season", (long) mem.season);
            memList.add(mo);
        }
        root.put("memories", memList);

        StringBuilder out = new StringBuilder(2048);
        writeJsonValue(out, root);
        return out.toString();
    }

    /**
     * 將 JSON 字串解析回 AdventureState 的 v2 欄位；遇到無效／損壞 JSON 回傳 false 而不拋例外。
     */
    static boolean deserializeInto(String rawJson, AdventureState state) {
        if (rawJson == null || rawJson.trim().isEmpty() || state == null) return false;
        Object parsed;
        try {
            JsonParser parser = new JsonParser(rawJson);
            parsed = parser.parseValue();
            parser.skipWhitespace();
            if (!parser.isAtEnd()) return false;
        } catch (RuntimeException ignored) {
            return false;
        }
        if (!(parsed instanceof Map)) return false;
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        if (!root.containsKey("schemaVersion")) return false;

        state.migratedFrom = asString(root.get("migratedFrom"), "");
        state.pioneerBadge = asBool(root.get("pioneerBadge"), false);
        state.extraAchievementsMask = asInt(root.get("extraAchievementsMask"), 0);
        state.campfireUnboxCount = Math.max(0, asInt(root.get("campfireUnboxCount"), 0));
        state.eggsHatchedCount = Math.max(0, asInt(root.get("eggsHatchedCount"), 0));
        state.gardenHarvestCount = Math.max(0, asInt(root.get("gardenHarvestCount"), 0));
        state.lastBirthdayGiftDate = asString(root.get("lastBirthdayGiftDate"), "");
        state.lastPenguinSnackDate = asString(root.get("lastPenguinSnackDate"), "");
        state.rainbowCollectedMask = asInt(root.get("rainbowCollectedMask"), 0);
        state.rainbowCompletedSets = Math.max(0, asInt(root.get("rainbowCompletedSets"), 0));
        state.photoHuntsCompleted = Math.max(0, asInt(root.get("photoHuntsCompleted"), 0));
        state.voiceMemoriesCount = Math.max(0, asInt(root.get("voiceMemoriesCount"), 0));

        // family
        Map<String, Object> fam = asMap(root.get("family"));
        if (fam != null) {
            String mode = asString(fam.get("playMode"), FamilyProfile.MODE_WEEKDAY);
            state.family.playMode = FamilyProfile.MODE_FAMILY.equals(mode)
                ? FamilyProfile.MODE_FAMILY : FamilyProfile.MODE_WEEKDAY;
            state.family.pocketMode = asBool(fam.get("pocketMode"), false);
            state.family.pocketStepsCollected = Math.max(0L, asLong(fam.get("pocketStepsCollected"), 0L));
            state.family.autoVaultBigRewards = asBool(fam.get("autoVaultBigRewards"), true);

            List<Object> mList = asList(fam.get("members"));
            if (mList != null && !mList.isEmpty()) {
                state.family.members.clear();
                for (Object item : mList) {
                    Map<String, Object> mm = asMap(item);
                    if (mm == null) continue;
                    String id = asString(mm.get("id"), "");
                    if (id.isEmpty()) continue;
                    state.family.members.add(new FamilyProfile.Member(
                        id,
                        asString(mm.get("name"), "探險隊員"),
                        asInt(mm.get("avatar"), 0),
                        asInt(mm.get("colorRgb"), FamilyProfile.PRESET_COLORS[0]),
                        asString(mm.get("birthday"), ""),
                        asString(mm.get("role"), FamilyProfile.ROLE_ADULT)
                    ));
                    if (state.family.members.size() >= FamilyProfile.MAX_MEMBERS) break;
                }
            }
            List<Object> squadIds = asList(fam.get("activeSquadIds"));
            if (squadIds != null) {
                state.family.activeSquadIds.clear();
                for (Object sid : squadIds) {
                    String s = asString(sid, "");
                    if (!s.isEmpty()) state.family.activeSquadIds.add(s);
                }
            }
            state.family.ensureDefaultMember();
        }

        // vault
        Map<String, Object> vaultObj = asMap(root.get("vault"));
        if (vaultObj != null) {
            state.vault.items.clear();
            List<Object> items = asList(vaultObj.get("items"));
            if (items != null) {
                for (Object o : items) {
                    Map<String, Object> vi = asMap(o);
                    if (vi == null) continue;
                    state.vault.restoreItem(new CampfireVault.VaultItem(
                        asString(vi.get("id"), "v1"),
                        asString(vi.get("kind"), CampfireVault.KIND_CHEST),
                        asString(vi.get("title"), "冒險包裹"),
                        asInt(vi.get("coins"), 0),
                        asInt(vi.get("starSand"), 0),
                        asInt(vi.get("eggType"), -1),
                        asString(vi.get("createdDate"), "")
                    ));
                }
            }
        }

        // companions
        Map<String, Object> compObj = asMap(root.get("companions"));
        if (compObj != null) {
            state.companions.equippedId = Math.max(0, asInt(compObj.get("equippedId"), CompanionSystem.ID_NONE));
            state.companions.unlockedAccessoriesMask = Math.max(1, asInt(compObj.get("unlockedAccessoriesMask"), 1));
            state.companions.owned.clear();
            List<Object> ownedList = asList(compObj.get("owned"));
            if (ownedList != null) {
                for (Object o : ownedList) {
                    Map<String, Object> oc = asMap(o);
                    if (oc == null) continue;
                    int cid = asInt(oc.get("id"), 0);
                    if (CompanionSystem.species(cid) == null) continue;
                    state.companions.owned.put(cid, new CompanionSystem.OwnedCompanion(
                        cid,
                        asInt(oc.get("bondXp"), 0),
                        asInt(oc.get("stage"), 1),
                        asBool(oc.get("shiny"), false),
                        asInt(oc.get("accessory"), 0),
                        asString(oc.get("unlockedDate"), ""),
                        asString(oc.get("lastPetDate"), "")
                    ));
                }
            }
            if (state.companions.equippedId != CompanionSystem.ID_NONE
                && !state.companions.isOwned(state.companions.equippedId)) {
                state.companions.equippedId = CompanionSystem.ID_NONE;
            }
            state.companions.eggs.clear();
            List<Object> eggList = asList(compObj.get("eggs"));
            if (eggList != null) {
                for (Object o : eggList) {
                    Map<String, Object> eo = asMap(o);
                    if (eo == null) continue;
                    if (state.companions.eggs.size() >= CompanionSystem.MAX_EGG_SLOTS) break;
                    int eggType = asInt(eo.get("eggType"), CompanionSystem.EGG_LEAF);
                    CompanionSystem.EggTypeDef def = CompanionSystem.eggTypeDef(eggType);
                    state.companions.eggs.add(new CompanionSystem.EggSlot(
                        asInt(eo.get("slot"), state.companions.eggs.size()),
                        def.type,
                        asInt(eo.get("stepsWalked"), 0),
                        asInt(eo.get("stepsRequired"), def.requiredSteps),
                        asString(eo.get("lastWarmDate"), ""),
                        asInt(eo.get("biomeOrigin"), 0)
                    ));
                }
            }
        }

        // camp
        Map<String, Object> campObj = asMap(root.get("camp"));
        if (campObj != null) {
            state.camp.starSand = Math.max(0, asInt(campObj.get("starSand"), 0));
            state.camp.snacks = Math.max(0, asInt(campObj.get("snacks"), 0));
            state.camp.warmBlankets = Math.max(0, asInt(campObj.get("warmBlankets"), 0));
            state.camp.wood = Math.max(0, asInt(campObj.get("wood"), 0));
            state.camp.petals = Math.max(0, asInt(campObj.get("petals"), 0));

            Map<String, Object> blds = asMap(campObj.get("buildings"));
            if (blds != null) {
                for (CampState.BuildingDef def : CampState.BUILDINGS) {
                    int defaultLv = (def.id == CampState.BLD_CAMPFIRE || def.id == CampState.BLD_GARDEN) ? 1 : 0;
                    state.camp.setBuildingLevel(def.id, asInt(blds.get(def.key), defaultLv));
                }
            }
            List<Object> plotList = asList(campObj.get("gardenPlots"));
            if (plotList != null) {
                for (Object o : plotList) {
                    Map<String, Object> po = asMap(o);
                    if (po == null) continue;
                    int slot = asInt(po.get("slot"), -1);
                    if (slot < 0 || slot >= CampState.GARDEN_PLOT_COUNT) continue;
                    CampState.GardenPlot gp = state.camp.gardenPlots[slot];
                    gp.seedId = Math.max(0, Math.min(CampState.SEEDS.length - 1, asInt(po.get("seedId"), 0)));
                    gp.waterSteps = Math.max(0, asInt(po.get("waterSteps"), 0));
                    gp.requiredSteps = Math.max(0, asInt(po.get("requiredSteps"), 0));
                }
            }
        }

        // stories
        Map<String, Object> stObj = asMap(root.get("stories"));
        if (stObj != null) {
            state.stories.activeArcId = Math.max(0, Math.min(StoryCatalog.ARC_COUNT - 1, asInt(stObj.get("activeArcId"), 0)));
            state.stories.activeChapter = Math.max(1, Math.min(StoryCatalog.CHAPTERS_PER_ARC, asInt(stObj.get("activeChapter"), 1)));
            state.stories.completedArcMask = asInt(stObj.get("completedArcMask"), 0);
            state.stories.bossClearedMask = asInt(stObj.get("bossClearedMask"), 0);
            state.stories.bossStepProgress = Math.max(0, asInt(stObj.get("bossStepProgress"), 0));
            state.stories.bossLookUpDone = asBool(stObj.get("bossLookUpDone"), false);
        }

        // lookUpQuests
        Map<String, Object> luObj = asMap(root.get("lookUpQuests"));
        if (luObj != null) {
            state.stories.lookUpDate = asString(luObj.get("date"), "");
            state.stories.lookUpAgeFilter = asString(luObj.get("ageFilter"), StoryCatalog.AGE_ALL);
            state.stories.offeredLookUpIds.clear();
            readIntList(asList(luObj.get("offeredIds")), state.stories.offeredLookUpIds);
            state.stories.completedLookUpIds.clear();
            readIntList(asList(luObj.get("completedIds")), state.stories.completedLookUpIds);
            state.stories.totalLookUpCompleted = Math.max(0, asInt(luObj.get("totalCompleted"), 0));
            state.stories.recentNotes.clear();
            List<Object> noteList = asList(luObj.get("recentNotes"));
            if (noteList != null) {
                for (Object o : noteList) {
                    Map<String, Object> no = asMap(o);
                    if (no == null) continue;
                    state.stories.recentNotes.add(new StoryCatalog.LookUpNote(
                        asString(no.get("date"), ""),
                        asString(no.get("questTitle"), ""),
                        asString(no.get("squadLabel"), ""),
                        asString(no.get("note"), "")
                    ));
                    if (state.stories.recentNotes.size() >= StoryCatalog.MAX_RECENT_NOTES) break;
                }
            }
        }

        // seasonPassport
        Map<String, Object> spObj = asMap(root.get("seasonPassport"));
        if (spObj != null) {
            state.stories.seasonKey = asString(spObj.get("seasonKey"), "");
            state.stories.seasonStampsMask = asInt(spObj.get("stampsMask"), 0);
        }

        // memories
        state.stories.memories.clear();
        List<Object> memList = asList(root.get("memories"));
        if (memList != null) {
            for (Object o : memList) {
                Map<String, Object> mo = asMap(o);
                if (mo == null) continue;
                state.stories.memories.add(new StoryCatalog.MemoryEntry(
                    asString(mo.get("id"), ""),
                    asString(mo.get("date"), ""),
                    asString(mo.get("title"), ""),
                    asString(mo.get("squadLabel"), ""),
                    asString(mo.get("photoPath"), ""),
                    asString(mo.get("audioPath"), ""),
                    asString(mo.get("doodlePath"), ""),
                    asString(mo.get("note"), ""),
                    asInt(mo.get("huntId"), 0),
                    asString(mo.get("colorName"), ""),
                    asInt(mo.get("dominantHue"), -1),
                    asInt(mo.get("stars"), 1),
                    asLong(mo.get("stepsOnDay"), 0L),
                    asInt(mo.get("companionId"), 0),
                    asInt(mo.get("companionStage"), 1),
                    asBool(mo.get("companionShiny"), false),
                    asInt(mo.get("season"), 0)
                ));
            }
        }

        state.syncV2EquippedMirror();
        return true;
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }

    private static List<Object> toLongList(List<Integer> ints) {
        List<Object> out = new ArrayList<>(ints.size());
        for (Integer v : ints) out.add(v.longValue());
        return out;
    }

    private static void readIntList(List<Object> src, List<Integer> dst) {
        if (src == null) return;
        for (Object o : src) {
            if (o instanceof Number) dst.add(((Number) o).intValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return (o instanceof List) ? (List<Object>) o : null;
    }

    private static String asString(Object o, String fallback) {
        return (o instanceof String) ? (String) o : fallback;
    }

    private static boolean asBool(Object o, boolean fallback) {
        return (o instanceof Boolean) ? (Boolean) o : fallback;
    }

    private static long asLong(Object o, long fallback) {
        return (o instanceof Number) ? ((Number) o).longValue() : fallback;
    }

    private static int asInt(Object o, int fallback) {
        return (o instanceof Number) ? ((Number) o).intValue() : fallback;
    }

    // ──────────────── 輕量純 Java JSON Writer ────────────────

    @SuppressWarnings("unchecked")
    private static void writeJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof String) {
            writeJsonString(sb, (String) value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeJsonString(sb, entry.getKey());
                sb.append(':');
                writeJsonValue(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof List) {
            sb.append('[');
            List<Object> list = (List<Object>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                writeJsonValue(sb, list.get(i));
            }
            sb.append(']');
        } else {
            writeJsonString(sb, value.toString());
        }
    }

    private static void writeJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    // ──────────────── 輕量純 Java JSON Parser ────────────────

    private static final class JsonParser {
        private final String src;
        private int pos = 0;
        private int depth = 0;

        JsonParser(String src) {
            this.src = src;
        }

        boolean isAtEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) throw new IllegalArgumentException("Unexpected EOF");
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
            if (++depth > 32) throw new IllegalArgumentException("Depth limit");
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
                } else if (peek('}')) {
                    pos++;
                    depth--;
                    return map;
                } else {
                    throw new IllegalArgumentException("Expected , or }");
                }
            }
        }

        private List<Object> parseArray() {
            if (++depth > 32) throw new IllegalArgumentException("Depth limit");
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
                } else if (peek(']')) {
                    pos++;
                    depth--;
                    return list;
                } else {
                    throw new IllegalArgumentException("Expected , or ]");
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (isAtEnd()) throw new IllegalArgumentException("Unterminated escape");
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
            throw new IllegalArgumentException("Invalid boolean");
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null");
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) pos++;
            boolean hasDigits = false;
            while (!isAtEnd() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') {
                pos++;
                hasDigits = true;
            }
            if (!hasDigits) throw new IllegalArgumentException("Invalid number");
            boolean isFloat = false;
            if (peek('.')) {
                isFloat = true;
                pos++;
                while (!isAtEnd() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            if (peek('e') || peek('E')) {
                isFloat = true;
                pos++;
                if (peek('+') || peek('-')) pos++;
                while (!isAtEnd() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            String token = src.substring(start, pos);
            return isFloat ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private boolean peek(char ch) {
            return pos < src.length() && src.charAt(pos) == ch;
        }

        private void expect(char ch) {
            if (pos >= src.length() || src.charAt(pos) != ch) {
                throw new IllegalArgumentException("Expected " + ch);
            }
            pos++;
        }
    }
}
