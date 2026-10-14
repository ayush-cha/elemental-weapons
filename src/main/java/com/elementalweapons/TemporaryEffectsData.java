package com.elementalweapons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Saved per dimension so effects in unloaded chunks survive until they can be cleaned up. */
public final class TemporaryEffectsData extends SavedData {
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
        Codec.LONG.fieldOf("expires").forGetter(Entry::expires),
        BlockState.CODEC.fieldOf("previous").forGetter(Entry::previous),
        Codec.BOOL.fieldOf("water").forGetter(Entry::water)
    ).apply(instance, Entry::new));
    static final Codec<TemporaryEffectsData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(ENTRY_CODEC.listOf().fieldOf("entries").forGetter(data -> data.entries))
            .apply(instance, TemporaryEffectsData::new));
    private static final SavedDataType<TemporaryEffectsData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(ElementalWeapons.MOD_ID, "temporary_effects"),
        TemporaryEffectsData::new, CODEC, DataFixTypes.LEVEL);

    private static int ownChanges;
    private final List<Entry> entries;
    private final Set<Long> controlledWater = new HashSet<>();

    public TemporaryEffectsData() {
        this(List.of());
    }

    private TemporaryEffectsData(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
        for (Entry entry : entries) if (entry.water()) controlledWater.add(entry.pos());
    }

    public static TemporaryEffectsData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isControlledWater(BlockPos pos) {
        return controlledWater.contains(pos.asLong());
    }

    public void add(BlockPos pos, BlockState previous, boolean water, long expires) {
        entries.add(new Entry(pos.asLong(), expires, previous, water));
        if (water) controlledWater.add(pos.asLong());
        setDirty();
    }

    public void forget(BlockPos pos) {
        long key = pos.asLong();
        if (entries.removeIf(entry -> entry.pos() == key)) {
            controlledWater.remove(key);
            setDirty();
        }
    }

    /** Called before other code changes a tracked block. Our own placement and removal are excluded. */
    public static void onBlockChanged(ServerLevel level, BlockPos pos, BlockState replacement) {
        if (ownChanges != 0) return;
        get(level).forgetIfReplaced(pos, replacement);
    }

    void forgetIfReplaced(BlockPos pos, BlockState replacement) {
        if (entries.isEmpty()) return;
        long key = pos.asLong();
        for (Entry entry : entries) {
            if (entry.pos() == key) {
                BlockState placed = entry.water() ? Blocks.WATER.defaultBlockState()
                    : Blocks.STONE.defaultBlockState();
                if (!replacement.equals(placed)) forget(pos);
                return;
            }
        }
    }

    public static boolean setOwnBlock(ServerLevel level, BlockPos pos, BlockState state) {
        ownChanges++;
        try {
            return level.setBlock(pos, state, 3);
        } finally {
            ownChanges--;
        }
    }

    public void cleanExpired(ServerLevel level, long now, boolean allLoaded) {
        // Restoring a block can trigger neighbor updates that change another tracked block.
        for (Entry entry : List.copyOf(entries)) {
            if (!entries.contains(entry)) continue;
            if (!allLoaded && now < entry.expires()) continue;
            BlockPos pos = BlockPos.of(entry.pos());
            if (!level.isLoaded(pos)) continue;
            BlockState placed = entry.water() ? Blocks.WATER.defaultBlockState()
                : Blocks.STONE.defaultBlockState();
            if (level.getBlockState(pos).equals(placed)) {
                setOwnBlock(level, pos, entry.previous());
            }
            forget(pos);
        }
    }

    private record Entry(long pos, long expires, BlockState previous, boolean water) { }
}
