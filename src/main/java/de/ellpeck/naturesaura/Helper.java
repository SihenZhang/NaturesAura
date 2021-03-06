package de.ellpeck.naturesaura;

import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.item.IAuraRecharge;
import de.ellpeck.naturesaura.blocks.tiles.TileEntityImpl;
import de.ellpeck.naturesaura.chunk.AuraChunk;
import net.minecraft.advancements.Advancement;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Helper {

    public static boolean getTileEntitiesInArea(World world, BlockPos pos, int radius, Function<TileEntity, Boolean> consumer) {
        world.profiler.func_194340_a(() -> NaturesAura.MOD_ID + ":getTileEntitiesInArea");
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++) {
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) {
                if (isChunkLoaded(world, x, z)) {
                    for (TileEntity tile : world.getChunk(x, z).getTileEntityMap().values()) {
                        if (tile.getPos().distanceSq(pos) <= radius * radius)
                            if (consumer.apply(tile)) {
                                world.profiler.endSection();
                                return true;
                            }
                    }
                }
            }
        }
        world.profiler.endSection();
        return false;
    }

    public static void getAuraChunksInArea(World world, BlockPos pos, int radius, Consumer<AuraChunk> consumer) {
        world.profiler.func_194340_a(() -> NaturesAura.MOD_ID + ":getAuraChunksInArea");
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++) {
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) {
                if (Helper.isChunkLoaded(world, x, z)) {
                    Chunk chunk = world.getChunk(x, z);
                    if (chunk.hasCapability(NaturesAuraAPI.capAuraChunk, null)) {
                        AuraChunk auraChunk = (AuraChunk) chunk.getCapability(NaturesAuraAPI.capAuraChunk, null);
                        consumer.accept(auraChunk);
                    }
                }
            }
        }
        world.profiler.endSection();
    }

    public static List<EntityItemFrame> getAttachedItemFrames(World world, BlockPos pos) {
        List<EntityItemFrame> frames = world.getEntitiesWithinAABB(EntityItemFrame.class, new AxisAlignedBB(pos).grow(0.25));
        for (int i = frames.size() - 1; i >= 0; i--) {
            EntityItemFrame frame = frames.get(i);
            BlockPos framePos = frame.getHangingPosition().offset(frame.facingDirection.getOpposite());
            if (!pos.equals(framePos))
                frames.remove(i);
        }
        return frames;
    }

    // For some reason this method isn't public in World, but I also don't want to have to make a new BlockPos
    // or use the messy MutableBlockPos system just to see if a chunk is loaded, so this will have to do I guess
    public static boolean isChunkLoaded(World world, int x, int z) {
        IChunkProvider provider = world.getChunkProvider();
        if (provider instanceof ChunkProviderServer)
            return ((ChunkProviderServer) provider).chunkExists(x, z);
        else
            return !provider.provideChunk(x, z).isEmpty();
    }

    public static int blendColors(int c1, int c2, float ratio) {
        int a = (int) ((c1 >> 24 & 0xFF) * ratio + (c2 >> 24 & 0xFF) * (1 - ratio));
        int r = (int) ((c1 >> 16 & 0xFF) * ratio + (c2 >> 16 & 0xFF) * (1 - ratio));
        int g = (int) ((c1 >> 8 & 0xFF) * ratio + (c2 >> 8 & 0xFF) * (1 - ratio));
        int b = (int) ((c1 & 0xFF) * ratio + (c2 & 0xFF) * (1 - ratio));
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    public static boolean areItemsEqual(ItemStack first, ItemStack second, boolean nbt) {
        if (!ItemStack.areItemsEqual(first, second))
            return false;
        return !nbt || ItemStack.areItemStackShareTagsEqual(first, second);
    }

    @SideOnly(Side.CLIENT)
    public static void renderItemInWorld(ItemStack stack) {
        if (!stack.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.disableLighting();
            GlStateManager.pushAttrib();
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popAttrib();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    @SideOnly(Side.CLIENT)
    public static void renderItemInGui(ItemStack stack, int x, int y, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, scale);
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(Minecraft.getMinecraft().fontRenderer, stack, 0, 0, null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    public static boolean putStackOnTile(EntityPlayer player, EnumHand hand, BlockPos pos, int slot, boolean sound) {
        TileEntity tile = player.world.getTileEntity(pos);
        if (tile instanceof TileEntityImpl) {
            IItemHandlerModifiable handler = ((TileEntityImpl) tile).getItemHandler(null);
            if (handler != null) {
                ItemStack handStack = player.getHeldItem(hand);
                if (!handStack.isEmpty()) {
                    ItemStack remain = handler.insertItem(slot, handStack, player.world.isRemote);
                    if (!ItemStack.areItemStacksEqual(remain, handStack)) {
                        if (sound)
                            player.world.playSound(player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM, SoundCategory.PLAYERS, 0.75F, 1F);
                        if (!player.world.isRemote)
                            player.setHeldItem(hand, remain);
                        return true;
                    }
                }

                if (!handler.getStackInSlot(slot).isEmpty()) {
                    if (sound)
                        player.world.playSound(player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM, SoundCategory.PLAYERS, 0.75F, 1F);
                    if (!player.world.isRemote) {
                        ItemStack stack = handler.getStackInSlot(slot);
                        if (!player.addItemStackToInventory(stack)) {
                            EntityItem item = new EntityItem(player.world, player.posX, player.posY, player.posZ, stack);
                            player.world.spawnEntity(item);
                        }
                        handler.setStackInSlot(slot, ItemStack.EMPTY);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public static ICapabilityProvider makeRechargeProvider(ItemStack stack, boolean needsSelected) {
        return new ICapabilityProvider() {
            private final IAuraRecharge recharge = (container, containerSlot, itemSlot, isSelected) -> {
                if (isSelected || !needsSelected) {
                    int toDrain = 300;
                    if (stack.getItemDamage() > 0 && container.drainAura(toDrain, true) >= toDrain) {
                        stack.setItemDamage(stack.getItemDamage() - 1);
                        container.drainAura(toDrain, false);
                        return true;
                    }
                }
                return false;
            };

            @Override
            public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
                return capability == NaturesAuraAPI.capAuraRecharge;
            }

            @Nullable
            @Override
            public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
                return capability == NaturesAuraAPI.capAuraRecharge ? (T) this.recharge : null;
            }
        };
    }

    public static IBlockState getStateFromString(String raw) {
        String[] split = raw.split("\\[");
        Block block = Block.REGISTRY.getObject(new ResourceLocation(split[0]));
        if (block != null) {
            IBlockState state = block.getDefaultState();
            if (split.length > 1) {
                for (String part : split[1].replace("]", "").split(",")) {
                    String[] keyValue = part.split("=");
                    for (IProperty<?> prop : state.getProperties().keySet()) {
                        IBlockState changed = findProperty(state, prop, keyValue[0], keyValue[1]);
                        if (changed != null) {
                            state = changed;
                            break;
                        }
                    }
                }
            }
            return state;
        } else
            return null;
    }

    private static <T extends Comparable<T>> IBlockState findProperty(IBlockState state, IProperty<T> prop, String key, String newValue) {
        if (key.equals(prop.getName()))
            for (T value : prop.getAllowedValues())
                if (prop.getName(value).equals(newValue))
                    return state.withProperty(prop, value);
        return null;
    }

    public static <T> void registerCap(Class<T> type) {
        CapabilityManager.INSTANCE.register(type, new Capability.IStorage<T>() {
            @Nullable
            @Override
            public NBTBase writeNBT(Capability capability, Object instance, EnumFacing side) {
                return null;
            }

            @Override
            public void readNBT(Capability capability, Object instance, EnumFacing side, NBTBase nbt) {

            }
        }, () -> null);
    }

    public static void addAdvancement(EntityPlayer player, ResourceLocation advancement, String criterion) {
        if (!(player instanceof EntityPlayerMP))
            return;
        EntityPlayerMP playerMp = (EntityPlayerMP) player;
        Advancement adv = playerMp.getServerWorld().getAdvancementManager().getAdvancement(advancement);
        if (adv != null)
            playerMp.getAdvancements().grantCriterion(adv, criterion);
    }

    public static int getIngredientAmount(Ingredient ingredient) {
        int highestAmount = 0;
        for (ItemStack stack : ingredient.getMatchingStacks())
            if (stack.getCount() > highestAmount)
                highestAmount = stack.getCount();
        return highestAmount;
    }

    @SideOnly(Side.CLIENT)
    public static void renderWeirdBox(double x, double y, double z, double width, double height, double depth) {
        GL11.glVertex3d(x, y + height, z);
        GL11.glVertex3d(x + width, y + height, z);
        GL11.glVertex3d(x + width, y, z);
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x + width, y, z + depth);
        GL11.glVertex3d(x + width, y, z);
        GL11.glVertex3d(x + width, y + height, z);
        GL11.glVertex3d(x + width, y + height, z + depth);
        GL11.glVertex3d(x + width, y + height, z + depth);
        GL11.glVertex3d(x, y + height, z + depth);
        GL11.glVertex3d(x, y, z + depth);
        GL11.glVertex3d(x + width, y, z + depth);
        GL11.glVertex3d(x, y + height, z + depth);
        GL11.glVertex3d(x, y + height, z);
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x, y, z + depth);
        GL11.glVertex3d(x, y + height, z);
        GL11.glVertex3d(x, y + height, z + depth);
        GL11.glVertex3d(x + width, y + height, z + depth);
        GL11.glVertex3d(x + width, y + height, z);
        GL11.glVertex3d(x + width, y, z);
        GL11.glVertex3d(x + width, y, z + depth);
        GL11.glVertex3d(x, y, z + depth);
        GL11.glVertex3d(x, y, z);
    }

    public static boolean isHoldingItem(EntityPlayer player, Item item) {
        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (!stack.isEmpty() && stack.getItem() == item)
                return true;
        }
        return false;
    }

    public static boolean isEmpty(IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++)
            if (!handler.getStackInSlot(i).isEmpty())
                return false;
        return true;
    }

    public static AxisAlignedBB aabb(Vec3d pos) {
        return new AxisAlignedBB(pos.x, pos.y, pos.z, pos.x, pos.y, pos.z);
    }
}
