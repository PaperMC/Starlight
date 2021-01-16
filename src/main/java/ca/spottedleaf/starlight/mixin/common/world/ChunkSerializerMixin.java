package ca.spottedleaf.starlight.mixin.common.world;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ChunkSerializer;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {

    private static final int STARLIGHT_LIGHT_VERSION = 4;

    private static final String UNINITIALISED_SKYLIGHT_TAG = "starlight.skylight_uninit";
    private static final String STARLIGHT_VERSION_TAG = "starlight.light_version";

    /**
     * Overwrites vanilla's light data with our own.
     * TODO this needs to be checked on update to account for format changes
     */
    @Inject(
            method = "write",
            at = @At("RETURN")
    )
    private static void saveLightHook(final ServerWorld world, final IChunk chunk, final CallbackInfoReturnable<CompoundNBT> cir) {
        final int minSection = WorldUtil.getMinLightSection(world);
        final int maxSection = WorldUtil.getMaxLightSection(world);
        CompoundNBT ret = cir.getReturnValue();
        if (ret == null) {
            return;
        }

        SWMRNibbleArray[] blockNibbles = ((ExtendedChunk)chunk).getBlockNibbles();
        SWMRNibbleArray[] skyNibbles = ((ExtendedChunk)chunk).getSkyNibbles();

        CompoundNBT level = ret.getCompound("Level");
        boolean lit = chunk.hasLight();
        // diff start - store our tag for whether light data is init'd
        if (lit) {
            level.putBoolean("isLightOn", false);
            level.putInt(STARLIGHT_VERSION_TAG, STARLIGHT_LIGHT_VERSION);
        }
        // diff end - store our tag for whether light data is init'd
        ChunkStatus status = ChunkStatus.byName(level.getString("Status"));

        CompoundNBT[] sections = new CompoundNBT[maxSection - minSection + 1];

        ListNBT sectionsStored = level.getList("Sections", 10);

        for (int i = 0; i < sectionsStored.size(); ++i) {
            CompoundNBT sectionStored = sectionsStored.getCompound(i);
            int k = sectionStored.getByte("Y");

            // strip light data
            sectionStored.remove("BlockLight");
            sectionStored.remove("SkyLight");

            if (!sectionStored.isEmpty()) {
                sections[k - minSection] = sectionStored;
            }
        }

        if (lit && status.isAtLeast(ChunkStatus.LIGHT)) {
            for (int i = minSection; i <= maxSection; ++i) {
                NibbleArray blockNibble = blockNibbles[i - minSection].isAllZero() ? new NibbleArray() : blockNibbles[i - minSection].toVanillaNibble();
                NibbleArray skyNibble = skyNibbles[i - minSection].isAllZero() ? new NibbleArray() : skyNibbles[i - minSection].toVanillaNibble();
                if (blockNibble != null || skyNibble != null) {
                    CompoundNBT section = sections[i - minSection];
                    if (section == null) {
                        section = new CompoundNBT();
                        section.putByte("Y", (byte)i);
                        sections[i - minSection] = section;
                    }

                    if (blockNibble != null && !blockNibble.isEmpty()) {
                        section.putByteArray("BlockLight", blockNibble.getData());
                    }

                    if (skyNibble != null) {
                        if (skyNibble.isEmpty()) {
                            section.putBoolean(UNINITIALISED_SKYLIGHT_TAG, true);
                        } else {
                            // we store under the same key so mod programs editing nbt
                            // can still read the data, hopefully.
                            // however, for compatibility we store chunks as unlit so vanilla
                            // is forced to re-light them if it encounters our data. It's too much of a burden
                            // to try and maintain compatibility with a broken and inferior skylight management system.
                            section.putByteArray("SkyLight", skyNibble.getData());
                        }
                    }
                }
            }
        }

        // rewrite section list
        sectionsStored.clear();
        for (CompoundNBT section : sections) {
            if (section != null) {
                sectionsStored.add(section);
            }
        }
        level.put("Sections", sectionsStored);
    }

    /**
     * Loads our light data into the returned chunk object from the tag.
     * TODO this needs to be checked on update to account for format changes
     */
    @Inject(
            method = "read",
            at = @At("RETURN")
    )
    private static void loadLightHook(final ServerWorld world, final TemplateManager structureManager, final PointOfInterestManager poiStorage,
                                      final ChunkPos pos, final CompoundNBT tag, final CallbackInfoReturnable<ChunkPrimer> cir) {
        final int minSection = WorldUtil.getMinLightSection(world);
        final int maxSection = WorldUtil.getMaxLightSection(world);
        ChunkPrimer ret = cir.getReturnValue();
        if (ret == null) {
            return;
        }

        SWMRNibbleArray[] blockNibbles = StarLightEngine.getFilledEmptyLight(world);
        SWMRNibbleArray[] skyNibbles = StarLightEngine.getFilledEmptyLight(world);


        // start copy from from the original method
        CompoundNBT levelTag = tag.getCompound("Level");
        boolean lit = levelTag.getInt(STARLIGHT_VERSION_TAG) == STARLIGHT_LIGHT_VERSION; ret.setLight(lit); // diff - override lit with our value
        boolean canReadSky = world.getDimensionType().hasSkyLight();
        ChunkStatus status = ChunkStatus.byName(tag.getCompound("Level").getString("Status"));
        if (lit && status.isAtLeast(ChunkStatus.LIGHT)) { // diff - we add the status check here
            ListNBT sections = levelTag.getList("Sections", 10);

            for (int i = 0; i < sections.size(); ++i) {
                CompoundNBT sectionData = sections.getCompound(i);
                int y = sectionData.getByte("Y");

                if (sectionData.contains("BlockLight", 7)) {
                    // this is where our diff is
                    blockNibbles[y - minSection] = new SWMRNibbleArray(sectionData.getByteArray("BlockLight").clone()); // clone for data safety
                }

                if (canReadSky) {
                    if (sectionData.contains("SkyLight", 7)) {
                        // we store under the same key so mod programs editing nbt
                        // can still read the data, hopefully.
                        // however, for compatibility we store chunks as unlit so vanilla
                        // is forced to re-light them if it encounters our data. It's too much of a burden
                        // to try and maintain compatibility with a broken and inferior skylight management system.
                        skyNibbles[y - minSection] = new SWMRNibbleArray(sectionData.getByteArray("SkyLight").clone()); // clone for data safety
                    } else if (sectionData.getBoolean(UNINITIALISED_SKYLIGHT_TAG)) {
                        skyNibbles[y - minSection] = new SWMRNibbleArray();
                    }
                }
            }
        }
        // end copy from vanilla

        ((ExtendedChunk)ret).setBlockNibbles(blockNibbles);
        ((ExtendedChunk)ret).setSkyNibbles(skyNibbles);
    }
}
