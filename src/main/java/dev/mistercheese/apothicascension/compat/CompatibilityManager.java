// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import com.mojang.logging.LogUtils;
import dev.mistercheese.apothicascension.ApothicAscension;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.slf4j.Logger;

/** Loader-facing bridge for the pure compatibility resolver. */
@EventBusSubscriber(modid = ApothicAscension.MODID)
public final class CompatibilityManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[Apothic Ascension/Compat]";
    private static final AtomicReference<CompatibilitySnapshot> CURRENT = new AtomicReference<>();

    private CompatibilityManager() {}

    public static CompatibilitySnapshot initialize() {
        return refresh("startup");
    }

    public static CompatibilitySnapshot current() {
        CompatibilitySnapshot snapshot = CURRENT.get();
        return snapshot != null ? snapshot : refresh("lazy-init");
    }

    public static CompatibilitySnapshot refresh(String phase) {
        CompatibilitySnapshot next = CompatibilityResolver.resolve(discoverVersions());
        CompatibilitySnapshot previous = CURRENT.getAndSet(next);
        boolean changed = previous == null || !previous.loadedVersions().equals(next.loadedVersions())
            || !previous.providers().equals(next.providers())
            || !previous.decisions().equals(next.decisions());
        logSnapshot(phase, next, changed);
        return next;
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) refresh("datapack-reload");
    }

    private static Map<String, String> discoverVersions() {
        Set<String> modIds = new LinkedHashSet<>();
        for (CompatibilityTarget target : CompatibilityRegistry.targets()) {
            modIds.add(target.modId());
        }
        for (CompatibilityProvider provider : CompatibilityRegistry.providers()) {
            modIds.add(provider.modId());
        }

        Map<String, String> versions = new LinkedHashMap<>();
        ModList mods = ModList.get();
        for (String modId : modIds) {
            mods.getModContainerById(modId).ifPresent(container ->
                versions.put(modId, container.getModInfo().getVersion().toString()));
        }
        return versions;
    }

    private static void logSnapshot(String phase, CompatibilitySnapshot snapshot, boolean changed) {
        LOGGER.info("{} phase={} build={} policy={} specialists={} decisions={} degraded={} changed={}",
            PREFIX, phase, snapshot.buildId(), snapshot.policyRevision(), snapshot.providers().size(),
            snapshot.decisions().size(), snapshot.degradedCount(), changed);
        for (CompatibilityProviderStatus provider : snapshot.providers()) {
            LOGGER.info("{} {}", PREFIX, provider.diagnostic());
        }
        for (CompatibilityDecision decision : snapshot.decisions()) {
            LOGGER.info("{} {}", PREFIX, decision.diagnostic());
        }
    }
}
