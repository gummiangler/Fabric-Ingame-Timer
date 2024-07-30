package de.gummiangler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ingame_timer implements ModInitializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get(MinecraftClient.getInstance().runDirectory.getPath(), "config");
    private static final Path SAVE_FILE_PATH = CONFIG_DIR.resolve("world_timers.json");

    private long startTime = 0;
    private boolean worldLoaded = false;
    private String currentWorldName = "";
    private Map<String, Long> worldTimers = new HashMap<>();
    private boolean timerPaused = false;

    @Override
    public void onInitialize() {
        loadTimers();
        final TextRenderer[] renderer = new TextRenderer[1];

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && !worldLoaded) {
                currentWorldName = formatWorldName(client.getServer().getSaveProperties().getLevelName());
                if (!worldTimers.containsKey(currentWorldName)) {
                    worldTimers.put(currentWorldName, 0L); // Initial Timer for new world
                }
                startTime = System.currentTimeMillis() - worldTimers.get(currentWorldName);
                renderer[0] = MinecraftClient.getInstance().textRenderer;
                worldLoaded = true;
            } else if (client.world == null && worldLoaded) {
                // Save the current timer value when the world is unloaded
                if (!currentWorldName.isEmpty() && !timerPaused) {
                    worldTimers.put(currentWorldName, System.currentTimeMillis() - startTime);
                    saveTimers();
                }
                worldLoaded = false;
            }
        });

        HudRenderCallback.EVENT.register((ctx, delta) -> {
            if (renderer[0] != null && worldLoaded) {
                long elapsedTime = timerPaused ? worldTimers.get(currentWorldName) : System.currentTimeMillis() - startTime;

                long seconds = (elapsedTime / 1000) % 60;
                long minutes = (elapsedTime / (1000 * 60)) % 60;
                long hours = (elapsedTime / (1000 * 60 * 60)) % 24;
                long days = elapsedTime / (1000 * 60 * 60 * 24);

                String timeString = String.format("%dd : %dh : %dm : %ds", days, hours, minutes, seconds);
                if (timerPaused) {
                    timeString += " (Timer pausiert)";
                }

                MatrixStack matrixStack = new MatrixStack();
                float scale = 0.9f;
                matrixStack.push();
                matrixStack.scale(scale, scale, scale);
                int x = (int) ((ctx.getScaledWindowWidth() - renderer[0].getWidth(timeString)) / 2);
                int y = (int) ((ctx.getScaledWindowHeight() - 50));

                ctx.drawText(renderer[0], timeString, x, y, 0x00FF00, false);
                matrixStack.pop();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("timerstart")
                    .executes(ctx -> {
                        MinecraftClient client = ctx.getSource().getClient();
                        if (timerPaused) {
                            client.execute(() -> {
                                timerPaused = false;
                                startTime = System.currentTimeMillis() - worldTimers.getOrDefault(currentWorldName, 0L);
                                ctx.getSource().sendFeedback(Text.literal("Timer fortgesetzt"));
                            });
                        } else {
                            ctx.getSource().sendFeedback(Text.literal("Timer läuft bereits"));
                        }
                        return 1;
                    }));

            dispatcher.register(ClientCommandManager.literal("timerstop")
                    .executes(ctx -> {
                        MinecraftClient client = ctx.getSource().getClient();
                        if (!timerPaused) {
                            client.execute(() -> {
                                // Save the current timer value and stop the timer
                                worldTimers.put(currentWorldName, System.currentTimeMillis() - startTime);
                                timerPaused = true;
                                saveTimers();
                                ctx.getSource().sendFeedback(Text.literal("Timer pausiert"));
                            });
                        } else {
                            ctx.getSource().sendFeedback(Text.literal("Timer ist bereits pausiert"));
                        }
                        return 1;
                    }));
        });
    }

    private void loadTimers() {
        if (Files.notExists(CONFIG_DIR)) {
            try {
                Files.createDirectory(CONFIG_DIR);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (Files.exists(SAVE_FILE_PATH)) {
            try (Reader reader = Files.newBufferedReader(SAVE_FILE_PATH)) {
                Type type = new TypeToken<Map<String, Long>>() {}.getType();
                worldTimers = GSON.fromJson(reader, type);
                if (worldTimers == null) {
                    worldTimers = new HashMap<>();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Files.createFile(SAVE_FILE_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveTimers() {
        try (Writer writer = Files.newBufferedWriter(SAVE_FILE_PATH)) {
            GSON.toJson(worldTimers, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatWorldName(String worldName) {
        return worldName.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
