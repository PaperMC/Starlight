package ca.spottedleaf.starlight.mixin.common.world;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {

    @Inject(method = "serialize", at = @At("RETURN"))
    private static void saveLightHook(ServerWorld world, Chunk chunk, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag ret = cir.getReturnValue();
        if (ret == null || world.getChunk(chunk.getPos().x, chunk.getPos().z, ChunkStatus.EMPTY, false) != null) {
            return;
        }

        SWMRNibbleArray[] blockNibbles = ((NibbledChunk)chunk).getBlockNibbles();
        SWMRNibbleArray[] skyNibbles = ((NibbledChunk)chunk).getSkyNibbles();
        CompoundTag level = ret.getCompound("Level");
        boolean lit = chunk.isLightOn();
        // diff start - store our tag for whether light data is init'd
        if (lit) {
            level.putBoolean("starlight.lit", lit);
        }
        // diff end - store our tag for whether light data is init'd
        ChunkStatus status = ChunkStatus.byId(level.getString("Status"));

        CompoundTag[] sections = new CompoundTag[18];

        ListTag sectionsStored = level.getList("Sections", 10);

        for (int i = 0; i < sectionsStored.size(); ++i) {
            CompoundTag sectionStored = sectionsStored.getCompound(i);
            int k = sectionStored.getByte("Y");

            // strip light data
            sectionStored.remove("BlockLight");
            sectionStored.remove("SkyLight");

            if (!sectionStored.isEmpty()) {
                sections[k + 1] = sectionStored;
            }
        }

        if (lit && status.isAtLeast(ChunkStatus.LIGHT)) {
            for (int i = -1; i <= 16; ++i) {
                ChunkNibbleArray blockNibble = blockNibbles[i + 1].asNibble();
                ChunkNibbleArray skyNibble = skyNibbles[i + 1].asNibble();
                if (blockNibble != null || skyNibble != null) {
                    CompoundTag section = sections[i + 1];
                    if (section == null) {
                        section = new CompoundTag();
                        section.putByte("Y", (byte)i);
                        sections[i + 1] = section;
                    }

                    if (blockNibble != null && !blockNibble.isUninitialized()) {
                        section.putByteArray("BlockLight", blockNibble.asByteArray());
                    }

                    if (skyNibble != null && !skyNibble.isUninitialized()) {
                        section.putByteArray("SkyLight", skyNibble.asByteArray());
                    }
                }
            }
        }

        // rewrite section list
        sectionsStored.clear();
        for (CompoundTag section : sections) {
            if (section != null) {
                sectionsStored.add(section);
            }
        }
        level.put("Sections", sectionsStored);
    }

    @Inject(method = "deserialize", at = @At("RETURN"))
    private static void loadLightHook(ServerWorld world, StructureManager structureManager, PointOfInterestStorage poiStorage, ChunkPos pos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        ProtoChunk ret = cir.getReturnValue();
        if (ret == null) {
            return;
        }

        SWMRNibbleArray[] blockNibbles = StarLightEngine.getFilledEmptyLight(false);
        SWMRNibbleArray[] skyNibbles = StarLightEngine.getFilledEmptyLight(true);
        // start copy from from the original method
        CompoundTag levelTag = tag.getCompound("Level");
        boolean lit = levelTag.getBoolean("isLightOn");
        lit = lit && levelTag.getBoolean("starlight.lit"); ret.setLightOn(lit); // diff - override lit with our value
        boolean canReadSky = world.getDimension().hasSkyLight();
        ChunkStatus status = ChunkStatus.byId(tag.getCompound("Level").getString("Status"));
        if (lit && status.isAtLeast(ChunkStatus.LIGHT)) { // diff - we add the status check here
            ListTag sections = levelTag.getList("Sections", 10);

            for (int i = 0; i < sections.size(); ++i) {
                CompoundTag sectionData = sections.getCompound(i);
                int y = sectionData.getByte("Y");

                if (sectionData.contains("BlockLight", 7)) {
                    // this is where our diff is
                    blockNibbles[y + 1] = new SWMRNibbleArray(sectionData.getByteArray("BlockLight"));
                }

                if (canReadSky && sectionData.contains("SkyLight", 7)) {
                    // this is where our diff is
                    skyNibbles[y + 1] = new SWMRNibbleArray(sectionData.getByteArray("SkyLight"));
                }
            }
        }
        // end copy from vanilla

        boolean nullableSky = true;
        for (int y = 16; y >= -1; --y) {
            SWMRNibbleArray nibble = skyNibbles[y + 1];
            if (nibble.isNullNibbleUpdating()) {
                nullableSky = false;
                continue;
            }
            if (!nullableSky) {
                nibble.markNonNull();
            }
        }

        ((NibbledChunk)ret).setBlockNibbles(blockNibbles);
        ((NibbledChunk)ret).setSkyNibbles(skyNibbles);
        // make sure the light engine knows we loaded from disk so we can properly handle chunk edges
        ((NibbledChunk)ret).setWasLoadedFromDisk(true);
    }
}
