package com.elementalweapons;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ElementalSwordItem extends Item {
    public enum Element { FIRE, WATER, EARTH, AIR }

    private static final int TEMPORARY_BLOCK_TICKS = 5 * 20;
    private static final int WATER_REACH = 6;
    private static final List<WaterWave> WATER_WAVES = new ArrayList<>();

    private final Element element;

    public ElementalSwordItem(Element element, Properties properties) {
        super(properties);
        this.element = element;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        boolean used = switch (element) {
            case FIRE -> shootFireball(serverLevel, player);
            case WATER -> placeTemporaryBlocks(serverLevel, player, Blocks.WATER.defaultBlockState(), false);
            case EARTH -> placeTemporaryBlocks(serverLevel, player, Blocks.STONE.defaultBlockState(), true);
            case AIR -> shootWindCharge(serverLevel, player);
        };

        if (used) {
            player.getCooldowns().addCooldown(stack, cooldownTicks(element));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    static int cooldownTicks(Element element) {
        return switch (element) {
            case FIRE -> 5 * 20;
            case AIR -> 15 * 10;
            case WATER, EARTH -> 15 * 20;
        };
    }

    private static boolean shootFireball(ServerLevel level, Player player) {
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(direction.scale(1.25));
        SmallFireball projectile = new SmallFireball(level, player, direction);
        projectile.setPos(origin.x, origin.y, origin.z);
        return level.addFreshEntity(projectile);
    }

    private static boolean shootWindCharge(ServerLevel level, Player player) {
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(direction.scale(1.25));
        WindCharge projectile = new WindCharge(player, level, origin.x, origin.y, origin.z);
        projectile.shoot(direction.x, direction.y, direction.z, 1.5F, 0.0F);
        return level.addFreshEntity(projectile);
    }

    private static boolean placeTemporaryBlocks(ServerLevel level, Player player, BlockState state, boolean wall) {
        Direction forward = player.getDirection();
        Direction sideways = forward.getClockWise();
        BlockPos base = player.blockPosition().relative(forward, 2);
        long expires = level.getGameTime() + TEMPORARY_BLOCK_TICKS;
        int placed = 0;

        for (int offset = -1; offset <= 1; offset++) {
            int height = wall ? 3 : 1;
            for (int y = 0; y < height; y++) {
                BlockPos pos = base.relative(sideways, offset).above(y);
                // Leave terrain, containers, and existing water alone.
                if (placeTemporaryBlock(level, pos, state, expires, !wall)) {
                    placed++;
                }
            }
        }

        if (placed > 0 && !wall) {
            WATER_WAVES.add(new WaterWave(level, player, base, forward,
                level.getGameTime(), expires));
        }
        return placed > 0;
    }

    private static boolean placeTemporaryBlock(ServerLevel level, BlockPos pos, BlockState state,
        long expires, boolean controlledWater) {
        if (!level.isLoaded(pos)) return false;
        BlockState previous = level.getBlockState(pos);
        if (!canReplace(previous)) return false;
        BlockPos fixedPos = pos.immutable();
        TemporaryEffectsData data = TemporaryEffectsData.get(level);
        data.add(fixedPos, previous, controlledWater, expires);
        if (!TemporaryEffectsData.setOwnBlock(level, pos, state)) {
            data.forget(fixedPos);
            return false;
        }
        return true;
    }

    static boolean canReplace(BlockState state) {
        return (state.canBeReplaced() || state.getBlock() instanceof FlowerBlock)
            && !state.hasBlockEntity()
            && !(state.getBlock() instanceof DoublePlantBlock);
    }

    public static boolean isControlledWater(ServerLevel level, BlockPos pos) {
        return TemporaryEffectsData.get(level).isControlledWater(pos);
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        TemporaryEffectsData.get(level).cleanExpired(level, now, false);

        Iterator<WaterWave> waves = WATER_WAVES.iterator();
        while (waves.hasNext()) {
            WaterWave wave = waves.next();
            if (wave.level() != level) continue;
            if (now >= wave.expires()) {
                waves.remove();
                continue;
            }
            advanceWater(wave, now);
            pushNearbyTargets(wave);
        }
    }

    private static void advanceWater(WaterWave wave, long now) {
        long elapsed = now - wave.started();
        if (elapsed <= 0 || elapsed % 2 != 0) return;
        int distance = (int) (elapsed / 2);
        if (distance > WATER_REACH) return;
        Direction sideways = wave.forward().getClockWise();
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos previous = wave.origin().relative(wave.forward(), distance - 1)
                .relative(sideways, offset);
            BlockPos next = previous.relative(wave.forward());
            if (wave.level().isLoaded(previous)
                && isControlledWater(wave.level(), previous)
                && wave.level().getBlockState(previous).is(Blocks.WATER)) {
                placeTemporaryBlock(wave.level(), next, Blocks.WATER.defaultBlockState(),
                    wave.expires(), true);
            }
        }
    }

    private static void pushNearbyTargets(WaterWave wave) {
        Vec3 origin = Vec3.atCenterOf(wave.origin());
        int dx = wave.forward().getStepX();
        int dz = wave.forward().getStepZ();
        AABB search = new AABB(origin.x - 5, origin.y - 1, origin.z - 5,
            origin.x + 5, origin.y + 3, origin.z + 5);
        for (Entity entity : wave.level().getEntities(wave.owner(), search,
            target -> target instanceof LivingEntity && target != wave.owner())) {
            double x = entity.getX() - origin.x;
            double z = entity.getZ() - origin.z;
            double forwardDistance = x * dx + z * dz;
            double sideDistance = Math.abs(x * dz - z * dx);
            if (forwardDistance >= -0.5 && forwardDistance <= 5 && sideDistance <= 2.0) {
                entity.push(dx * 0.14, 0.025, dz * 0.14);
            }
        }
    }

    public static void clearTemporaryEffects(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            TemporaryEffectsData.get(level).cleanExpired(level, level.getGameTime(), true);
        }
        WATER_WAVES.clear();
    }

    private record WaterWave(ServerLevel level, Player owner, BlockPos origin, Direction forward,
        long started, long expires) { }
}
