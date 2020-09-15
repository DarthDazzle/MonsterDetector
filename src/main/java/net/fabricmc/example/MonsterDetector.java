package net.fabricmc.example;

import java.util.ArrayList;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.light.ChunkLightingView;

public class MonsterDetector implements ClientModInitializer {
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
	private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
	private static final KeyBinding ENABLE_OVERLAY = createKeyBinding(ENABLE_OVERLAY_KEYBIND, InputUtil.Type.KEYSYM, 296, KEYBIND_CATEGORY);
	private static final Identifier UPDATE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "update_render");
	private static final KeyBinding UPDATE_OVERLAY = createKeyBinding(UPDATE_OVERLAY_KEYBIND, InputUtil.Type.KEYSYM, 295, KEYBIND_CATEGORY);

    private static KeyBinding createKeyBinding(Identifier id, InputUtil.Type type, int code, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key." + id.getNamespace() + "." + id.getPath(), type, code, category));
	}
	private static boolean active = false;
	private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
	private static ClientWorld world;
	private static ChunkLightingView block;
	private static ShapeContext entityContext;

	static int xStart; 
	static int yStart;
	static int zStart;
	private static ArrayList<Long> listofBlocks;

	private static boolean isSpawnable(BlockPos.Mutable blockPos) {

		BlockState ogBlock = world.getBlockState(blockPos);
		BlockState upBlock = world.getBlockState(blockPos.up());
		VoxelShape upperCollisionShape = upBlock.getCollisionShape(world, blockPos.up(), entityContext);
		
		boolean isMushroom = world.getBiome(blockPos).getCategory() == Biome.Category.MUSHROOM;
		boolean isSolid = ogBlock.isSolidBlock(world, blockPos);
		boolean isFree = !(upperCollisionShape.getMax(Direction.Axis.Y) > 0) || upBlock.isAir() || upBlock.getFluidState().getFluid().equals(Fluids.WATER); 
		boolean isDark = block.getLightLevel(blockPos.up()) < 8;
		return (isSolid && isFree && isDark && !isMushroom);
	}

	private static ArrayList<Long> findSpawnBlocks(){
		ArrayList<Long> list = new ArrayList<Long>();
		int spawnRange = 128;
		int distance = 0;

		BlockPos.Mutable blockPos = new BlockPos.Mutable();
		while (distance <= spawnRange) {
			

			for (int xAdd = -distance; xAdd <= distance; xAdd++) {
				for (int yAdd = -(distance-Math.abs(xAdd)); yAdd <= (distance - Math.abs(xAdd)); yAdd++) {
					int zAdd = distance - Math.abs(xAdd) - Math.abs(yAdd);
					blockPos.set(xAdd+xStart,yAdd+yStart,zAdd+zStart);
					if (isSpawnable(blockPos)) {
						list.add(blockPos.asLong());						
					}
					if (zAdd != 0) {
						blockPos.set(xAdd+xStart,yAdd+yStart,-zAdd+zStart);
						if (isSpawnable(blockPos)) {
							list.add(blockPos.asLong());
						}
					}
					
				}
				
			}
			distance++;
		}

		return list;
	}

    public static void renderCross(Camera camera, World world, BlockPos pos, int color, ShapeContext entityContext) {
        double d0 = camera.getPos().x;
        double d1 = camera.getPos().y - .005D;
        VoxelShape upperOutlineShape = world.getBlockState(pos).getOutlineShape(world, pos, entityContext);
        if (!upperOutlineShape.isEmpty())
            d1 -= upperOutlineShape.getMax(Direction.Axis.Y);
        double d2 = camera.getPos().z;
        
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        RenderSystem.color4f(red / 255f, green / 255f, blue / 255f, 1f);
        GL11.glVertex3d(x + .01 - d0, y - d1, z + .01 - d2);
        GL11.glVertex3d(x - .01 + 1 - d0, y - d1, z - .01 + 1 - d2);
        GL11.glVertex3d(x - .01 + 1 - d0, y - d1, z + .01 - d2);
        GL11.glVertex3d(x + .01 - d0, y - d1, z - .01 + 1 - d2);
    }

	@Override
	public void onInitializeClient() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            while (ENABLE_OVERLAY.wasPressed()) {
				
				if (!active) {
					world = CLIENT.world;
					block = world.getLightingProvider().get(LightType.BLOCK);
					entityContext = ShapeContext.of(CLIENT.player);
					xStart = (int)client.player.getX();
					yStart = (int)client.player.getY();
					zStart = (int)client.player.getZ();
					String str = "Active: " + active;
					CLIENT.player.sendMessage(new LiteralText(str), false);
					listofBlocks = findSpawnBlocks();
				}
				active = !active;
			}
			while (UPDATE_OVERLAY.wasPressed()) {
				listofBlocks = findSpawnBlocks();
			}
		});
		Hooks.DEBUG_RENDER_PRE.register(() -> {
			if (MonsterDetector.active) {	
				
                PlayerEntity playerEntity = CLIENT.player;
                int playerPosX = ((int) playerEntity.getX()) >> 4;
                int playerPosZ = ((int) playerEntity.getZ()) >> 4;
                
                World world = CLIENT.world;
                BlockPos playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
                Camera camera = CLIENT.gameRenderer.getCamera();

				RenderSystem.disableDepthTest();
				RenderSystem.disableTexture();
				RenderSystem.enableBlend();
				RenderSystem.disableCull();
				RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
				GL11.glLineWidth(1.0f);
				GL11.glBegin(GL11.GL_QUADS);
				BlockPos.Mutable mutable = new BlockPos.Mutable();
				for (Long pos : listofBlocks) {
					mutable.set(BlockPos.unpackLongX(pos),BlockPos.unpackLongY(pos),BlockPos.unpackLongZ(pos));
					if (mutable.isWithinDistance(playerPos, 128)) {
						MonsterDetector.renderCross(camera, world, mutable, 0xFF0000, entityContext);
					}
				}
				GL11.glEnd();
				RenderSystem.disableBlend();
				RenderSystem.enableCull();
				RenderSystem.enableTexture();
			}
		});
	}
}
