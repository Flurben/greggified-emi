package com.greggifiedemi.testkit;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.search.EmiSearch;

/**
 * Dev-only pack test (never shipped): run inside a real modpack, with or without Greggified EMI. It creates (or
 * reuses) a creative world through the normal Create World screen, so the pack's datapacks load, then times the same
 * EMI scenarios in both cases and, when Greggified EMI is present, drives its features against the pack's recipes.
 * Results go to {@code greggified-testkit/<mode>.json} in the game directory, then the game quits.
 */
@Mod(Testkit.MOD_ID)
public final class Testkit {

    public static final String MOD_ID = "greggifiedemi_testkit";
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "greggified-testkit";
    private static final long SCENARIO_NANOS = 6_000_000_000L;

    private static final List<long[]> frames = new ArrayList<>();
    private static final Map<String, Object> results = new LinkedHashMap<>();
    static final List<String> checks = new ArrayList<>();

    private static boolean enabled;
    private static int state;
    private static int ticks;
    private static long stateStart;
    private static long frameStart;
    private static long screenStart;
    private static long screenNanos;
    private static boolean recording;
    private static String scenario;
    private static long joinedAt;
    private static final List<Double> recipeOpenMs = new ArrayList<>();
    private static final List<Double> searchMs = new ArrayList<>();
    private static int searchIndex;
    private static boolean zoomOffMeasured;
    private static boolean historyOffMeasured;
    private static long searchStart;
    private static List<? extends EmiIngredient> searchBefore;

    public Testkit() {
        enabled = Boolean.getBoolean("greggifiedemi.testkit") || Files.exists(gameDir().resolve("greggified-testkit")
                .resolve("enabled"));

        if (enabled) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                MinecraftForge.EVENT_BUS.addListener(Testkit::onClientTick);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Testkit::onRenderTick);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Testkit::onScreenPre);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, Testkit::onScreenPost);
            });
        }
    }

    static Path gameDir() {
        return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
    }

    static String mode() {
        return ModList.get().isLoaded("greggifiedemi") ? "greggified" : "baseline";
    }

    static void check(boolean ok, String message) {
        LOGGER.info("[testkit] {} {}", ok ? "PASS" : "FAIL", message);
        checks.add((ok ? "PASS " : "FAIL ") + message);
    }

    private static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            frameStart = System.nanoTime();
            screenNanos = -1;
        } else if (recording && screenNanos >= 0) {
            frames.add(new long[] { System.nanoTime() - frameStart, screenNanos });
        }
    }

    private static void onScreenPre(ScreenEvent.Render.Pre event) {
        screenStart = System.nanoTime();
    }

    private static void onScreenPost(ScreenEvent.Render.Post event) {
        screenNanos = System.nanoTime() - screenStart;
    }

    static void moveMouse(double guiX, double guiY) {
        final Minecraft minecraft = Minecraft.getInstance();
        final double scale = minecraft.getWindow().getGuiScale();

        try {
            final Field xpos = ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91507_");
            final Field ypos = ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91508_");
            xpos.setDouble(minecraft.mouseHandler, guiX * scale);
            ypos.setDouble(minecraft.mouseHandler, guiY * scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[testkit] could not move the mouse", e);
        }

        EmiScreenManager.lastMouseX = (int) guiX;
        EmiScreenManager.lastMouseY = (int) guiY;
    }

    private static void next() {
        state++;
        stateStart = System.nanoTime();
        ticks = 0;
    }

    private static void startScenario(String name) {
        scenario = name;
        frames.clear();
        recording = true;
        stateStart = System.nanoTime();
    }

    private static boolean scenarioDone() {
        if (System.nanoTime() - stateStart < SCENARIO_NANOS) {
            return false;
        }

        recording = false;
        results.put("frames: " + scenario, summarize(frames));
        LOGGER.info("[testkit] {} -> {}", scenario, results.get("frames: " + scenario));
        return true;
    }

    private static Map<String, Object> summarize(List<long[]> samples) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("frames", samples.size());
        out.put("frame_ms", stats(samples.stream().mapToLong(s -> s[0]).toArray()));
        out.put("screen_ms", stats(samples.stream().mapToLong(s -> s[1]).toArray()));
        return out;
    }

    private static Map<String, Double> stats(long[] nanos) {
        final Map<String, Double> out = new LinkedHashMap<>();

        if (nanos.length == 0) {
            return out;
        }

        Arrays.sort(nanos);
        out.put("mean", round(Arrays.stream(nanos).average().orElse(0) / 1e6));
        out.put("p50", round(nanos[nanos.length / 2] / 1e6));
        out.put("p95", round(nanos[(int) (nanos.length * 0.95)] / 1e6));
        out.put("p99", round(nanos[(int) (nanos.length * 0.99)] / 1e6));
        out.put("max", round(nanos[nanos.length - 1] / 1e6));
        return out;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        ticks++;

        try {
            switch (state) {
                case 0 -> {
                    // packs may replace the title screen (FancyMenu), so any screen at the menu will do
                    if (minecraft.level == null && minecraft.getOverlay() == null && minecraft.screen != null
                            && (minecraft.screen instanceof TitleScreen && ticks > 40 || ticks > 200)) {
                        openWorld(minecraft);
                        next();
                    }
                }
                case 1 -> {
                    if (minecraft.screen instanceof CreateWorldScreen create && ticks > 20) {
                        final WorldCreationUiState ui = create.getUiState();
                        ui.setName(WORLD);
                        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                        ui.setAllowCheats(true);
                        pressButton(create, Component.translatable("selectWorld.create"));
                        LOGGER.info("[testkit] creating world {}", WORLD);
                        next();
                    } else if (minecraft.player != null) {
                        next();
                    }
                }
                case 2 -> {
                    if (minecraft.player != null && minecraft.level != null) {
                        joinedAt = System.nanoTime();
                        next();
                    } else if (minecraft.screen != null && ticks % 40 == 0) {
                        // experimental-settings and backup prompts: answer like a player who wants to play
                        confirmPrompt(minecraft.screen);
                    }
                }
                case 3 -> {
                    if (EmiReloadManager.isLoaded() && ticks > 100) {
                        results.put("emi_ready_after_join_ms", round((System.nanoTime() - joinedAt) / 1e6));
                        results.put("index_size", EmiApi.getRecipeManager() == null ? 0
                                : dev.emi.emi.registry.EmiStackList.stacks.size());
                        results.put("recipe_count", EmiApi.getRecipeManager().getRecipes().size());
                        minecraft.setScreen(new InventoryScreen(minecraft.player));
                        next();
                    }
                }
                case 4 -> {
                    if (ticks > 40) {
                        moveMouse(5, 5);
                        startScenario("inventory, mouse idle");
                        next();
                    }
                }
                case 5 -> {
                    if (scenarioDone()) {
                        if ("greggified".equals(mode()) && !historyOffMeasured) {
                            // the same idle screen without NEI's history panel, to show what that panel costs
                            historyOffMeasured = true;
                            GreggifiedChecks.setHistoryPanel(false);
                            startScenario("inventory, mouse idle, history panel off");
                            return;
                        }
                        if (historyOffMeasured) {
                            GreggifiedChecks.setHistoryPanel(true);
                        }
                        final var right = EmiScreenManager.getPanelFor(SidebarSide.RIGHT);
                        if (right != null && right.space != null) {
                            moveMouse(right.space.tx + 9, right.space.ty + 9);
                        }
                        startScenario("inventory, hover index stack");
                        next();
                    }
                }
                case 6 -> {
                    if (scenarioDone()) {
                        if ("greggified".equals(mode()) && !zoomOffMeasured) {
                            // the same hover without NEI's item zoom, to show what the zoom itself costs
                            zoomOffMeasured = true;
                            GreggifiedChecks.setItemZoom(false);
                            startScenario("inventory, hover index stack, item zoom off");
                            return;
                        }
                        if (zoomOffMeasured) {
                            GreggifiedChecks.setItemZoom(true);
                        }
                        final var right = EmiScreenManager.getPanelFor(SidebarSide.RIGHT);
                        startScenario("inventory, page through index");
                        next();
                        if (right != null) {
                            right.scroll(1);
                        }
                    }
                }
                case 7 -> {
                    final var right = EmiScreenManager.getPanelFor(SidebarSide.RIGHT);
                    if (right != null && ticks % 5 == 0) {
                        right.scroll(1);
                    }
                    if (scenarioDone()) {
                        next();
                    }
                }
                case 8 -> {
                    // open recipes for the 10 index stacks with the most recipes, timing each
                    final List<EmiStack> targets = heavyStacks();
                    for (EmiStack stack : targets) {
                        final long start = System.nanoTime();
                        EmiApi.displayRecipes(stack);
                        recipeOpenMs.add((System.nanoTime() - start) / 1e6);
                        minecraft.setScreen(new InventoryScreen(minecraft.player));
                    }
                    results.put("open_recipes_ms", stats(recipeOpenMs.stream()
                            .mapToLong(ms -> (long) (ms * 1e6)).toArray()));
                    results.put("open_recipes_targets", targets.stream().map(s -> String.valueOf(s.getId())).toList());
                    if (!targets.isEmpty()) {
                        EmiApi.displayRecipes(targets.get(0));
                    }
                    next();
                }
                case 9 -> {
                    if (ticks > 20) {
                        moveMouse(minecraft.screen.width / 2.0, minecraft.screen.height / 2.0);
                        startScenario("recipe view");
                        next();
                    }
                }
                case 10 -> {
                    if (scenarioDone()) {
                        minecraft.setScreen(new InventoryScreen(minecraft.player));
                        next();
                    }
                }
                case 11 -> {
                    // search cost: compile each query and filter the whole index, the work EMI's search thread does
                    // (packs such as ReMI change how results are handed back, so this times the work itself)
                    final String[] queries = { "ingot", "plate", "iron", "@minecraft", "steel rod", "-ore dust",
                            "/ingot$/" };
                    final List<? extends EmiIngredient> source = dev.emi.emi.registry.EmiStackList.filteredStacks;
                    for (int round = 0; round < 6; round++) {
                        for (String query : queries) {
                            final long start = System.nanoTime();
                            final EmiSearch.CompiledQuery compiled = new EmiSearch.CompiledQuery(query);
                            int matches = 0;
                            for (EmiIngredient ingredient : source) {
                                for (EmiStack stack : ingredient.getEmiStacks()) {
                                    if (compiled.test(stack)) {
                                        matches++;
                                        break;
                                    }
                                }
                            }
                            // the first round warms caches up and is not counted
                            if (round > 0) {
                                searchMs.add((System.nanoTime() - start) / 1e6);
                            }
                            results.put("search_matches: " + query, matches);
                        }
                    }
                    results.put("search_ms", stats(searchMs.stream().mapToLong(ms -> (long) (ms * 1e6)).toArray()));
                    next();
                    state++;
                }
                case 13 -> {
                    if (ticks > 40) {
                        if ("greggified".equals(mode())) {
                            GreggifiedChecks.run(minecraft);
                        }
                        next();
                    }
                }
                case 14 -> {
                    if ("greggified".equals(mode()) && !GreggifiedChecks.done(minecraft)) {
                        return;
                    }
                    write();
                    next();
                    minecraft.stop();
                }
                default -> {}
            }
        } catch (Throwable th) {
            LOGGER.error("[testkit] failed in state {}", state, th);
            check(false, "testkit crashed in state " + state + ": " + th);
            write();
            minecraft.stop();
        }
    }

    private static List<EmiStack> heavyStacks() {
        final List<EmiStack> stacks = new ArrayList<>();

        for (EmiIngredient ingredient : dev.emi.emi.registry.EmiStackList.stacks) {
            if (ingredient instanceof EmiStack stack && !stack.isEmpty()) {
                stacks.add(stack);
            }
            if (stacks.size() >= 4000) {
                break;
            }
        }

        stacks.sort((a, b) -> Integer.compare(EmiApi.getRecipeManager().getRecipesByOutput(b).size(),
                EmiApi.getRecipeManager().getRecipesByOutput(a).size()));
        return stacks.subList(0, Math.min(10, stacks.size()));
    }

    private static void openWorld(Minecraft minecraft) {
        if (minecraft.getLevelSource().levelExists(WORLD)) {
            LOGGER.info("[testkit] loading world {}", WORLD);
            minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, WORLD);
        } else {
            CreateWorldScreen.openFresh(minecraft, minecraft.screen);
        }
    }

    private static void confirmPrompt(net.minecraft.client.gui.screens.Screen screen) {
        for (String key : new String[] { "selectWorld.backupJoinSkipButton", "gui.proceed", "gui.yes",
                "selectWorld.create" }) {
            final String label = Component.translatable(key).getString();

            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button && button.getMessage().getString().equals(label)) {
                    LOGGER.info("[testkit] pressing '{}' on {}", label, screen.getClass().getName());
                    button.onPress();
                    return;
                }
            }
        }
    }

    private static void pressButton(net.minecraft.client.gui.screens.Screen screen, Component message) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(message.getString())) {
                button.onPress();
                return;
            }
        }
        throw new IllegalStateException("No button " + message.getString());
    }

    static boolean isRecipeScreen(Minecraft minecraft) {
        return minecraft.screen instanceof RecipeScreen;
    }

    static EmiRecipe anyRecipe() {
        return EmiApi.getRecipeManager().getRecipes().get(0);
    }

    private static void write() {
        results.put("mode", mode());
        results.put("checks", checks);
        results.put("check_failures", checks.stream().filter(c -> c.startsWith("FAIL")).count());

        final Path file = gameDir().resolve("greggified-testkit").resolve(mode() + "-"
                + System.currentTimeMillis() + ".json");

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(results),
                    StandardCharsets.UTF_8);
            LOGGER.info("[testkit] wrote {}", file);
        } catch (Exception e) {
            LOGGER.error("[testkit] could not write {}", file, e);
        }
    }

}
