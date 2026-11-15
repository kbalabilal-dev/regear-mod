package com.regearmod.mixin;

import com.regearmod.RegearRunner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the shop windows from being drawn while a regear is running.
 * The window itself (the "handler") is still open and filled by the server,
 * so RegearRunner can read it and click through it without showing anything.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void regear$hideShopWindows(Screen screen, CallbackInfo ci) {
        if (screen instanceof GenericContainerScreen && RegearRunner.wantsHiddenWindow()) {
            ci.cancel();
        }
    }
}
