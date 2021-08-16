package me.jellysquid.mods.sodium.mixin.world.mob_spawning;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructureManager;
import net.minecraft.world.gen.feature.structure.StructurePiece;
import net.minecraft.world.gen.feature.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(StructureManager.class)
public abstract class StructureAccessorMixin {
    @Shadow
    @Final
    private IWorld level;

    /**
     * @reason Avoid heavily nested stream code and object allocations where possible
     * @author JellySquid
     */
    @Overwrite
    public StructureStart<?> getStructureAt(BlockPos blockPos, boolean fine, Structure<?> feature) {
        IChunk originChunk = this.level.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4, ChunkStatus.STRUCTURE_REFERENCES);

        LongSet references = originChunk.getReferencesForFeature(feature);
        LongIterator iterator = references.iterator();

        while (iterator.hasNext()) {
            long pos = iterator.nextLong();

            IChunk chunk = this.level.getChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos), ChunkStatus.STRUCTURE_STARTS);
            StructureStart<?> structure = chunk.getStartForFeature(feature);

            if (structure == null || !structure.canBeReferenced() || !structure.getBoundingBox().isInside(blockPos)) {
                continue;
            }

            if (!fine || this.anyPieceContainsPosition(structure, blockPos)) {
                return structure;
            }
        }

        return StructureStart.INVALID_START;
    }

    private boolean anyPieceContainsPosition(StructureStart<?> structure, BlockPos blockPos) {
        for (StructurePiece piece : structure.getPieces()) {
            if (piece.getBoundingBox().isInside(blockPos)) {
                return true;
            }
        }

        return false;
    }
}
