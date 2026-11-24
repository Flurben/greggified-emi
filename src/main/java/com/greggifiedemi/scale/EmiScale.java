package com.greggifiedemi.scale;

import java.util.ArrayDeque;
import java.util.Deque;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import com.greggifiedemi.Config;

import dev.emi.emi.api.widget.Bounds;

/**
 * Lets EMI's panels use their own GUI scale while the screen they sit on (inventories, EMI's recipe view) keeps the
 * game's. NEI has no such option; this is an addition the owner asked for.
 *
 * <p>EMI lays out and draws its sidebars in "EMI space": a virtual GUI as large as the window would be at EMI's scale.
 * Entry points of {@code EmiScreenManager} that take a position move into EMI space ({@link #enter}): the screen's
 * {@code width}/{@code height} are swapped for the virtual size, coordinates are divided by {@link #factor()}, and
 * drawing goes through a model-view scale (EMI batches its item grid against the model-view matrix, so the GUI pose
 * alone would not reach it). Every place where EMI reaches back into the screen (slot lookup, exclusion areas, drag and
 * drop, slot overlays) and every tooltip leaves EMI space again ({@link #leave}), so they keep the game's scale.
 *
 * <p>Code that takes a position therefore always receives it in the <em>current</em> space; {@link #emi}, {@link #game}
 * and {@link #fromGame} convert. {@code EmiScreenManager.lastMouseX/Y} always hold game coordinates, as EMI's recipe
 * view and other mods expect.
 */
public final class EmiScale {

    /** One entered EMI space: the screen whose size was swapped, its real size and the space it was entered from. */
    private record Frame(Screen screen, int realWidth, int realHeight, boolean wasInEmi) {}

    private static final Deque<Frame> FRAMES = new ArrayDeque<>();

    private static boolean detached;
    private static int setting;

    private static boolean inEmi;
    /** Game GUI units per EMI unit while scaled (EMI scale / game scale). */
    private static double factor = 1;
    private static int virtualWidth, virtualHeight;

    // memo of the scale computation, which only changes with the window or the settings
    private static int memoFramebufferWidth = -1, memoFramebufferHeight = -1, memoSetting = -1;
    private static double memoGameScale = -1;
    private static boolean memoUnicode;
    private static int memoEmiScale;

    private EmiScale() {}

    /** Re-reads the two settings; called when the config loads and when the config screen changes them. */
    public static void reloadSettings() {
        detached = Config.detachGuiScale();
        setting = Config.emiGuiScale();
        memoSetting = -1;
    }

    /** The GUI scale EMI's panels are drawn at: the game's unless detached. */
    public static int emiScale() {
        final Window window = Minecraft.getInstance().getWindow();

        if (!detached) {
            return (int) window.getGuiScale();
        }

        final boolean unicode = Minecraft.getInstance().isEnforceUnicode();

        if (window.getWidth() != memoFramebufferWidth || window.getHeight() != memoFramebufferHeight
                || setting != memoSetting || unicode != memoUnicode || window.getGuiScale() != memoGameScale) {
            memoFramebufferWidth = window.getWidth();
            memoFramebufferHeight = window.getHeight();
            memoSetting = setting;
            memoUnicode = unicode;
            memoGameScale = window.getGuiScale();
            // the same rule as Video Settings > GUI Scale: 0 is Auto, larger values are capped to what fits
            memoEmiScale = window.calculateScale(setting, unicode);
        }

        return memoEmiScale;
    }

    /** Whether EMI's panels currently use a scale other than the game's. */
    public static boolean isScaled() {
        return detached && Minecraft.getInstance().screen != null
                && emiScale() != Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static boolean inEmi() {
        return inEmi;
    }

    /** True when a call made in game space has to move into EMI space before EMI handles it. */
    public static boolean shouldEnter() {
        if (!detached) {
            return false;
        }

        final Screen screen = Minecraft.getInstance().screen;

        if (inEmi && !FRAMES.isEmpty() && FRAMES.peek().screen() == screen) {
            return false;
        }

        return screen != null && isScaled();
    }

    /** Moves into EMI space for the current screen; pair with {@link #exit()} in a {@code finally}. */
    public static void enter() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Window window = minecraft.getWindow();
        final Screen screen = minecraft.screen;
        final int scale = emiScale();

        factor = scale / window.getGuiScale();
        virtualWidth = scaledSize(window.getWidth(), scale);
        virtualHeight = scaledSize(window.getHeight(), scale);

        FRAMES.push(new Frame(screen, screen.width, screen.height, inEmi));
        screen.width = virtualWidth;
        screen.height = virtualHeight;
        inEmi = true;
    }

    public static void exit() {
        final Frame frame = FRAMES.pop();
        frame.screen().width = frame.realWidth();
        frame.screen().height = frame.realHeight();
        inEmi = frame.wasInEmi();
    }

    /**
     * Leaves EMI space for a call into the screen or a tooltip. Returns whether it did; pass the result to
     * {@link #reenter(boolean)} in a {@code finally}.
     */
    public static boolean leave() {
        if (!inEmi) {
            return false;
        }

        final Frame frame = FRAMES.peek();
        frame.screen().width = frame.realWidth();
        frame.screen().height = frame.realHeight();
        inEmi = false;
        return true;
    }

    public static void reenter(boolean left) {
        if (left) {
            final Frame frame = FRAMES.peek();
            frame.screen().width = virtualWidth;
            frame.screen().height = virtualHeight;
            inEmi = true;
        }
    }

    /** Game GUI units per EMI unit while in EMI space, otherwise 1. */
    public static double factor() {
        return inEmi ? factor : 1;
    }

    /** {@code ceil(pixels / scale)}, as {@code Window} computes the scaled GUI size. */
    private static int scaledSize(int pixels, int scale) {
        final int size = pixels / scale;
        return pixels % scale != 0 ? size + 1 : size;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Coordinates

    /** A game GUI coordinate in EMI space (only valid while entered). */
    public static double toEmi(double game) {
        return game / factor;
    }

    /** An EMI-space coordinate in game GUI units (only valid while entered). */
    public static double toGame(double emi) {
        return emi * factor;
    }

    /**
     * An integer game position in EMI space. When it is the mouse, the exact EMI position of the cursor is used, so
     * hover matches the pixel under the cursor instead of the truncated game position.
     */
    public static int toEmiX(int gameX) {
        return gameX == gameMouseX() ? emiMouseX() : (int) Math.floor(gameX / factor);
    }

    public static int toEmiY(int gameY) {
        return gameY == gameMouseY() ? emiMouseY() : (int) Math.floor(gameY / factor);
    }

    public static int toGameX(int emiX) {
        return emiX == emiMouseX() ? gameMouseX() : (int) Math.floor(emiX * factor);
    }

    public static int toGameY(int emiY) {
        return emiY == emiMouseY() ? gameMouseY() : (int) Math.floor(emiY * factor);
    }

    /** A position in the current space, in EMI space (for the bookmark panel's geometry, which EMI lays out). */
    public static double emi(double current) {
        if (inEmi) {
            return current;
        }

        return shouldEnter() ? current * Minecraft.getInstance().getWindow().getGuiScale() / emiScale() : current;
    }

    /** A position in the current space, in game GUI units (for the screen under EMI, e.g. recipe view widgets). */
    public static double game(double current) {
        return inEmi ? current * factor : current;
    }

    /** A game GUI position (such as {@code EmiScreenManager.lastMouseX}) in the current space. */
    public static double fromGame(double game) {
        return inEmi ? game / factor : game;
    }

    /** EMI-space bounds of game bounds, rounded outwards. */
    public static Bounds toEmi(Bounds game) {
        if (game.empty()) {
            return game;
        }

        final int x = (int) Math.floor(game.x() / factor);
        final int y = (int) Math.floor(game.y() / factor);
        final int right = (int) Math.ceil(game.right() / factor);
        final int bottom = (int) Math.ceil(game.bottom() / factor);
        return new Bounds(x, y, right - x, bottom - y);
    }

    private static int gameMouseX() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Window window = minecraft.getWindow();
        return (int) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
    }

    private static int gameMouseY() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Window window = minecraft.getWindow();
        return (int) (minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
    }

    private static int emiMouseX() {
        final Minecraft minecraft = Minecraft.getInstance();
        return (int) (minecraft.mouseHandler.xpos() * virtualWidth / minecraft.getWindow().getScreenWidth());
    }

    private static int emiMouseY() {
        final Minecraft minecraft = Minecraft.getInstance();
        return (int) (minecraft.mouseHandler.ypos() * virtualHeight / minecraft.getWindow().getScreenHeight());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Drawing

    /** Something drawn in EMI space with the mouse in EMI coordinates. */
    @FunctionalInterface
    public interface Drawing {
        void draw(int mouseX, int mouseY);
    }

    /** Enters EMI space and draws at EMI's scale. */
    public static void draw(GuiGraphics graphics, int gameMouseX, int gameMouseY, Drawing drawing) {
        enter();
        final PoseStack view = RenderSystem.getModelViewStack();

        try {
            final int mouseX = toEmiX(gameMouseX);
            final int mouseY = toEmiY(gameMouseY);
            graphics.flush();
            view.pushPose();
            view.scale((float) factor, (float) factor, 1);
            RenderSystem.applyModelViewMatrix();
            drawing.draw(mouseX, mouseY);
        } finally {
            graphics.flush();
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            exit();
        }
    }

    /** From EMI space, draws something at the game's scale (slot overlays, tooltips). */
    public static void drawInGame(GuiGraphics graphics, Runnable drawing) {
        final double scale = factor;
        final boolean left = leave();
        final PoseStack view = RenderSystem.getModelViewStack();

        try {
            graphics.flush();
            view.pushPose();
            view.scale((float) (1 / scale), (float) (1 / scale), 1);
            RenderSystem.applyModelViewMatrix();
            drawing.run();
        } finally {
            graphics.flush();
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            reenter(left);
        }
    }

}
