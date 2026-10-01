// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.shadowsoffire.apotheosis.affix.AffixType;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRule;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only deterministic test and runtime-preflight commands.
 *
 * <p>Literal rarity/stage children intentionally keep Brigadier tab completion self-documenting.
 * Generation calls are contained so a datapack/ABI failure in the diagnostic surface reports the
 * failure instead of terminating the integrated/dedicated server tick loop.</p>
 */
@EventBusSubscriber(modid = ApothicAscension.MODID)
public final class AscensionCommands {
    static record RaritySpec(String key, int sortIndex, int statRolls, int basicEffectRolls, int abilityRolls) {
        int expectedAffixes() {
            if (statRolls < 0 || basicEffectRolls < 0 || abilityRolls < 0) return -1;
            return statRolls + basicEffectRolls + abilityRolls;
        }
        boolean hasAuthoredRollProfile() {
            return expectedAffixes() >= 0;
        }
    }
    private record ProbeResult(int attempted, int succeeded, String firstFailure) {}

    static final RaritySpec[] RARITIES = {
        new RaritySpec("common", 300, -1, -1, -1),
        new RaritySpec("uncommon", 400, -1, -1, -1),
        new RaritySpec("rare", 500, -1, -1, -1),
        new RaritySpec("epic", 600, -1, -1, -1),
        new RaritySpec("mythic", 700, -1, -1, -1),
        new RaritySpec("legendary", 800, 3, 1, 1),
        new RaritySpec("ancient", 900, 3, 2, 1),
        new RaritySpec("forgotten", 1000, 3, 2, 1),
        new RaritySpec("primal", 1100, 4, 2, 1),
        new RaritySpec("stellar", 1200, 4, 2, 1),
        new RaritySpec("divine", 1300, 4, 2, 2),
        new RaritySpec("esoteric", 1400, 4, 2, 2),
        new RaritySpec("cataclysmic", 1500, 5, 2, 2),
        new RaritySpec("abyssal", 1600, 5, 3, 2),
        new RaritySpec("empyrean", 1700, 5, 3, 2),
        new RaritySpec("paracausal", 1800, 6, 3, 2),
        new RaritySpec("transcendent", 1900, 6, 3, 3),
        new RaritySpec("apotheotic", 2000, 7, 3, 3)
    };

    private AscensionCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("apothic-ascension")
            .requires(source -> source.hasPermission(2));

        LiteralArgumentBuilder<CommandSourceStack> roll = Commands.literal("roll");
        for (String itemType : supportedItemTypes()) {
            LiteralArgumentBuilder<CommandSourceStack> itemNode = Commands.literal(itemType);
            for (RaritySpec spec : RARITIES) {
                LiteralArgumentBuilder<CommandSourceStack> rarityNode = Commands.literal(spec.key())
                    .executes(ctx -> rollTyped(ctx.getSource(), itemType, spec, 1));
                rarityNode.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                    .executes(ctx -> rollTyped(ctx.getSource(), itemType, spec,
                        IntegerArgumentType.getInteger(ctx, "count"))));
                itemNode.then(rarityNode);
            }
            roll.then(itemNode);
        }
        root.then(roll);

        LiteralArgumentBuilder<CommandSourceStack> stage = Commands.literal("stage");
        stage.then(Commands.literal("get").executes(ctx -> stageGet(ctx.getSource())));
        stage.then(Commands.literal("auto").executes(ctx -> stageAuto(ctx.getSource())));
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        for (AscensionStage value : AscensionStage.values()) {
            set.then(Commands.literal(value.key()).executes(ctx -> stageSet(ctx.getSource(), value)));
        }
        stage.then(set);
        root.then(stage);

        LiteralArgumentBuilder<CommandSourceStack> probe = Commands.literal("probe");
        probe.then(Commands.literal("registry").executes(ctx -> probeRegistry(ctx.getSource())));

        LiteralArgumentBuilder<CommandSourceStack> probeKit = Commands.literal("kit");
        LiteralArgumentBuilder<CommandSourceStack> probeBoss = Commands.literal("boss");
        LiteralArgumentBuilder<CommandSourceStack> probeStress = Commands.literal("stress");
        for (RaritySpec spec : RARITIES) {
            probeKit.then(Commands.literal(spec.key()).executes(ctx -> probeExact(ctx.getSource(), spec, false)));
            probeBoss.then(Commands.literal(spec.key()).executes(ctx -> probeExact(ctx.getSource(), spec, true)));
            LiteralArgumentBuilder<CommandSourceStack> stressRarity = Commands.literal(spec.key())
                .executes(ctx -> probeStress(ctx.getSource(), spec, 8));
            stressRarity.then(Commands.argument("iterations", IntegerArgumentType.integer(1, 32))
                .executes(ctx -> probeStress(ctx.getSource(), spec, IntegerArgumentType.getInteger(ctx, "iterations"))));
            probeStress.then(stressRarity);
        }
        probe.then(probeKit);
        probe.then(probeBoss);
        probe.then(probeStress);
        root.then(probe);

        LiteralArgumentBuilder<CommandSourceStack> combat = Commands.literal("combat");
        combat.then(Commands.literal("get").executes(ctx -> combatGet(ctx.getSource())));
        combat.then(Commands.literal("clear").executes(ctx -> combatClear(ctx.getSource())));
        root.then(combat);

        event.getDispatcher().register(root);
    }

    private static String[] supportedItemTypes() {
        return new String[] {
            "kit", "sword", "axe", "pickaxe", "shovel", "shears", "bow", "crossbow", "trident", "shield",
            "helmet", "chestplate", "leggings", "boots"
        };
    }

    private static ItemStack itemBase(String itemType) {
        return switch (itemType) {
            case "sword" -> new ItemStack(Items.NETHERITE_SWORD);
            case "axe" -> new ItemStack(Items.NETHERITE_AXE);
            case "pickaxe" -> new ItemStack(Items.NETHERITE_PICKAXE);
            case "shovel" -> new ItemStack(Items.NETHERITE_SHOVEL);
            case "shears" -> new ItemStack(Items.SHEARS);
            case "bow" -> new ItemStack(Items.BOW);
            case "crossbow" -> new ItemStack(Items.CROSSBOW);
            case "trident" -> new ItemStack(Items.TRIDENT);
            case "shield" -> new ItemStack(Items.SHIELD);
            case "helmet" -> new ItemStack(Items.NETHERITE_HELMET);
            case "chestplate" -> new ItemStack(Items.NETHERITE_CHESTPLATE);
            case "leggings" -> new ItemStack(Items.NETHERITE_LEGGINGS);
            case "boots" -> new ItemStack(Items.NETHERITE_BOOTS);
            default -> ItemStack.EMPTY;
        };
    }

    private static int rollTyped(CommandSourceStack source, String itemType, RaritySpec spec, int count)
        throws CommandSyntaxException {
        if ("kit".equals(itemType)) return rollKit(source, spec, count);
        ServerPlayer player = source.getPlayerOrException();
        LootRarity rarity = resolveRarity(spec);
        if (rarity == null) {
            player.sendSystemMessage(Component.literal("[Apothic Ascension] Rarity is not loaded: " + spec.key()));
            return 0;
        }
        ItemStack base = itemBase(itemType);
        if (base.isEmpty()) {
            player.sendSystemMessage(Component.literal("[Apothic Ascension] Unsupported item type: " + itemType));
            return 0;
        }
        GenContext gen = GenContext.forPlayer(player);
        int made = 0;
        String failure = null;
        for (int i = 0; i < count; i++) {
            try {
                ItemStack stack = LootController.createLootItem(base.copy(), rarity, gen);
                int expected = expectedAffixCapacity(base, rarity, spec, gen);
                RuntimeAffixInspector.Result integrity = RuntimeAffixInspector.inspect(stack, spec.sortIndex(), expected);
                if (!integrity.complete()) {
                    failure = "generated stack integrity: " + integrity.failure();
                    break;
                }
                if (player.getInventory().add(stack)) made++;
            }
            catch (RuntimeException ex) {
                failure = describeFailure(ex);
                break;
            }
        }
        if (failure != null) {
            player.sendSystemMessage(Component.literal("[Apothic Ascension] Roll failed safely for "
                + title(spec.key()) + " " + itemType + ": " + failure));
            return 0;
        }
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Rolled " + made + "/" + count + " exact "
            + title(spec.key()) + " " + itemType + (count == 1 ? "." : "s.")));
        return made == count ? 1 : 0;
    }

    private static int rollKit(CommandSourceStack source, RaritySpec spec, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LootRarity rarity = resolveRarity(spec);
        if (rarity == null) {
            player.sendSystemMessage(Component.literal("[Apothic Ascension] Rarity is not loaded: " + spec.key()));
            return 0;
        }

        GenContext gen = GenContext.forPlayer(player);
        ItemStack[] bases = fullKitBases();
        int made = 0;
        String failure = null;
        outer: for (int kitIndex = 0; kitIndex < count; kitIndex++) {
            for (ItemStack base : bases) {
                try {
                    ItemStack stack = LootController.createLootItem(base.copy(), rarity, gen);
                    int expected = expectedAffixCapacity(base, rarity, spec, gen);
                    RuntimeAffixInspector.Result integrity = RuntimeAffixInspector.inspect(
                        stack, spec.sortIndex(), expected);
                    if (!integrity.complete()) {
                        failure = "generated stack integrity: " + integrity.failure();
                        break outer;
                    }
                    if (player.getInventory().add(stack)) made++;
                }
                catch (RuntimeException ex) {
                    failure = describeFailure(ex);
                    break outer;
                }
            }
        }

        if (failure != null) {
            player.sendSystemMessage(Component.literal("[Apothic Ascension] Kit roll failed safely for "
                + title(spec.key()) + ": " + failure));
            return 0;
        }
        int expectedTotal = bases.length * count;
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Rolled " + count + " kit"
            + (count == 1 ? "" : "s") + ": " + made + "/" + expectedTotal + " exact " + title(spec.key()) + " pieces."));
        return made == expectedTotal ? 1 : 0;
    }

    private static int probeRegistry(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<LootRarity> loaded = RarityRegistry.getSortedRarities();
        int total = loaded == null ? 0 : loaded.size();
        int expectedLoaded = 0;
        StringBuilder missing = new StringBuilder();
        for (RaritySpec spec : RARITIES) {
            if (resolveRarity(spec) != null) {
                expectedLoaded++;
            }
            else {
                if (!missing.isEmpty()) missing.append(", ");
                missing.append(spec.key());
            }
        }

        String suffix = missing.isEmpty() ? "all expected rarities present" : "missing: " + missing;
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Rarity registry: " + total
            + " total, " + expectedLoaded + "/" + RARITIES.length + " expected loaded; " + suffix + "."));
        return missing.isEmpty() ? 1 : 0;
    }

    private static int probeExact(CommandSourceStack source, RaritySpec spec, boolean bossProfile) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LootRarity rarity = resolveRarity(spec);
        if (rarity == null) {
            player.sendSystemMessage(Component.literal("[Apothic Ascension] Probe failed: rarity is not loaded: " + spec.key()));
            return 0;
        }

        GenContext gen = GenContext.forPlayer(player);
        ItemStack[] bases = bossProfile ? bossProfileBases() : fullKitBases();
        ProbeResult result = runProbe(gen, rarity, bases, spec);
        String profile = bossProfile ? "boss-equipment" : "full-kit";
        String msg = "[Apothic Ascension] " + title(spec.key()) + " " + profile + " probe: "
            + result.succeeded() + "/" + result.attempted() + " complete";
        if (result.firstFailure() != null) msg += "; first failure: " + result.firstFailure();
        else if (result.succeeded() != result.attempted()) msg += "; one or more categories returned an empty stack";
        else msg += "; PASS";
        player.sendSystemMessage(Component.literal(msg + "."));
        if (bossProfile) {
            AscensionRarity ascension = AscensionRarity.bySortIndex(spec.sortIndex());
            if (ascension != null) {
                player.sendSystemMessage(Component.literal("[Apothic Ascension] " + title(spec.key())
                    + " boss pressure: EHP x" + trim(ascension.bossEffectiveHealthMultiplier())
                    + ", outgoing x" + trim(ascension.bossDamageMultiplier())
                    + ", post-mitigation hit cap " + trim(ascension.bossSingleHitCapFraction() * 100.0D) + "% max HP."));
            }
        }
        return result.succeeded() == result.attempted() ? 1 : 0;
    }

    private static ProbeResult runProbe(GenContext gen, LootRarity rarity, ItemStack[] bases, RaritySpec spec) {
        int succeeded = 0;
        String failure = null;
        for (ItemStack base : bases) {
            try {
                ItemStack stack = LootController.createLootItem(base, rarity, gen);
                int expected = expectedAffixCapacity(base, rarity, spec, gen);
                RuntimeAffixInspector.Result integrity = RuntimeAffixInspector.inspect(
                    stack, spec.sortIndex(), expected);
                if (integrity.complete()) succeeded++;
                else if (failure == null) failure = integrity.failure();
            }
            catch (RuntimeException ex) {
                if (failure == null) failure = describeFailure(ex);
            }
        }
        return new ProbeResult(bases.length, succeeded, failure);
    }

    private static int probeStress(CommandSourceStack source, RaritySpec spec, int iterations) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LootRarity rarity = resolveRarity(spec);
        if (rarity == null) {
            player.sendSystemMessage(Component.literal("[Apothic Ascension] Stress probe failed: rarity is not loaded: " + spec.key()));
            return 0;
        }

        GenContext gen = GenContext.forPlayer(player);
        int attempted = 0;
        int complete = 0;
        String firstFailure = null;
        for (int i = 0; i < iterations; i++) {
            ProbeResult result = runProbe(gen, rarity, fullKitBases(), spec);
            attempted += result.attempted();
            complete += result.succeeded();
            if (firstFailure == null && result.firstFailure() != null) firstFailure = result.firstFailure();
        }

        String message = "[Apothic Ascension] " + title(spec.key()) + " stress probe: "
            + complete + "/" + attempted + " structurally complete across " + iterations + " full-kit iteration"
            + (iterations == 1 ? "" : "s");
        if (firstFailure == null && complete == attempted) message += "; PASS";
        else if (firstFailure != null) message += "; first failure: " + firstFailure;
        else message += "; one or more generated stacks failed integrity";
        player.sendSystemMessage(Component.literal(message + "."));
        return complete == attempted ? 1 : 0;
    }

    static ItemStack[] bossProfileBases() {
        return new ItemStack[] {
            new ItemStack(Items.NETHERITE_SWORD),
            new ItemStack(Items.BOW),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.NETHERITE_HELMET),
            new ItemStack(Items.NETHERITE_CHESTPLATE),
            new ItemStack(Items.NETHERITE_LEGGINGS),
            new ItemStack(Items.NETHERITE_BOOTS)
        };
    }

    static ItemStack[] fullKitBases() {
        return new ItemStack[] {
            new ItemStack(Items.NETHERITE_SWORD),
            new ItemStack(Items.NETHERITE_AXE),
            new ItemStack(Items.NETHERITE_PICKAXE),
            new ItemStack(Items.NETHERITE_SHOVEL),
            new ItemStack(Items.SHEARS),
            new ItemStack(Items.BOW),
            new ItemStack(Items.CROSSBOW),
            new ItemStack(Items.TRIDENT),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.NETHERITE_HELMET),
            new ItemStack(Items.NETHERITE_CHESTPLATE),
            new ItemStack(Items.NETHERITE_LEGGINGS),
            new ItemStack(Items.NETHERITE_BOOTS)
        };
    }

    /**
     * Computes the structural affix count this item is expected to receive from the exact effective
     * rule list Apotheosis will execute for its loot category. Category-specific rarity overrides
     * are authoritative: bows, armor, shields, breakers and shears intentionally diverge from the
     * generic rarity profile at some tiers.
     *
     * <p>Each requested type is then capped by the candidates available before selection. This
     * preserves the original exhaustion invariant: a category is not failed merely because it has
     * fewer legal candidates than requested, but generation must not fall below the capacity that
     * actually exists for its effective rule list.</p>
     */
    static int expectedAffixCapacity(ItemStack base, LootRarity rarity, RaritySpec spec, GenContext gen) {
        if (!spec.hasAuthoredRollProfile()) return -1;

        int requestedStat = 0;
        int requestedBasic = 0;
        int requestedAbility = 0;
        LootCategory category = LootCategory.forItem(base);
        for (LootRule rule : rarity.getRules(category)) {
            if (!(rule instanceof LootRule.AffixLootRule affixRule)) continue;
            switch (affixRule.type()) {
                case STAT -> requestedStat++;
                case BASIC_EFFECT -> requestedBasic++;
                case ABILITY -> requestedAbility++;
            }
        }

        int stat = Math.min(requestedStat, LootController.getWeightedAffixes(base, rarity, AffixType.STAT, gen).size());
        int basic = Math.min(requestedBasic, LootController.getWeightedAffixes(base, rarity, AffixType.BASIC_EFFECT, gen).size());
        int ability = Math.min(requestedAbility, LootController.getWeightedAffixes(base, rarity, AffixType.ABILITY, gen).size());
        return stat + basic + ability;
    }

    static String describeFailure(Throwable ex) {
        String name = ex.getClass().getSimpleName();
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return name;
        if (message.length() > 180) message = message.substring(0, 180) + "…";
        return name + ": " + message;
    }

    private static int stageGet(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscensionStage effective = AscensionStageTracker.getStored(player);
        AscensionStage progression = AscensionStageTracker.getProgressionStored(player);
        String mode = AscensionStageTracker.hasDebugOverride(player) ? "debug override" : "automatic";
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Stage: " + title(effective.key())
            + " (mode: " + mode + ", progression: " + title(progression.key()) + ")"
            + " | hostile damage x" + trim(effective.hostileDamageMultiplier())
            + " | hostile effective health x" + trim(effective.hostileEffectiveHealthMultiplier())));
        return 1;
    }

    private static int stageSet(CommandSourceStack source, AscensionStage stage) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscensionStageTracker.setDebugOverride(player, stage);
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Debug stage locked to " + title(stage.key())
            + ". Use /apothic-ascension stage auto to resume progression."));
        return 1;
    }

    private static int stageAuto(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscensionStage stage = AscensionStageTracker.clearDebugOverride(player);
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Automatic progression restored. Effective stage: "
            + title(stage.key()) + "."));
        return 1;
    }

    private static int combatGet(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CombatTelemetry.Snapshot s = CombatTelemetry.snapshot(player);
        if (!s.present()) {
            player.sendSystemMessage(Component.literal("[Apothic Ascension] No Ascension boss combat telemetry is available."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Combat telemetry: " + title(s.rarity())
            + " boss, stage " + title(s.stage()) + ", " + trim(s.elapsedTicks() / 20.0D) + "s"
            + " | outgoing " + s.outgoingHits() + " hits / " + trim(s.outgoingHealthLoss()) + " health lost"
            + " (" + s.cappedHits() + " capped, max " + trim(s.maxOutgoingHealthLoss()) + ")"
            + " | incoming " + s.incomingHits() + " hits / " + trim(s.incomingHealthLoss()) + " health lost"
            + " (max " + trim(s.maxIncomingHealthLoss()) + ")."));
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Boss max HP " + trim(s.bossMaxHealth())
            + " | outgoing post-mitigation before cap " + trim(s.outgoingPreCap())
            + " (max " + trim(s.maxOutgoingPreCap()) + ")"
            + " | allowed after cap before absorption " + trim(s.outgoingAllowed())
            + " (max " + trim(s.maxOutgoingAllowed()) + ")"
            + " | incoming allowed before absorption " + trim(s.incomingAllowed())
            + " (max " + trim(s.maxIncomingAllowed()) + ")"
            + " | boss died=" + s.bossDied() + ", player died=" + s.playerDied() + "."));
        return 1;
    }

    private static int combatClear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean removed = CombatTelemetry.clear(player);
        player.sendSystemMessage(Component.literal("[Apothic Ascension] Combat telemetry "
            + (removed ? "cleared." : "was already empty.")));
        return 1;
    }

    static LootRarity resolveRarity(RaritySpec spec) {
        return spec == null ? null : RarityResolver.knownRarity(spec.key());
    }

    static String title(String key) {
        if (key == null || key.isEmpty()) return "Unknown";
        return Character.toUpperCase(key.charAt(0)) + key.substring(1).replace('_', ' ');
    }

    static String trim(double value) {
        if (value == Math.rint(value)) return Long.toString(Math.round(value));
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
