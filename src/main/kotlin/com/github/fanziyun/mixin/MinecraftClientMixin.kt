package com.github.fanziyun.mixin

import com.github.fanziyun.smoke.ClientSmokeTest
import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Minecraft::class)
class MinecraftClientMixin {
    @Inject(method = ["tick"], at = [At("TAIL")])
    fun changelog363_smokeTestTick(callback: CallbackInfo) {
        ClientSmokeTest.onClientTick(Minecraft.getInstance())
    }
}
