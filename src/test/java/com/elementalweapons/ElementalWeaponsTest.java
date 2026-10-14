package com.elementalweapons;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ElementalWeaponsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void cooldownsHaveDistinctDurations() {
        assertEquals(100, ElementalSwordItem.cooldownTicks(ElementalSwordItem.Element.FIRE));
        assertEquals(150, ElementalSwordItem.cooldownTicks(ElementalSwordItem.Element.AIR));
        assertEquals(300, ElementalSwordItem.cooldownTicks(ElementalSwordItem.Element.WATER));
        assertEquals(300, ElementalSwordItem.cooldownTicks(ElementalSwordItem.Element.EARTH));
    }

    @Test
    void smallPlantsAreReplaceableButTerrainAndContainersAreNot() {
        assertTrue(ElementalSwordItem.canReplace(Blocks.SHORT_GRASS.defaultBlockState()));
        assertTrue(ElementalSwordItem.canReplace(Blocks.DEAD_BUSH.defaultBlockState()));
        assertTrue(ElementalSwordItem.canReplace(Blocks.DANDELION.defaultBlockState()));
        assertFalse(ElementalSwordItem.canReplace(Blocks.STONE.defaultBlockState()));
        assertFalse(ElementalSwordItem.canReplace(Blocks.CHEST.defaultBlockState()));
        assertFalse(ElementalSwordItem.canReplace(Blocks.TALL_GRASS.defaultBlockState()));
    }

    @Test
    void waterOwnershipAndReplacedPlantSurviveSaveRoundTrip() {
        BlockPos pos = new BlockPos(10, 65, -4);
        TemporaryEffectsData original = new TemporaryEffectsData();
        original.add(pos, Blocks.DANDELION.defaultBlockState(), true, 12345L);
        var encoded = TemporaryEffectsData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var restored = TemporaryEffectsData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.isControlledWater(pos));
        assertTrue(encoded.toString().contains("dandelion"));
        assertTrue(encoded.toString().contains("12345"));
        restored.forget(pos);
        assertFalse(restored.isControlledWater(pos));
    }

    @Test
    void changingTemporaryWaterToAnotherBlockCancelsItsCleanup() {
        BlockPos pos = new BlockPos(1, 70, 1);
        TemporaryEffectsData data = new TemporaryEffectsData();
        data.add(pos, Blocks.SHORT_GRASS.defaultBlockState(), true, 100L);
        data.forgetIfReplaced(pos, Blocks.WATER.defaultBlockState());
        assertTrue(data.isControlledWater(pos));
        data.forgetIfReplaced(pos, Blocks.STONE.defaultBlockState());
        assertFalse(data.isControlledWater(pos));
    }
}
