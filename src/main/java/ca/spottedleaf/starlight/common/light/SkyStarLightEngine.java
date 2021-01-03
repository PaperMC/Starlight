package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunkSection;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import java.util.Arrays;
import java.util.Set;

public final class SkyStarLightEngine extends StarLightEngine {

    /*
      Specification for managing the initialisation and de-initialisation of skylight nibble arrays:

      Skylight nibble initialisation requires that non-empty chunk sections have 1 radius nibbles non-null.

      This presents some problems, as vanilla is only guaranteed to have 0 radius neighbours loaded when editing blocks.
      However starlight fixes this so that it has 1 radius loaded. Still, we don't actually have guarantees
      that we have the necessary chunks loaded to de-initialise neighbour sections (but we do have enough to de-initialise
      our own) - we need a radius of 2 to de-initialise neighbour nibbles.
      How do we solve this?

      Each chunk will store the last known "emptiness" of sections for each of their 1 radius neighbour chunk sections.
      If the chunk does not have full data, then its nibbles are NOT de-initialised. This is because obviously the
      chunk did not go through the light stage yet - or its neighbours are not lit. In either case, once the last
      known "emptiness" of neighbouring sections is filled with data, the chunk will run a full check of the data
      to see if any of its nibbles need to be de-initialised.

      The emptiness map allows us to de-initialise neighbour nibbles if the neighbour has it filled with data,
      and if it doesn't have data then we know it will correctly de-initialise once it fills up.

      Unlike vanilla, we store whether nibbles are uninitialised on disk - so we don't need any dumb hacking
      around those.
     */

    protected final int[] heightMapBlockChange = new int[16 * 16];
    {
        Arrays.fill(this.heightMapBlockChange, Integer.MIN_VALUE); // clear heightmap
    }

    protected final boolean[] nullPropagationCheckCache;

    public SkyStarLightEngine(final World world) {
        super(true, world);
        this.nullPropagationCheckCache = new boolean[WorldUtil.getTotalLightSections(world)];
    }

    @Override
    protected boolean[] handleEmptySectionChanges(final ChunkProvider lightAccess, final Chunk chunk,
                                                  final Boolean[] emptinessChanges, final boolean unlit) {
        final World world = (World)lightAccess.getWorld();
        final int chunkX = chunk.getPos().x;
        final int chunkZ = chunk.getPos().z;

        boolean[] chunkEmptinessMap = this.getEmptinessMap(chunkX, chunkZ);
        boolean[] ret = null;
        final boolean needsInit = unlit || chunkEmptinessMap == null;
        if (needsInit) {
            this.setEmptinessMapCache(chunkX, chunkZ, ret = chunkEmptinessMap = new boolean[WorldUtil.getTotalSections(world)]);
        }

        // update emptiness map
        for (int sectionIndex = (emptinessChanges.length - 1); sectionIndex >= 0; --sectionIndex) {
            final Boolean valueBoxed = emptinessChanges[sectionIndex];
            if (valueBoxed == null) {
                if (needsInit) {
                    throw new IllegalStateException("Current chunk has not initialised emptiness map yet supplied emptiness map isn't filled?");
                }
                continue;
            }
            chunkEmptinessMap[sectionIndex] = valueBoxed.booleanValue();
        }

        // now init neighbour nibbles
        for (int sectionIndex = (emptinessChanges.length - 1); sectionIndex >= 0; --sectionIndex) {
            final Boolean valueBoxed = emptinessChanges[sectionIndex];
            final int sectionY = sectionIndex + this.minSection;
            if (valueBoxed == null) {
                continue;
            }

            final boolean empty = valueBoxed.booleanValue();

            if (empty) {
                continue;
            }

            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    // if we're not empty, we also need to initialise nibbles
                    // note: if we're unlit, we absolutely do not want to extrude, as light data isn't set up
                    final boolean extrude = (dx | dz) != 0 || !unlit;
                    for (int dy = 1; dy >= -1; --dy) {
                        this.initNibbleForLitChunk(dx + chunkX, dy + sectionY, dz + chunkZ, extrude, false);
                    }
                }
            }
        }

        // check for de-init and lazy-init
        // lazy init is when chunks are being lit, so at the time they weren't loaded when their neighbours were running
        // init checks.
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                // does this neighbour have 1 radius loaded?
                boolean neighboursLoaded = true;
                neighbour_loaded_search:
                for (int dz2 = -1; dz2 <= 1; ++dz2) {
                    for (int dx2 = -1; dx2 <= 1; ++dx2) {
                        if (this.getEmptinessMap(dx + dx2 + chunkX, dz + dz2 + chunkZ) == null) {
                            neighboursLoaded = false;
                            break neighbour_loaded_search;
                        }
                    }
                }

                for (int sectionY = this.maxLightSection; sectionY >= this.minLightSection; --sectionY) {
                    final SWMRNibbleArray nibble = this.getNibbleFromCache(dx + chunkX, sectionY, dz + chunkZ);

                    // check neighbours to see if we need to de-init this one
                    boolean allEmpty = true;
                    neighbour_search:
                    for (int dy2 = -1; dy2 <= 1; ++dy2) {
                        for (int dz2 = -1; dz2 <= 1; ++dz2) {
                            for (int dx2 = -1; dx2 <= 1; ++dx2) {
                                final int y = sectionY + dy2;
                                if (y < this.minSection || y > this.maxSection) {
                                    // empty
                                    continue;
                                }
                                final boolean[] emptinessMap = this.getEmptinessMap(dx + dx2 + chunkX, dz + dz2 + chunkZ);
                                if (emptinessMap != null) {
                                    if (!emptinessMap[y - this.minSection]) {
                                        allEmpty = false;
                                        break neighbour_search;
                                    }
                                } else {
                                    final ChunkSection section = this.getChunkSection(dx + dx2 + chunkX, y, dz + dz2 + chunkZ);
                                    if (section != null && section != EMPTY_CHUNK_SECTION) {
                                        allEmpty = false;
                                        break neighbour_search;
                                    }
                                }
                            }
                        }
                    }

                    if (allEmpty & neighboursLoaded) {
                        // can only de-init when neighbours are loaded
                        // de-init is fine to delay, as de-init is just an optimisation - it's not required for lighting
                        // to be correct

                        // all were empty, so de-init
                        if (nibble != null) {
                            nibble.setNull();
                        }
                    } else if (!allEmpty) {
                        // must init
                        final boolean extrude = (dx | dz) != 0 || !unlit;
                        this.initNibbleForLitChunk(dx + chunkX, sectionY, dz + chunkZ, extrude, false);
                    }
                }
            }
        }

        return ret;
    }

    protected final void initNibbleForLitChunk(final int chunkX, final int chunkY, final int chunkZ, final boolean extrude,
                                               final boolean initRemovedNibbles) {
        if (chunkY < this.minLightSection || chunkY > this.maxLightSection || this.getChunkInCache(chunkX, chunkZ) == null) {
            return;
        }
        SWMRNibbleArray nibble = this.getNibbleFromCache(chunkX, chunkY, chunkZ);
        if (nibble == null) {
            if (!initRemovedNibbles) {
                throw new IllegalStateException();
            } else {
                this.setNibbleInCache(chunkX, chunkY, chunkZ, nibble = new SWMRNibbleArray(null, true));
            }
        }
        this.initNibbleForLitChunk(nibble, chunkX, chunkY, chunkZ, extrude);
    }

    protected final void initNibbleForLitChunk(final SWMRNibbleArray currNibble, final int chunkX, final int chunkY, final int chunkZ, final boolean extrude) {
        if (!currNibble.isNullNibbleUpdating()) {
            // already initialised
            return;
        }

        final boolean[] emptinessMap = this.getEmptinessMap(chunkX, chunkZ);

        // are we above this chunk's lowest empty section?
        int lowestY = this.minLightSection - 1;
        for (int currY = this.maxSection; currY >= this.minSection; --currY) {
            if (emptinessMap == null) {
                // cannot delay nibble init for lit chunks, as we need to init to propagate into them.
                final ChunkSection current = this.getChunkSection(chunkX, currY, chunkZ);
                if (current == null || current == EMPTY_CHUNK_SECTION) {
                    continue;
                }
            } else {
                if (emptinessMap[currY - this.minSection]) {
                    continue;
                }
            }

            // should always be full lit here
            lowestY = currY;
            break;
        }

        if (chunkY > lowestY) {
            // we need to set this one to full
            this.getNibbleFromCache(chunkX, chunkY, chunkZ).setFull();
            return;
        }

        if (extrude) {
            // this nibble is going to depend solely on the skylight data above it
            // find first non-null data above (there does exist one, as we just found it above)
            for (int currY = chunkY + 1; currY <= this.maxLightSection; ++currY) {
                final SWMRNibbleArray nibble = this.getNibbleFromCache(chunkX, currY, chunkZ);
                if (nibble != null && !nibble.isNullNibbleUpdating()) {
                    currNibble.extrudeLower(nibble);
                    break;
                }
            }
        } else {
            currNibble.setNonNull();
        }
    }

    protected final void rewriteNibbleCacheForSkylight(final Chunk chunk) {
        for (int index = 0, max = this.nibbleCache.length; index < max; ++index) {
            final SWMRNibbleArray nibble = this.nibbleCache[index];
            if (nibble != null && nibble.isNullNibbleUpdating()) {
                // stop propagation in these areas
                this.nibbleCache[index] = null;
                nibble.updateVisible();
            }
        }
    }

    // rets whether neighbours were init'd

    protected final boolean checkNullSection(final int chunkX, final int chunkY, final int chunkZ,
                                             final boolean extrudeInitialised) {
        // null chunk sections may have nibble neighbours in the horizontal 1 radius that are
        // non-null. Propagation to these neighbours is necessary.
        // What makes this easy is we know none of these neighbours are non-empty (otherwise
        // this nibble would be initialised). So, we don't have to initialise
        // the neighbours in the full 1 radius, because there's no worry that any "paths"
        // to the neighbours on this horizontal plane are blocked.
        if (chunkY < this.minLightSection || chunkY > this.maxLightSection || this.nullPropagationCheckCache[chunkY - this.minLightSection]) {
            return false;
        }
        this.nullPropagationCheckCache[chunkY - this.minLightSection] = true;

        // check horizontal neighbours
        boolean needInitNeighbours = false;
        neighbour_search:
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                final SWMRNibbleArray nibble = this.getNibbleFromCache(dx + chunkX, chunkY, dz + chunkZ);
                if (nibble != null && !nibble.isNullNibbleUpdating()) {
                    needInitNeighbours = true;
                    break neighbour_search;
                }
            }
        }

        if (needInitNeighbours) {
            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    this.initNibbleForLitChunk(dx + chunkX, chunkY, dz + chunkZ, (dx | dz) == 0 ? extrudeInitialised : true, true);
                }
            }
        }

        return needInitNeighbours;
    }

    protected final int getLightLevelExtruded(final int worldX, final int worldY, final int worldZ) {
        final int chunkX = worldX >> 4;
        int chunkY = worldY >> 4;
        final int chunkZ = worldZ >> 4;

        SWMRNibbleArray nibble = this.getNibbleFromCache(chunkX, chunkY, chunkZ);
        if (nibble != null) {
            return nibble.getUpdating(worldX, worldY, worldZ);
        }

        for (;;) {
            if (++chunkY > this.maxLightSection) {
                return 15;
            }

            nibble = this.getNibbleFromCache(chunkX, chunkY, chunkZ);

            if (nibble != null) {
                return nibble.getUpdating(worldX, 0, worldZ);
            }
        }
    }

    @Override
    protected boolean[] getEmptinessMap(final Chunk chunk) {
        return ((ExtendedChunk)chunk).getEmptinessMap();
    }

    @Override
    protected void setEmptinessMap(final Chunk chunk, final boolean[] to) {
        ((ExtendedChunk)chunk).setEmptinessMap(to);
    }

    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(final Chunk chunk) {
        return ((ExtendedChunk)chunk).getSkyNibbles();
    }

    @Override
    protected void setNibbles(final Chunk chunk, final SWMRNibbleArray[] to) {
        ((ExtendedChunk)chunk).setSkyNibbles(to);
    }

    @Override
    protected boolean canUseChunk(final Chunk chunk) {
        // can only use chunks for sky stuff if their sections have been init'd
        return chunk.getStatus().isAtLeast(ChunkStatus.LIGHT) && (this.isClientSide ? true : chunk.isLightOn());
    }

    @Override
    protected void checkChunkEdges(final ChunkProvider lightAccess, final Chunk chunk, final int fromSection,
                                   final int toSection) {
        this.rewriteNibbleCacheForSkylight(chunk);
        super.checkChunkEdges(lightAccess, chunk, fromSection, toSection);
    }

    @Override
    protected void checkChunkEdges(ChunkProvider lightAccess, Chunk chunk, ShortCollection sections) {
        this.rewriteNibbleCacheForSkylight(chunk);
        super.checkChunkEdges(lightAccess, chunk, sections);
    }

    @Override
    protected void checkBlock(final ChunkProvider lightAccess, final int worldX, final int worldY, final int worldZ) {
        // blocks can change opacity
        // blocks can change direction of propagation

        // same logic applies from BlockStarLightEngine#checkBlock

        final int encodeOffset = this.coordinateOffset;

        final int currentLevel = this.getLightLevel(worldX, worldY, worldZ);

        if (currentLevel == 15) {
            // must re-propagate clobbered source
            this.appendToIncreaseQueue(
                    ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                            | (currentLevel & 0xFL) << (6 + 6 + 16)
                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                            | FLAG_HAS_SIDED_TRANSPARENT_BLOCKS // don't know if the block is conditionally transparent
            );
        } else {
            this.setLightLevel(worldX, worldY, worldZ, 0);
        }

        this.appendToDecreaseQueue(
                ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                        | (currentLevel & 0xFL) << (6 + 6 + 16)
                        | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
        );
    }

    @Override
    protected void propagateBlockChanges(final ChunkProvider lightAccess, final Chunk atChunk, final Set<BlockPos> positions) {
        this.rewriteNibbleCacheForSkylight(atChunk);
        Arrays.fill(this.nullPropagationCheckCache, false);

        final BlockView world = lightAccess.getWorld();
        final int chunkX = atChunk.getPos().x;
        final int chunkZ = atChunk.getPos().z;
        final int heightMapOffset = chunkX * -16 + (chunkZ * (-16 * 16));

        // setup heightmap for changes
        for (final BlockPos pos : positions) {
            final int index = pos.getX() + (pos.getZ() << 4) + heightMapOffset;
            final int curr = this.heightMapBlockChange[index];
            if (pos.getY() > curr) {
                this.heightMapBlockChange[index] = pos.getY();
            }
        }

        // note: light sets are delayed while processing skylight source changes due to how
        // nibbles are initialised, as we want to avoid clobbering nibble values so what when
        // below nibbles are initialised they aren't reading from partially modified nibbles

        // now we can recalculate the sources for the changed columns
        for (int index = 0; index < (16 * 16); ++index) {
            final int maxY = this.heightMapBlockChange[index];
            if (maxY == Integer.MIN_VALUE) {
                // not changed
                continue;
            }
            this.heightMapBlockChange[index] = Integer.MIN_VALUE; // restore default for next caller

            final int columnX = (index & 15) | (chunkX << 4);
            final int columnZ = (index >>> 4) | (chunkZ << 4);

            // try and propagate from the above y
            // delay light set until after processing all sources to setup
            final int maxPropagationY = this.tryPropagateSkylight(world, columnX, maxY, columnZ, true, true);

            // maxPropagationY is now the highest block that could not be propagated to

            // remove all sources below that are 15
            final long propagateDirection = AxisDirection.POSITIVE_Y.everythingButThisDirection;
            final int encodeOffset = this.coordinateOffset;

            if (this.getLightLevelExtruded(columnX, maxPropagationY, columnZ) == 15) {
                // ensure section is checked
                this.checkNullSection(columnX >> 4, maxPropagationY >> 4, columnZ >> 4, true);

                for (int currY = maxPropagationY; currY >= (this.minLightSection << 4); --currY) {
                    if ((currY & 15) == 15) {
                        // ensure section is checked
                        this.checkNullSection(columnX >> 4, (currY >> 4), columnZ >> 4, true);
                    }

                    // ensure section below is always checked
                    final SWMRNibbleArray nibble = this.getNibbleFromCache(columnX >> 4, currY >> 4, columnZ >> 4);
                    if (nibble == null) {
                        // advance currY to the the top of the section below
                        currY = (currY) & (~15);
                        // note: this value ^ is actually 1 above the top, but the loop decrements by 1 so we actually
                        // end up there
                        continue;
                    }

                    if (nibble.getUpdating(columnX, currY, columnZ) != 15) {
                        break;
                    }

                    // delay light set until after processing all sources to setup
                    this.appendToDecreaseQueue(
                            ((columnX + (columnZ << 6) + (currY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                    | (15L << (6 + 6 + 16))
                                    | (propagateDirection << (6 + 6 + 16 + 4))
                                    // do not set transparent blocks for the same reason we don't in the checkBlock method
                    );
                }
            }
        }

        // delayed light sets are processed here, and must be processed before checkBlock as checkBlock reads
        // immediate light value
        this.processDelayedIncreases();
        this.processDelayedDecreases();

        for (final BlockPos pos : positions) {
            this.checkBlock(lightAccess, pos.getX(), pos.getY(), pos.getZ());
        }

        this.performLightDecrease(lightAccess);
    }

    protected final int[] heightMapGen = new int[32 * 32];

    @Override
    protected void lightChunk(final ChunkProvider lightAccess, final Chunk chunk, final boolean needsEdgeChecks) {
        this.rewriteNibbleCacheForSkylight(chunk);
        Arrays.fill(this.nullPropagationCheckCache, false);

        final BlockView world = lightAccess.getWorld();
        final ChunkPos chunkPos = chunk.getPos();
        final int chunkX = chunkPos.x;
        final int chunkZ = chunkPos.z;

        final ChunkSection[] sections = chunk.getSectionArray();

        int highestNonEmptySection = this.maxSection;
        while (highestNonEmptySection == (this.minSection - 1) ||
                sections[highestNonEmptySection - this.minSection] == null || sections[highestNonEmptySection - this.minSection].isEmpty()) {
            this.checkNullSection(chunkX, highestNonEmptySection, chunkZ, false);
            // try propagate FULL to neighbours

            // check neighbours to see if we need to propagate into them
            for (final AxisDirection direction : ONLY_HORIZONTAL_DIRECTIONS) {
                final int neighbourX = chunkX + direction.x;
                final int neighbourZ = chunkZ + direction.z;
                final SWMRNibbleArray neighbourNibble = this.getNibbleFromCache(neighbourX, highestNonEmptySection, neighbourZ);
                if (neighbourNibble == null) {
                    // unloaded neighbour
                    // most of the time we fall here
                    continue;
                }

                // it looks like we need to propagate into the neighbour

                final int incX;
                final int incZ;
                final int startX;
                final int startZ;

                if (direction.x != 0) {
                    // x direction
                    incX = 0;
                    incZ = 1;

                    if (direction.x < 0) {
                        // negative
                        startX = chunkX << 4;
                    } else {
                        startX = chunkX << 4 | 15;
                    }
                    startZ = chunkZ << 4;
                } else {
                    // z direction
                    incX = 1;
                    incZ = 0;

                    if (direction.z < 0) {
                        // negative
                        startZ = chunkZ << 4;
                    } else {
                        startZ = chunkZ << 4 | 15;
                    }
                    startX = chunkX << 4;
                }

                final int encodeOffset = this.coordinateOffset;
                final long propagateDirection = 1L << direction.ordinal(); // we only want to check in this direction

                for (int currY = highestNonEmptySection << 4, maxY = currY | 15; currY <= maxY; ++currY) {
                    for (int i = 0, currX = startX, currZ = startZ; i < 16; ++i, currX += incX, currZ += incZ) {
                        this.appendToIncreaseQueue(
                                ((currX + (currZ << 6) + (currY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                        | (15L << (6 + 6 + 16)) // we know we're at full lit here
                                        | (propagateDirection << (6 + 6 + 16 + 4))
                                        // no transparent flag, we know for a fact there are no blocks here that could be directionally transparent (as the section is EMPTY)
                        );
                    }
                }
            }

            if (highestNonEmptySection-- == (this.minSection - 1)) {
                break;
            }
        }

        if (highestNonEmptySection >= this.minSection) {
            // fill out our other sources

            // init heightmap
            // index = (x + 1) + ((z + 1) << 5)
            final int[] heightMap = this.heightMapGen;
            final int worldChunkX = chunkPos.x << 4;
            final int worldChunkZ = chunkPos.z << 4;
            final int minX = worldChunkX - 1;
            final int maxX = worldChunkX + 16;
            final int minZ = worldChunkZ - 1;
            final int maxZ = worldChunkZ + 16;
            for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                for (int currX = minX; currX <= maxX; ++currX) {
                    int maxY = ((this.minLightSection - 1) << 4);

                    // ensure the section below is always checked
                    this.checkNullSection(currX >> 4, highestNonEmptySection, currZ >> 4, false);
                    this.checkNullSection(currX >> 4, highestNonEmptySection - 1, currZ >> 4, false);
                    for (int sectionY = highestNonEmptySection; sectionY >= 0; --sectionY) {
                        final ChunkSection section = this.getChunkSection(currX >> 4, sectionY, currZ >> 4);

                        if (section == null) {
                            // unloaded neighbour
                            continue;
                        }

                        // ensure the section below is always checked
                        this.checkNullSection(currX >> 4, sectionY - 1, currZ >> 4, false);

                        final long bitset = ((ExtendedChunkSection)section).getBitsetForColumn(currX & 15, currZ & 15);
                        if (bitset == 0) {
                            continue;
                        }

                        final int highestBitSet = 63 ^ Long.numberOfLeadingZeros(bitset); // from [0, 63]
                        final int highestYValue = highestBitSet; // y = highest bit set / bits per block
                        maxY = highestYValue | (sectionY << 4);
                        break;
                    }
                    heightMap[(currX - worldChunkX + 1) | ((currZ - worldChunkZ + 1) << 5)] = maxY;
                }
            }

            // now setup sources
            final int encodeOffset = this.coordinateOffset;
            for (int currZ = 0; currZ <= 15; ++currZ) {
                for (int currX = 0; currX <= 15; ++currX) {
                    final int worldX = currX | worldChunkX;
                    final int worldZ = currZ | worldChunkZ;
                    // NX = -1 on x
                    // PX = +1 on x
                    // NZ = -1 on z
                    // PZ = +1 on z
                    // C = center

                    // index = (x + 1) | ((z + 1) << 5)

                    // X = 0, Z = 0
                    final int heightMapC  = heightMap[(currX + 1) | ((currZ + 1) << 5)];

                    // X = -1
                    final int heightMapNX = heightMap[(currX - 1 + 1) | ((currZ + 1) << 5)];

                    // X = 1
                    final int heightMapPX = heightMap[(currX + 1 + 1) | ((currZ + 1) << 5)];

                    // Z = -1
                    final int heightMapNZ = heightMap[(currX + 1) | ((currZ - 1 + 1) << 5)];

                    // Z = 1
                    final int heightMapPZ = heightMap[(currX + 1) | ((currZ + 1 + 1) << 5)];

                    for (int currY = (highestNonEmptySection << 4) + 16; currY > heightMapC;) {
                        final SWMRNibbleArray nibble = this.getNibbleFromCache(chunkX, currY >> 4, chunkZ);
                        if (nibble == null) {
                            // skip this section, has no data
                            currY = (currY - 16) & (~15);
                            continue;
                        }

                        long propagateDirectionBitset = 0L;
                        // +X
                        propagateDirectionBitset |= ((currY <= heightMapPX) ? 1L : 0L) << AxisDirection.POSITIVE_X.ordinal();

                        // -X
                        propagateDirectionBitset |= ((currY <= heightMapNX) ? 1L : 0L) << AxisDirection.NEGATIVE_X.ordinal();

                        // +Z
                        propagateDirectionBitset |= ((currY <= heightMapPZ) ? 1L : 0L) << AxisDirection.POSITIVE_Z.ordinal();

                        // -Z
                        propagateDirectionBitset |= ((currY <= heightMapNZ) ? 1L : 0L) << AxisDirection.NEGATIVE_Z.ordinal();

                        // +Y is always 0 since we don't want to check upwards

                        // -Y:
                        propagateDirectionBitset |= ((currY == (heightMapC + 1)) ? 1L : 0L) << AxisDirection.NEGATIVE_Y.ordinal();

                        // now setup source
                        // unlike block checks, we don't use FORCE_WRITE here because our init doesn't rely on above nibbles
                        // when initialising
                        nibble.set((worldX & 15) | ((worldZ & 15) << 4) | ((currY & 15) << 8), 15);
                        if (propagateDirectionBitset != 0L) {
                            this.appendToIncreaseQueue(
                                    ((worldX + (worldZ << 6) + (currY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | (15L << (6 + 6 + 16))
                                            | propagateDirectionBitset << (6 + 6 + 16 + 4)
                                            // above heightmap, so not sidedly transparent
                            );
                        }

                        --currY;
                    }

                    // Just in case there's a conditionally transparent block at the top.
                    this.tryPropagateSkylight(world, worldX, heightMapC, worldZ, false, false);
                }
            }
        } // else: apparently the chunk is empty

        if (needsEdgeChecks) {
            // not required to propagate here, but this will reduce the hit of the edge checks
            this.performLightIncrease(lightAccess);

            for (int y = this.maxLightSection; y >= this.minLightSection; --y) {
                this.checkNullSection(chunkX, y, chunkZ, false);
            }
            this.checkChunkEdges(lightAccess, chunk, this.minLightSection, this.maxLightSection);
        } else {
            for (int y = highestNonEmptySection; y >= this.minLightSection; --y) {
                this.checkNullSection(chunkX, y, chunkZ, false);
            }
            this.propagateNeighbourLevels(lightAccess, chunk, this.minLightSection, highestNonEmptySection);

            this.performLightIncrease(lightAccess);
        }
    }

    protected final void processDelayedIncreases() {
        // copied from performLightIncrease
        final long[] queue = this.increaseQueue;
        final int decodeOffsetX = -this.encodeOffsetX;
        final int decodeOffsetY = -this.encodeOffsetY;
        final int decodeOffsetZ = -this.encodeOffsetZ;

        for (int i = 0, len = this.increaseQueueInitialLength; i < len; ++i) {
            final long queueValue = queue[i];

            final int posX = ((int)queueValue & 63) + decodeOffsetX;
            final int posZ = (((int)queueValue >>> 6) & 63) + decodeOffsetZ;
            final int posY = (((int)queueValue >>> 12) & ((1 << 16) - 1)) + decodeOffsetY;
            final int propagatedLightLevel = (int)((queueValue >>> (6 + 6 + 16)) & 0xF);

            this.setLightLevel(posX, posY, posZ, propagatedLightLevel);
        }
    }

    protected final void processDelayedDecreases() {
        // copied from performLightDecrease
        final long[] queue = this.decreaseQueue;
        final int decodeOffsetX = -this.encodeOffsetX;
        final int decodeOffsetY = -this.encodeOffsetY;
        final int decodeOffsetZ = -this.encodeOffsetZ;

        for (int i = 0, len = this.decreaseQueueInitialLength; i < len; ++i) {
            final long queueValue = queue[i];

            final int posX = ((int)queueValue & 63) + decodeOffsetX;
            final int posZ = (((int)queueValue >>> 6) & 63) + decodeOffsetZ;
            final int posY = (((int)queueValue >>> 12) & ((1 << 16) - 1)) + decodeOffsetY;

            this.setLightLevel(posX, posY, posZ, 0);
        }
    }

    // delaying the light set is useful for block changes since they need to worry about initialising nibblearrays
    // while also queueing light at the same time (initialising nibblearrays might depend on nibbles above, so
    // clobbering the light values will result in broken propagation)
    protected final int tryPropagateSkylight(final BlockView world, final int worldX, int startY, final int worldZ,
                                             final boolean extrudeInitialised, final boolean delayLightSet) {
        final BlockPos.Mutable mutablePos = this.mutablePos3;
        final int encodeOffset = this.coordinateOffset;
        final long propagateDirection = AxisDirection.POSITIVE_Y.everythingButThisDirection; // just don't check upwards.

        if (this.getLightLevelExtruded(worldX, startY + 1, worldZ) != 15) {
            return startY;
        }

        // ensure this section is always checked
        this.checkNullSection(worldX >> 4, startY >> 4, worldZ >> 4, extrudeInitialised);

        BlockState above = this.getBlockState(worldX, startY + 1, worldZ);
        if (above == null) {
            above = AIR_BLOCK_STATE;
        }

        for (;startY >= (this.minLightSection << 4); --startY) {
            if ((startY & 15) == 15) {
                // ensure this section is always checked
                this.checkNullSection(worldX >> 4, startY >> 4, worldZ >> 4, extrudeInitialised);
            }
            BlockState current = this.getBlockState(worldX, startY, worldZ);
            if (current == null) {
                current = AIR_BLOCK_STATE;
            }

            final VoxelShape fromShape;
            if (((ExtendedAbstractBlockState)above).isConditionallyFullOpaque()) {
                this.mutablePos2.set(worldX, startY + 1, worldZ);
                fromShape = above.getCullingFace(world, this.mutablePos2, AxisDirection.NEGATIVE_Y.nms);
                if (VoxelShapes.unionCoversFullCube(VoxelShapes.empty(), fromShape)) {
                    // above wont let us propagate
                    break;
                }
            } else {
                fromShape = VoxelShapes.empty();
            }

            final int opacityIfCached = ((ExtendedAbstractBlockState)current).getOpacityIfCached();
            // does light propagate from the top down?
            if (opacityIfCached != -1) {
                if (opacityIfCached != 0) {
                    // we cannot propagate 15 through this
                    break;
                }
                // most of the time it falls here.
                // add to propagate
                // light set delayed until we determine if this nibble section is null
                this.appendToIncreaseQueue(
                        ((worldX + (worldZ << 6) + (startY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                | (15L << (6 + 6 + 16)) // we know we're at full lit here
                                | (propagateDirection << (6 + 6 + 16 + 4))
                );
            } else {
                mutablePos.set(worldX, startY, worldZ);
                long flags = 0L;
                if (((ExtendedAbstractBlockState)current).isConditionallyFullOpaque()) {
                    final VoxelShape cullingFace = current.getCullingFace(world, mutablePos, AxisDirection.POSITIVE_Y.nms);

                    if (VoxelShapes.unionCoversFullCube(fromShape, cullingFace)) {
                        // can't propagate here, we're done on this column.
                        break;
                    }
                    flags |= FLAG_HAS_SIDED_TRANSPARENT_BLOCKS;
                }

                final int opacity = current.getOpacity(world, mutablePos);
                if (opacity > 0) {
                    // let the queued value (if any) handle it from here.
                    break;
                }

                // light set delayed until we determine if this nibble section is null
                this.appendToIncreaseQueue(
                        ((worldX + (worldZ << 6) + (startY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                | (15L << (6 + 6 + 16)) // we know we're at full lit here
                                | (propagateDirection << (6 + 6 + 16 + 4))
                                | flags
                );
            }

            above = current;

            if (this.getNibbleFromCache(worldX >> 4, startY >> 4, worldZ >> 4) == null) {
                // we skip empty sections here, as this is just an easy way of making sure the above block
                // can propagate through air.

                // nothing can propagate in null sections, remove the queue entry for it
                --this.increaseQueueInitialLength;

                // advance currY to the the top of the section below
                startY = (startY) & (~15);
                // note: this value ^ is actually 1 above the top, but the loop decrements by 1 so we actually
                // end up there

                // make sure this is marked as AIR
                above = AIR_BLOCK_STATE;
            } else if (!delayLightSet) {
                this.setLightLevel(worldX, startY, worldZ, 15);
            }
        }

        return startY;
    }
}
