package com.elementalweapons.mixin;

import com.elementalweapons.TemporaryEffectsData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
        at = @At("HEAD"))
    private void elementalWeapons$trackExternalChanges(BlockPos pos, BlockState state, int flags,
        CallbackInfoReturnable<Boolean> ci) {
        if ((Object) this instanceof ServerLevel serverLevel) {
            TemporaryEffectsData.onBlockChanged(serverLevel, pos, state);
        }
    }
}
