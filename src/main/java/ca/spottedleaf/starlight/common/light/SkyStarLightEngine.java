package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunkSection;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.IChunkLightProvider;
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

    @Override
    protected void handleEmptySectionChanges(final IChunkLightProvider lightAccess, final IChunk chunk,
                                             final Boolean[] emptinessChanges, final boolean unlit) {
        final int chunkX = chunk.getPos().x;
        final int chunkZ = chunk.getPos().z;

        final boolean[][] chunkEmptinessMap = this.getEmptinessMap(chunkX, chunkZ);

        // index = (cx + 2) + 5*(cz + 2)
        long loadedNeighboursBitset = 0L;
        long unloadedNeighbourBitset = 0L;

        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                final IChunk neighbour = this.getChunkInCache(dx + chunkX, dz + chunkZ);
                if (neighbour == null) {
                    continue;
                }

                final boolean[][] neighbourEmptinessMap = this.getEmptinessMap(dx + chunkX, dz + chunkZ);
                for (int i = 0; i < neighbourEmptinessMap.length; ++i) {
                    // index = (cx + 1) + 3*(cz + 1)
                    final int dx2 = (i % 3) - 1;
                    final int dz2 = (i / 3) - 1;

                    final int bitsetIndex = (dx2 + dx + 2) + 5*(dz2 + dz + 2);
                    if (neighbourEmptinessMap[i] == null) {
                        unloadedNeighbourBitset |= 1L << bitsetIndex;
                    } else {
                        loadedNeighboursBitset |= 1L << bitsetIndex;
                    }
                }
            }
        }

        loadedNeighboursBitset &= ~unloadedNeighbourBitset;
        loadedNeighboursBitset |=  1L << ((0 + 2) + 5*(0 + 2));

        final boolean[] needsDeInitCheck = new boolean[9];
        final boolean needsInit = unlit || chunkEmptinessMap[ExtendedChunk.getEmptinessMapIndex(0, 0)] == null;
        if (needsInit) {
            chunkEmptinessMap[ExtendedChunk.getEmptinessMapIndex(0, 0)] = new boolean[16];;
        }

        // this chunk is new, so we need to init neighbours
        // because this chunk might have been modified inbetween loading/saving, we have to rewrite the emptiness map
        // for our neighbours, so don't bother checking if they exist & whether they even needed a de-init recalc
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                final ExtendedChunk neighbour = ((ExtendedChunk)this.getChunkInCache(dx + chunkX, dz + chunkZ));
                if (neighbour == null) {
                    // if the neighbour hasn't initialised its own empty map, we can't use it
                    // when it does though, it'll come by and initialise our map for it
                    continue;
                }
                final boolean[][] neighbourEmptinessMap = this.getEmptinessMap(dx + chunkX, dz + chunkZ);

                if (needsInit && (dx | dz) != 0) {
                    // init neighbour
                    neighbourEmptinessMap[ExtendedChunk.getEmptinessMapIndex(-dx, -dz)] = new boolean[16];

                    if (neighbourEmptinessMap[ExtendedChunk.getEmptinessMapIndex(0, 0)] != null) {
                        // init ourselves
                        System.arraycopy(
                                neighbourEmptinessMap[ExtendedChunk.getEmptinessMapIndex(0, 0)],
                                0,
                                chunkEmptinessMap[ExtendedChunk.getEmptinessMapIndex(dx, dz)] = new boolean[16],
                                0,
                                16
                        );
                    }
                }

                // check if our neighbours are ready for a recalc

                long neighboursMask = 0L;
                for (int dz2 = -1; dz2 <= 1; ++dz2) {
                    for (int dx2 = -1; dx2 <= 1; ++dx2) {
                        neighboursMask |= 1L << ((dx2 + dx + 2) + 5*(dz2 + dz + 2));
                    }
                }

                if ((loadedNeighboursBitset & neighboursMask) == neighboursMask) {
                    // can check for de-init
                    needsDeInitCheck[(dx + 1) + 3 * (dz + 1)] = true;
                }
            }
        }

        for (int sectionY = (emptinessChanges.length - 1); sectionY >= 0; --sectionY) {
            final Boolean valueBoxed = emptinessChanges[sectionY];
            if (valueBoxed == null) {
                continue;
            }

            final boolean empty = valueBoxed.booleanValue();

            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    final ExtendedChunk neighbour = (ExtendedChunk)this.getChunkInCache(dx + chunkX, dz + chunkZ);
                    if (neighbour == null) {
                        // this is the case on the client, we _assume_ the server inits and sends to us.
                        // or we're on the server, and the neighbours haven't generated light yet.
                        continue;
                    }

                    // init nibbles as needed

                    if (!empty) {
                        // if we're not empty, we also need to initialise nibbles
                        // note: if we're unlit, we absolutely do not want to extrude, as light data isn't set up
                        final boolean extrude = (dx | dz) != 0 || !unlit;
                        for (int dy = 1; dy >= -1; --dy) {
                            this.initNibbleForLitChunk(dx + chunkX, dy + sectionY, dz + chunkZ, extrude, false);
                        }
                    }

                    // update neighbour map
                    this.getEmptinessMap(dx + chunkX, dz + chunkZ)[ExtendedChunk.getEmptinessMapIndex(-dx, -dz)][sectionY] = empty;
                }
            }
        }

        // check for de-init, only runs if this just had data loaded in (or is being lit)
        for (int i = 0; i < needsDeInitCheck.length; ++i) {
            if (!needsDeInitCheck[i]) {
                continue;
            }

            // index = (cx + 1) + 3*(cz + 1)
            final int neighbourX = (i % 3) - 1 + chunkX;
            final int neighbourZ = (i / 3) - 1 + chunkZ;

            final boolean[][] neighbourEmptinessMap = this.getEmptinessMap(neighbourX, neighbourZ);

            for (int sectionY = 16; sectionY >= -1; --sectionY) {
                final SWMRNibbleArray nibble = this.getNibbleFromCache(neighbourX, sectionY, neighbourZ);
                if (nibble == null || nibble.isNullNibbleUpdating()) {
                    // already null
                    continue;
                }

                // check neighbours to see if we need to de-init this one
                boolean allEmpty = true;
                neighbour_search:
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        for (int dx = -1; dx <= 1; ++dx) {
                            final int y = sectionY + dy;
                            if (y < 0 || y > 15) {
                                // empty
                                continue;
                            }
                            if (!neighbourEmptinessMap[ExtendedChunk.getEmptinessMapIndex(dx, dz)][y]) {
                                allEmpty = false;
                                break neighbour_search;
                            }
                        }
                    }
                }

                if (allEmpty) {
                    // all were empty, so de-init
                    nibble.setNull();
                }
            }
        }
    }

    protected final void initNibbleForLitChunk(final int chunkX, final int chunkY, final int chunkZ, final boolean extrude,
                                               final boolean initRemovedNibbles) {
        if (chunkY < -1 || chunkY > 16 || this.getChunkInCache(chunkX, chunkZ) == null) {
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

        final boolean[] emptinessMap = this.getEmptinessMap(chunkX, chunkZ)[ExtendedChunk.getEmptinessMapIndex(0, 0)];

        // are we above this chunk's lowest empty section?
        int lowestY = -2;
        for (int currY = 15; currY >= 0; --currY) {
            if (emptinessMap == null) {
                // cannot delay nibble init for lit chunks, as we need to init to propagate into them.
                final ChunkSection current = this.getChunkSection(chunkX, currY, chunkZ);
                if (current == null || current == EMPTY_CHUNK_SECTION) {
                    continue;
                }
            } else {
                if (emptinessMap[currY]) {
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
            for (int currY = chunkY + 1; currY <= 16; ++currY) {
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

    protected final void rewriteNibbleCacheForSkylight(final IChunk chunk) {
        for (int index = 0, max = this.nibbleCache.length; index < max; ++index) {
            final SWMRNibbleArray nibble = this.nibbleCache[index];
            if (nibble != null && nibble.isNullNibbleUpdating()) {
                // stop propagation in these areas
                this.nibbleCache[index] = null;
                nibble.updateVisible();
            }
        }
    }

    protected final boolean[] nullPropagationCheckCache = new boolean[16 - (-1) + 1];

    // rets whether neighbours were init'd

    protected final boolean checkNullSection(final int chunkX, final int chunkY, final int chunkZ,
                                             final boolean extrudeInitialised) {
        // null chunk sections may have nibble neighbours in the horizontal 1 radius that are
        // non-null. Propagation to these neighbours is necessary.
        // What makes this easy is we know none of these neighbours are non-empty (otherwise
        // this nibble would be initialised). So, we don't have to initialise
        // the neighbours in the full 1 radius, because there's no worry that any "paths"
        // to the neighbours on this horizontal plane are blocked.
        if (chunkY < -1 || chunkY > 16 || this.nullPropagationCheckCache[chunkY + 1]) {
            return false;
        }
        this.nullPropagationCheckCache[chunkY + 1] = true;

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
            if (++chunkY > 16) {
                return 15;
            }

            nibble = this.getNibbleFromCache(chunkX, chunkY, chunkZ);

            if (nibble != null) {
                return nibble.getUpdating(worldX, 0, worldZ);
            }
        }
    }

    public SkyStarLightEngine(final boolean isClientSide) {
        super(true, isClientSide);
    }

    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(final IChunk chunk) {
        return ((ExtendedChunk)chunk).getSkyNibbles();
    }

    @Override
    protected boolean[][] getEmptinessMap(final IChunk chunk) {
        return ((ExtendedChunk)chunk).getEmptinessMap();
    }

    @Override
    protected void setNibbles(final IChunk chunk, final SWMRNibbleArray[] to) {
        ((ExtendedChunk)chunk).setSkyNibbles(to);
    }

    @Override
    protected boolean canUseChunk(final IChunk chunk) {
        // can only use chunks for sky stuff if their sections have been init'd
        return chunk.getStatus().isAtLeast(ChunkStatus.LIGHT)
                && (this.isClientSide ? ((ExtendedChunk)chunk).getEmptinessMap()[ExtendedChunk.getEmptinessMapIndex(0, 0)] != null : chunk.hasLight());
    }

    @Override
    protected void checkChunkEdges(final IChunkLightProvider lightAccess, final IChunk chunk, final int fromSection,
                                   final int toSection) {
        this.rewriteNibbleCacheForSkylight(chunk);
        super.checkChunkEdges(lightAccess, chunk, fromSection, toSection);
    }

    @Override
    protected void checkChunkEdges(final IChunkLightProvider lightAccess, final IChunk chunk, final ShortCollection sections) {
        this.rewriteNibbleCacheForSkylight(chunk);
        super.checkChunkEdges(lightAccess, chunk, sections);
    }

    @Override
    protected void checkBlock(final IChunkLightProvider lightAccess, final int worldX, final int worldY, final int worldZ) {
        // blocks can change opacity
        // blocks can change direction of propagation

        // same logic applies from BlockStarLightEngine#checkBlock

        final int encodeOffset = this.coordinateOffset;

        final int currentLevel = this.getLightLevel(worldX, worldY, worldZ);

        if (currentLevel == 15) {
            // must re-propagate clobbered source
            this.increaseQueue[this.increaseQueueInitialLength++] =
                    ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                            | (currentLevel & 0xFL) << (6 + 6 + 16)
                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                            | FLAG_HAS_SIDED_TRANSPARENT_BLOCKS; // don't know if the block is conditionally transparent
        } else {
            this.setLightLevel(worldX, worldY, worldZ, 0);
        }

        this.decreaseQueue[this.decreaseQueueInitialLength++] =
                ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                        | (currentLevel & 0xFL) << (6 + 6 + 16)
                        | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4));
    }

    protected final int[] heightMapBlockChange = new int[16 * 16];
    {
        Arrays.fill(this.heightMapBlockChange, -1024); // clear heightmap
    }

    @Override
    protected void propagateBlockChanges(final IChunkLightProvider lightAccess, final IChunk atChunk, final Set<BlockPos> positions) {
        this.rewriteNibbleCacheForSkylight(atChunk);
        Arrays.fill(this.nullPropagationCheckCache, false);

        final IBlockReader world = lightAccess.getWorld();
        final int chunkX = atChunk.getPos().x;
        final int chunkZ = atChunk.getPos().z;
        final int heightMapOffset = chunkX * -16 + (chunkZ * (-16 * 16));

        // setup heightmap for changes
        int highestBlockY = -1024;
        for (final BlockPos pos : positions) {
            final int index = pos.getX() + (pos.getZ() << 4) + heightMapOffset;
            final int curr = this.heightMapBlockChange[index];
            if (pos.getY() > curr) {
                this.heightMapBlockChange[index] = pos.getY();
            }
            if (pos.getY() > highestBlockY) {
                highestBlockY = pos.getY();
            }
        }

        // note: light sets are delayed while processing skylight source changes due to how
        // nibbles are initialised, as we want to avoid clobbering nibble values so what when
        // below nibbles are initialised they aren't reading from partially modified nibbles

        // now we can recalculate the sources for the changed columns
        for (int index = 0; index < (16 * 16); ++index) {
            final int maxY = this.heightMapBlockChange[index];
            if (maxY == -1024) {
                // not changed
                continue;
            }
            this.heightMapBlockChange[index] = -1024; // restore default for next caller

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

                for (int currY = maxPropagationY; currY >= (-1 << 4); --currY) {
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
                    this.decreaseQueue[this.decreaseQueueInitialLength++] =
                            ((columnX + (columnZ << 6) + (currY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                    | (15L << (6 + 6 + 16))
                                    | (propagateDirection << (6 + 6 + 16 + 4));
                                    // do not set transparent blocks for the same reason we don't in the checkBlock method
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
    protected void lightChunk(final IChunkLightProvider lightAccess, final IChunk chunk, final boolean needsEdgeChecks) {
        this.rewriteNibbleCacheForSkylight(chunk);
        Arrays.fill(this.nullPropagationCheckCache, false);

        final IBlockReader world = lightAccess.getWorld();
        final ChunkPos chunkPos = chunk.getPos();
        final int chunkX = chunkPos.x;
        final int chunkZ = chunkPos.z;

        final ChunkSection[] sections = chunk.getSections();

        int highestNonEmptySection = 15;
        while (highestNonEmptySection == -1 ||
                sections[highestNonEmptySection] == null || sections[highestNonEmptySection].isEmpty()) {
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
                        this.increaseQueue[this.increaseQueueInitialLength++] =
                                ((currX + (currZ << 6) + (currY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                        | (15L << (6 + 6 + 16)) // we know we're at full lit here
                                        | (propagateDirection << (6 + 6 + 16 + 4));
                                        // no transparent flag, we know for a fact there are no blocks here that could be directionally transparent (as the section is EMPTY)
                    }
                }
            }

            if (highestNonEmptySection-- == -1) {
                break;
            }
        }

        if (highestNonEmptySection >= 0) {
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
                    int maxY = ((-1 -1) << 4);

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
                        final int highestYValue = highestBitSet >> 1;
                        maxY = highestYValue | (sectionY << 4);
                        break;
                    }
                    heightMap[(currX - worldChunkX + 1) | ((currZ - worldChunkZ + 1) << 5)] = maxY;
                }
            }

            // now setup sources
            final long[] queue = this.increaseQueue;
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

                    int queueLength = this.increaseQueueInitialLength;

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
                            queue[queueLength++] =
                                    ((worldX + (worldZ << 6) + (currY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | (15L << (6 + 6 + 16))
                                            | propagateDirectionBitset << (6 + 6 + 16 + 4);
                                            // above heightmap, so not sidedly transparent
                        }

                        --currY;
                    }

                    this.increaseQueueInitialLength = queueLength;
                    // Just in case there's a conditionally transparent block at the top.
                    this.tryPropagateSkylight(world, worldX, heightMapC, worldZ, false, false);
                }
            }
        } // else: apparently the chunk is empty

        if (needsEdgeChecks) {
            // not required to propagate here, but this will reduce the hit of the edge checks
            this.performLightIncrease(lightAccess);

            for (int y = 16; y >= -1; --y) {
                this.checkNullSection(chunkX, y, chunkZ, false);
            }
            this.checkChunkEdges(lightAccess, chunk, -1, 16);
        } else {
            for (int y = highestNonEmptySection; y >= -1; --y) {
                this.checkNullSection(chunkX, y, chunkZ, false);
            }
            this.propagateNeighbourLevels(lightAccess, chunk, -1, highestNonEmptySection);

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
    protected final int tryPropagateSkylight(final IBlockReader world, final int worldX, int startY, final int worldZ,
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

        for (;startY >= (-1 << 4); --startY) {
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
                this.mutablePos2.setPos(worldX, startY + 1, worldZ);
                fromShape = above.getFaceOcclusionShape(world, this.mutablePos2, AxisDirection.NEGATIVE_Y.nms);
                if (VoxelShapes.faceShapeCovers(VoxelShapes.empty(), fromShape)) {
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
                this.increaseQueue[this.increaseQueueInitialLength++] =
                        ((worldX + (worldZ << 6) + (startY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                | (15L << (6 + 6 + 16)) // we know we're at full lit here
                                | (propagateDirection << (6 + 6 + 16 + 4));
            } else {
                mutablePos.setPos(worldX, startY, worldZ);
                long flags = 0L;
                if (((ExtendedAbstractBlockState)current).isConditionallyFullOpaque()) {
                    final VoxelShape cullingFace = current.getFaceOcclusionShape(world, mutablePos, AxisDirection.POSITIVE_Y.nms);

                    if (VoxelShapes.faceShapeCovers(fromShape, cullingFace)) {
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
                this.increaseQueue[this.increaseQueueInitialLength++] =
                        ((worldX + (worldZ << 6) + (startY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                | (15L << (6 + 6 + 16)) // we know we're at full lit here
                                | (propagateDirection << (6 + 6 + 16 + 4))
                                | flags;
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
