package com.elementalweapons.mixin;

import com.elementalweapons.ElementalSwordItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "spread", at = @At("HEAD"), cancellable = true)
    private void elementalWeapons$keepTideForward(ServerLevel level, BlockPos pos,
        BlockState state, FluidState fluid, CallbackInfo ci) {
        if ((Object) this instanceof WaterFluid && ElementalSwordItem.isControlledWater(level, pos)) {
            ci.cancel();
        }
    }
}
