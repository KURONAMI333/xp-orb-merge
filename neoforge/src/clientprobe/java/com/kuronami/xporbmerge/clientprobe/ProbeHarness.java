package com.kuronami.xporbmerge.clientprobe;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dev-only client probe: from the title screen it creates a flat world, joins it, hands the server
 * ticks to {@link #onServerTick}, and quits once {@link #finish} is called. Results land in
 * {@code <run>/probe-result.json}, screenshots in {@code <run>/screenshots/}. Never shipped.
 */
public abstract class ProbeHarness {

    protected final String name;
    protected final Map<String, Object> result = new LinkedHashMap<>();
    private boolean worldRequested;
    private int ticksWithPlayer = -1;
    private boolean finished;

    protected ProbeHarness(String name) {
        this.name = name;
        // Registered here as listeners: NeoForge refuses @SubscribeEvent methods on a superclass.
        NeoForge.EVENT_BUS.addListener(this::onScreenInit);
        NeoForge.EVENT_BUS.addListener(this::onServerTickEvent);
    }

    private void onScreenInit(ScreenEvent.Init.Post event) {
        // A fresh run directory opens the accessibility onboarding screen instead of the title screen.
        if (worldRequested || !(event.getScreen() instanceof TitleScreen || event.getScreen() instanceof AccessibilityOnboardingScreen)) {
            return;
        }
        worldRequested = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.save();
        String level = name + "-" + System.currentTimeMillis();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(level, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(level, settings, new WorldOptions(20261008L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                event.getScreen());
        result.put("world", level);
    }

    private void onServerTickEvent(ServerTickEvent.Post event) {
        if (finished) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) {
            return;
        }
        ticksWithPlayer++;
        if (ticksWithPlayer == 0) {
            server.overworld().setDayTime(6000);
        }
        try {
            onServerTick(server, player, ticksWithPlayer);
        } catch (RuntimeException e) {
            result.put("exception", e.toString());
            finish(false);
        }
    }

    /** Runs on the integrated server thread; {@code tick} counts from the first tick with a player. */
    protected abstract void onServerTick(MinecraftServer server, ServerPlayer player, int tick);

    protected void screenshot(String label) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Screenshot.grab(mc.gameDirectory, name + "-" + label + ".png", mc.getMainRenderTarget(), message -> { }));
    }

    protected void finish(boolean pass) {
        if (finished) {
            return;
        }
        finished = true;
        result.put("pass", pass);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(result);
        Minecraft mc = Minecraft.getInstance();
        try {
            Files.writeString(new File(mc.gameDirectory, "probe-result.json").toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[PROBE] " + name + " " + (pass ? "PASS" : "FAIL") + " " + json.replace('\n', ' '));
        // Leave time for the last screenshot, then quit the game (the integrated server saves on the way out).
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            mc.execute(mc::stop);
        }, "probe-quit").start();
    }
}
