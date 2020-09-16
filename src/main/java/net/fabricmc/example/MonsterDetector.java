package net.fabricmc.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Maps;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.light.ChunkLightingView;

public class MonsterDetector implements ClientModInitializer {
    private static final String KEYBIND_CATEGORY = "key.lightoverlay.category";
	private static final Identifier ENABLE_OVERLAY_KEYBIND = new Identifier("lightoverlay", "enable_overlay");
	private static final KeyBinding ENABLE_OVERLAY = createKeyBinding(ENABLE_OVERLAY_KEYBIND, InputUtil.Type.KEYSYM, 296, KEYBIND_CATEGORY);

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
	private static ChunkPos[] chunkList;
	private static ArrayList<ChunkPos> chunkQueue;
	private static final Map<Long, ArrayList<Long>> CHUNK_MAP = Maps.newConcurrentMap();
	private static final Map<Long, Integer> HeightMap = new HashMap<>();

	private static void setChunkList() {
		chunkQueue = new ArrayList<>();
		int dist = 0;
		int maxDist = 8;
		chunkList = new ChunkPos[17*17];
		int chunkI = 0;
		while (dist <= maxDist) {
			int x = dist;
			int y = dist;
			int points;
			if(dist == 0){
				points = 1;
			} else {
				points = (dist*2+1)*(dist*2+1)-((dist-1)*2+1)*((dist-1)*2+1);
			}
			int xDir = -1;
			int yDir = 0;
			for (int i = 0; i < points; i++) {
				ChunkPos cPos = new ChunkPos(new BlockPos(((xStart>>4) + x)<<4, 70, ((zStart>>4) + y)<<4));
				long lcPos = cPos.toLong();
				Chunk chunk = world.getChunk(new BlockPos(((xStart>>4) + x)<<4, 70, ((zStart>>4) + y)<<4));
				Heightmap hm = chunk.getHeightmap(Type.WORLD_SURFACE);
				int maxH = 0;
				for (int k = 0; k < 16; k++) {
					for (int l = 0; l < 16; l++) {
						int h = hm.get(k, l);
						if(maxH < h)
							maxH = h;
					}
				}
				CHUNK_MAP.clear();
				chunkList[chunkI] = cPos;
				chunkQueue.add(cPos);
				HeightMap.put(lcPos, maxH);
				chunkI++;

				x = x+xDir;
				y = y+yDir;
				if(Math.abs(x)==dist && xDir!=0)
				{
					yDir = xDir;
					xDir = 0;
				} else if(Math.abs(y)==dist && yDir!=0) {
					xDir = -yDir;
					yDir = 0;
				}
			}
			dist++;
		}
		chunkQueue.add(chunkList[0]);
		CLIENT.player.sendMessage(new LiteralText("" + chunkI), false);
	}

	private static boolean isSpawnable(BlockPos.Mutable blockPos) {

		BlockState ogBlock = world.getBlockState(blockPos);
		BlockState upBlock = world.getBlockState(blockPos.up());
		
		boolean isMushroom = world.getBiome(blockPos).getCategory() == Biome.Category.MUSHROOM;
		boolean isSolid = ogBlock.isSolidBlock(world, blockPos);
		//!(upperCollisionShape.getMax(Direction.Axis.Y) > 0) || 
		boolean isFree = upBlock.isAir() || upBlock.getFluidState().getFluid().equals(Fluids.WATER); 
		boolean isDark = block.getLightLevel(blockPos.up()) < 8;
		return (isSolid && isFree && isDark && !isMushroom);
	}
	private static boolean initial = true;
	private static void findSpawnBlocks(){
		int nChunks2Check = 5;
		int chunksChecked = 0;
		BlockPos.Mutable bPos = new BlockPos.Mutable();
		while(chunksChecked < nChunks2Check) {
			if(chunkQueue.isEmpty()) {
				initial = false;
				return;
			}
			ChunkPos cPos = chunkQueue.remove(0);
			long lcPos = cPos.toLong();
			CHUNK_MAP.remove(lcPos);
			ArrayList<Long> bPosList = new ArrayList<>();
			for (int i = 0; i < 16; i++) {
				for (int j = 0; j < 16; j++) {
					int blockX = cPos.getStartX() + i;
					int blockZ = cPos.getStartZ() + j;
					for(int l =3; l < HeightMap.get(lcPos);l++) {
						bPos = new BlockPos.Mutable(blockX, l, blockZ);
						if(isSpawnable(bPos))
						{
							bPosList.add(bPos.asLong());
							
						}	
					}
				}
			}
			CHUNK_MAP.put(lcPos, bPosList);
			chunksChecked++;
		}
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
        GL11.glVertex3d(x + 1 - d0, y - d1, z + 1 - d2);
        GL11.glVertex3d(x + 0 - d0, y - d1, z + 1 - d2);
        GL11.glVertex3d(x + 0 - d0, y - d1, z + 0 - d2);
        GL11.glVertex3d(x + 1 - d0, y - d1, z + 0 - d2);
    }

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_WORLD_TICK.register((client) -> {
            while (ENABLE_OVERLAY.wasPressed()) {
				
				if (!active) {
					world = CLIENT.world;
					block = world.getLightingProvider().get(LightType.BLOCK);
					entityContext = ShapeContext.of(CLIENT.player);
					xStart = (int)CLIENT.player.getX();
					yStart = (int)CLIENT.player.getY();
					zStart = (int)CLIENT.player.getZ();
					initial = true;
					setChunkList();
				}
				active = !active;
			}
			if (active) {
				findSpawnBlocks();
				if (!initial) {
					BlockPos playerPos = new BlockPos(CLIENT.player.getX(), CLIENT.player.getY(), CLIENT.player.getZ());
					
					for (int i = -1; i < 1; i++) {
						for (int j = -1; j < 1; j++) {
							ChunkPos cPos = (new ChunkPos(playerPos.add(i*16, 0, j*16)));
							chunkQueue.add(cPos);
						}
					}
				}
				
			}
		});
		Hooks.DEBUG_RENDER_PRE.register(() -> {
			if (MonsterDetector.active) {	
                PlayerEntity playerEntity = CLIENT.player;
                //int playerPosX = ((int) playerEntity.getX()) >> 4;
                //int playerPosZ = ((int) playerEntity.getZ()) >> 4;
                
                World world = CLIENT.world;
				BlockPos playerPos = new BlockPos(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
				BlockPos origPos = new BlockPos(xStart,yStart,zStart);
                Camera camera = CLIENT.gameRenderer.getCamera();

				RenderSystem.disableDepthTest();
				RenderSystem.disableTexture();
				RenderSystem.enableBlend();
				RenderSystem.disableCull();
				RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
				GL11.glLineWidth(1.0f);
				GL11.glBegin(GL11.GL_QUADS);
				BlockPos.Mutable mutable = new BlockPos.Mutable();
				mutable.set(origPos);
				MonsterDetector.renderCross(camera, world, mutable, 0x00FF00, entityContext);
				for (ChunkPos chunkPos : chunkList) {
					if (CHUNK_MAP.get(chunkPos.toLong()) == null)
						continue;
					for (Long bPos : CHUNK_MAP.get(chunkPos.toLong())) {
						mutable.set(BlockPos.unpackLongX(bPos),BlockPos.unpackLongY(bPos),BlockPos.unpackLongZ(bPos));
						if(mutable.isWithinDistance(playerPos, 128))
							if(mutable.isWithinDistance(origPos, 128))
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
