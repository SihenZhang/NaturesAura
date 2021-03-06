package de.ellpeck.naturesaura;

import baubles.api.BaublesApi;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.misc.IWorldData;
import de.ellpeck.naturesaura.api.multiblock.IMultiblock;
import de.ellpeck.naturesaura.blocks.multi.Multiblock;
import de.ellpeck.naturesaura.compat.Compat;
import de.ellpeck.naturesaura.misc.WorldData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.lwjgl.util.vector.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class InternalHooks implements NaturesAuraAPI.IInternalHooks {
    @Override
    public boolean extractAuraFromPlayer(EntityPlayer player, int amount, boolean simulate) {
        return this.auraPlayerInteraction(player, amount, true, simulate);
    }

    @Override
    public boolean insertAuraIntoPlayer(EntityPlayer player, int amount, boolean simulate) {
        return this.auraPlayerInteraction(player, amount, false, simulate);
    }

    private boolean auraPlayerInteraction(EntityPlayer player, int amount, boolean extract, boolean simulate) {
        if (extract && player.capabilities.isCreativeMode)
            return true;

        if (Compat.baubles) {
            IItemHandler baubles = BaublesApi.getBaublesHandler(player);
            for (int i = 0; i < baubles.getSlots(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (!stack.isEmpty() && stack.hasCapability(NaturesAuraAPI.capAuraContainer, null)) {
                    IAuraContainer container = stack.getCapability(NaturesAuraAPI.capAuraContainer, null);
                    if (extract)
                        amount -= container.drainAura(amount, simulate);
                    else
                        amount -= container.storeAura(amount, simulate);
                    if (amount <= 0)
                        return true;
                }
            }
        }

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.hasCapability(NaturesAuraAPI.capAuraContainer, null)) {
                IAuraContainer container = stack.getCapability(NaturesAuraAPI.capAuraContainer, null);
                if (extract)
                    amount -= container.drainAura(amount, simulate);
                else
                    amount -= container.storeAura(amount, simulate);
                if (amount <= 0)
                    return true;
            }
        }

        return false;
    }

    @Override
    public void spawnMagicParticle(double posX, double posY, double posZ, double motionX, double motionY, double motionZ, int color, float scale, int maxAge, float gravity, boolean collision, boolean fade) {
        NaturesAura.proxy.spawnMagicParticle(posX, posY, posZ, motionX, motionY, motionZ, color, scale, maxAge, gravity, collision, fade);
    }

    @Override
    public void spawnParticleStream(float startX, float startY, float startZ, float endX, float endY, float endZ, float speed, int color, float scale) {
        Vector3f dir = new Vector3f(endX - startX, endY - startY, endZ - startZ);
        float length = dir.length();
        if (length > 0) {
            dir.normalise();
            this.spawnMagicParticle(startX, startY, startZ,
                    dir.x * speed, dir.y * speed, dir.z * speed,
                    color, scale, (int) (length / speed), 0F, false, false);
        }
    }

    @Override
    public void setParticleDepth(boolean depth) {
        NaturesAura.proxy.setParticleDepth(depth);
    }

    @Override
    public void setParticleSpawnRange(int range) {
        NaturesAura.proxy.setParticleSpawnRange(range);
    }

    @Override
    public IMultiblock createMultiblock(ResourceLocation name, String[][] pattern, Object... rawMatchers) {
        return new Multiblock(name, pattern, rawMatchers);
    }

    @Override
    public List<Tuple<Vec3d, Integer>> getActiveEffectPowders(World world, AxisAlignedBB area, ResourceLocation name) {
        world.profiler.func_194340_a(() -> NaturesAura.MOD_ID + ":getActiveEffectPowders");
        List<Tuple<Vec3d, Integer>> found = new ArrayList<>();
        for (Tuple<Vec3d, Integer> powder : ((WorldData) IWorldData.getWorldData(world)).effectPowders.get(name))
            if (area.contains(powder.getFirst()))
                found.add(powder);
        world.profiler.endSection();
        return found;
    }

    @Override
    public boolean isEffectPowderActive(World world, BlockPos pos, ResourceLocation name) {
        world.profiler.func_194340_a(() -> NaturesAura.MOD_ID + ":isEffectPowderActive");
        Vec3d posVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        List<Tuple<Vec3d, Integer>> powders = this.getActiveEffectPowders(world, new AxisAlignedBB(pos).grow(64), name);
        for (Tuple<Vec3d, Integer> powder : powders) {
            AxisAlignedBB bounds = Helper.aabb(powder.getFirst()).grow(powder.getSecond());
            if (bounds.contains(posVec)) {
                world.profiler.endSection();
                return true;
            }
        }
        world.profiler.endSection();
        return false;
    }

    @Override
    public void getAuraSpotsInArea(World world, BlockPos pos, int radius, BiConsumer<BlockPos, Integer> consumer) {
        world.profiler.func_194340_a(() -> NaturesAura.MOD_ID + ":getSpotsInArea");
        Helper.getAuraChunksInArea(world, pos, radius, chunk -> chunk.getSpotsInArea(pos, radius, consumer));
        world.profiler.endSection();
    }

    @Override
    public int getSpotAmountInArea(World world, BlockPos pos, int radius) {
        MutableInt result = new MutableInt();
        this.getAuraSpotsInArea(world, pos, radius, (blockpos, drainSpot) -> result.increment());
        return result.intValue();
    }

    @Override
    public int getAuraInArea(World world, BlockPos pos, int radius) {
        MutableInt result = new MutableInt(IAuraChunk.DEFAULT_AURA);
        this.getAuraSpotsInArea(world, pos, radius, (blockPos, drainSpot) -> result.add(drainSpot));
        return result.intValue();
    }

    @Override
    public int triangulateAuraInArea(World world, BlockPos pos, int radius) {
        MutableFloat result = new MutableFloat(IAuraChunk.DEFAULT_AURA);
        IAuraChunk.getSpotsInArea(world, pos, radius, (blockPos, spot) -> {
            float percentage = 1F - (float) pos.getDistance(blockPos.getX(), blockPos.getY(), blockPos.getZ()) / radius;
            result.add(spot * percentage);
        });
        return result.intValue();
    }

    @Override
    public BlockPos getLowestAuraDrainSpot(World world, BlockPos pos, int radius, BlockPos defaultSpot) {
        MutableInt lowestAmount = new MutableInt(Integer.MAX_VALUE);
        MutableObject<BlockPos> lowestSpot = new MutableObject<>();
        this.getAuraSpotsInArea(world, pos, radius, (blockPos, drainSpot) -> {
            if (drainSpot < lowestAmount.intValue()) {
                lowestAmount.setValue(drainSpot);
                lowestSpot.setValue(blockPos);
            }
        });
        BlockPos lowest = lowestSpot.getValue();
        if (lowest == null || lowestAmount.intValue() >= 0)
            lowest = defaultSpot;
        return lowest;
    }

    @Override
    public BlockPos getHighestAuraDrainSpot(World world, BlockPos pos, int radius, BlockPos defaultSpot) {
        MutableInt highestAmount = new MutableInt(Integer.MIN_VALUE);
        MutableObject<BlockPos> highestSpot = new MutableObject<>();
        this.getAuraSpotsInArea(world, pos, radius, (blockPos, drainSpot) -> {
            if (drainSpot > highestAmount.intValue()) {
                highestAmount.setValue(drainSpot);
                highestSpot.setValue(blockPos);
            }
        });
        BlockPos highest = highestSpot.getValue();
        if (highest == null || highestAmount.intValue() <= 0)
            highest = defaultSpot;
        return highest;
    }
}
