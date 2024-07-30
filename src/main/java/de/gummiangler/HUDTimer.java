package de.gummiangler;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.WorldSavePath;

public class HUDTimer {
    public void HUDTimer() {

        MinecraftClient client = MinecraftClient.getInstance();
        client.getWorldGenerationProgressTracker();
        client.getServer().getSaveProperties().getLevelName();

        };
    }




