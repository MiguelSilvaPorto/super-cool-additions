package com.sccadd.analysis;

import de.scc.calculator.SccConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * The "soft" part of the analysis fingerprint: everything that should mark the
 * cached analysis as stale (use it right away, refresh in the background) instead
 * of throwing it away (which costs 8-12 minutes on a pack like ATM10).
 *
 * <p>Contains mod versions and the analysis-relevant parts of the SCC config.
 * UI-only settings are ignored, exactly like SCC's own fingerprint ignore list.</p>
 */
public final class FingerprintExtra {
    /** Copy of SCC's own FINGERPRINT_IGNORED: settings that do not change recipes. */
    private static final Set<String> IGNORED = Set.of(
            "maxDepth",
            "maxNodes",
            "maxMillis",
            "autoExpandDepth",
            "subtractInventory",
            "defaultAmount",
            "terminalSort",
            "jeiSearchSync",
            "rememberSearch",
            "lastSearch",
            "recentOrders",
            "showCraftable",
            "craftingRows",
            "wirelessPick",
            "wirelessPickOrder",
            "wirelessStoreAir",
            "jobSound",
            "jobSoundVolume",
            "onlyAvailableCraftable",
            "hiddenCraftables",
            "debugCategory",
            "autoAnalyse",
            "saveAnalysis",
            "analysisMillisPerTick",
            "analysisMillisWhenMenu",
            "analysisMillisWhenOpen",
            "analysisMillisPerFrame",
            "logToFile",
            "logDetail",
            "configVersion",
            "variantComponents",
            "windowPos",
            "windowPinned",
            "windowGui",
            "sounds",
            "windowFade",
            "networkHum",
            "skyObjects",
            "starAnimations",
            "starSounds",
            "mutedSounds",
            "skyWithShaders",
            "blockDesigns",
            "preloadDesigns",
            "designAtlasBase"
    );

    private FingerprintExtra() {
    }

    public static String signature() {
        StringBuilder sb = new StringBuilder(1_024);
        appendMods(sb);
        appendConfig(sb);
        return Long.toHexString(fnv(sb.toString()));
    }

    private static void appendMods(StringBuilder sb) {
        try {
            List<String> mods = new ArrayList<>();
            for (IModInfo mod : ModList.get().getMods()) {
                mods.add(mod.getModId() + "@" + mod.getVersion());
            }
            mods.sort(String::compareTo);
            for (String mod : mods) {
                sb.append(mod).append('\n');
            }
        } catch (Throwable failed) {
            sb.append("mods?\n");
        }
    }

    private static void appendConfig(StringBuilder sb) {
        try {
            SccConfig config = SccConfig.get();
            List<Field> fields = new ArrayList<>();
            for (Field field : SccConfig.class.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)
                        && !field.isSynthetic() && !IGNORED.contains(field.getName())) {
                    fields.add(field);
                }
            }
            fields.sort(Comparator.comparing(Field::getName));
            for (Field field : fields) {
                sb.append(field.getName()).append('=');
                try {
                    sb.append(field.get(config));
                } catch (Throwable failed) {
                    sb.append('?');
                }
                sb.append('\n');
            }
        } catch (Throwable failed) {
            sb.append("config?\n");
        }
    }

    private static long fnv(String text) {
        long hash = -3750763034362895579L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 1099511628211L;
        }
        return hash;
    }
}
