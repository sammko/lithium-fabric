package me.jellysquid.mods.lithium.common.entity;

import me.jellysquid.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import me.jellysquid.mods.lithium.common.util.Producer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.CollisionView;
import net.minecraft.world.EntityView;
import net.minecraft.world.border.WorldBorder;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class LithiumEntityCollisions {
    public static final double EPSILON = 1.0E-7D;

    /**
     * [VanillaCopy] CollisionView#getBlockCollisions(Entity, Box)
     * This is a much, much faster implementation which uses simple collision testing against full-cube block shapes.
     * Checks against the world border are replaced with our own optimized functions which do not go through the
     * VoxelShape system.
     */
    public static Stream<VoxelShape> getBlockCollisions(CollisionView world, Entity entity, Box box) {
        if (isBoxEmpty(box)) {
            return Stream.empty();
        }

        final ChunkAwareBlockCollisionSweeper sweeper = new ChunkAwareBlockCollisionSweeper(world, entity, box);

        return StreamSupport.stream(new Spliterators.AbstractSpliterator<VoxelShape>(Long.MAX_VALUE, Spliterator.NONNULL | Spliterator.IMMUTABLE) {
            private boolean skipWorldBorderCheck = entity == null;

            @Override
            public boolean tryAdvance(Consumer<? super VoxelShape> consumer) {
                if (!this.skipWorldBorderCheck) {
                    this.skipWorldBorderCheck = true;

                    if (canEntityCollideWithWorldBorder(world, entity)) {
                        consumer.accept(world.getWorldBorder().asVoxelShape());

                        return true;
                    }
                }

                VoxelShape shape = sweeper.step();
                if (shape != null) {
                    consumer.accept(shape);
                    return true;
                }

                return false;
            }
        }, false);
    }

    /**
     * See {@link LithiumEntityCollisions#getBlockCollisions(CollisionView, Entity, Box)}
     *
     * @return True if the box (possibly that of an entity's) collided with any blocks
     */
    public static boolean doesBoxCollideWithBlocks(CollisionView world, Entity entity, Box box) {
        if (isBoxEmpty(box)) {
            return false;
        }

        final ChunkAwareBlockCollisionSweeper sweeper = new ChunkAwareBlockCollisionSweeper(world, entity, box);

        VoxelShape shape = sweeper.step();
        return shape != null;
    }

    /**
     * See {@link LithiumEntityCollisions#getEntityCollisions(EntityView, Entity, Box, Predicate)}
     *
     * @return True if the box (possibly that of an entity's) collided with any other entities
     */
    public static boolean doesBoxCollideWithEntities(EntityView view, Entity entity, Box box, Predicate<Entity> predicate) {
        if (isBoxEmpty(box)) {
            return false;
        }

        return getEntityCollisionProducer(view, entity, box.expand(EPSILON), predicate).computeNext(null);
    }

    /**
     * Returns a stream of entity collision boxes.
     */
    public static Stream<VoxelShape> getEntityCollisions(EntityView view, Entity entity, Box box, Predicate<Entity> predicate) {
        if (isBoxEmpty(box)) {
            return Stream.empty();
        }

        return Producer.asStream(getEntityCollisionProducer(view, entity, box.expand(EPSILON), predicate));
    }

    /**
     * [VanillaCopy] EntityView#getEntityCollisions
     * Re-implements the function named above without stream code or unnecessary allocations. This can provide a small
     * boost in some situations (such as heavy entity crowding) and reduces the allocation rate significantly.
     */
    public static Producer<VoxelShape> getEntityCollisionProducer(EntityView view, Entity entity, Box box, Predicate<Entity> predicate) {
        return new Producer<VoxelShape>() {
            private Iterator<Entity> it;

            @Override
            public boolean computeNext(Consumer<? super VoxelShape> consumer) {
                if (this.it == null) {
                    this.it = view.getOtherEntities(entity, box).iterator();
                }

                while (this.it.hasNext()) {
                    Entity otherEntity = this.it.next();

                    if (!predicate.test(otherEntity)) {
                        continue;
                    }
                    /**
                     * method_30948 returns false by default, designed to be overridden by entities whose collisions
                     * should be "hard" (boats and shulkers, for now).
                     *
                     * method_30949 only allows hard collisions if the calling entity is not riding otherEntity as a
                     * vehicle.
                     */
                    if (entity == null) {
                        if (!otherEntity.method_30948()) {
                            continue;
                        }
                    } else if (!entity.method_30949(otherEntity)) {
                        continue;
                    }

                    if (consumer != null) {
                        consumer.accept(VoxelShapes.cuboid(otherEntity.getBoundingBox()));
                    }
                    return true;
                }

                return false;
            }
        };
    }

    /**
     * This provides a faster check for seeing if an entity is within the world border as it avoids going through
     * the slower shape system.
     *
     * @return True if the {@param box} is fully within the {@param border}, otherwise false.
     */
    public static boolean isBoxFullyWithinWorldBorder(WorldBorder border, Box box) {
        double wboxMinX = Math.floor(border.getBoundWest());
        double wboxMinZ = Math.floor(border.getBoundNorth());

        double wboxMaxX = Math.ceil(border.getBoundEast());
        double wboxMaxZ = Math.ceil(border.getBoundSouth());

        return box.minX >= wboxMinX && box.minX < wboxMaxX && box.minZ >= wboxMinZ && box.minZ < wboxMaxZ &&
                box.maxX >= wboxMinX && box.maxX < wboxMaxX && box.maxZ >= wboxMinZ && box.maxZ < wboxMaxZ;
    }

    private static boolean canEntityCollideWithWorldBorder(CollisionView world, Entity entity) {
        WorldBorder border = world.getWorldBorder();

        boolean isInsideBorder = isBoxFullyWithinWorldBorder(border, entity.getBoundingBox().contract(EPSILON));
        boolean isCrossingBorder = isBoxFullyWithinWorldBorder(border, entity.getBoundingBox().expand(EPSILON));

        return !isInsideBorder && isCrossingBorder;
    }

    private static boolean isBoxEmpty(Box box) {
        return box.getAverageSideLength() <= EPSILON;
    }
}