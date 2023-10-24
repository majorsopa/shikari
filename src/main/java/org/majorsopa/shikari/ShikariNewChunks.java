package org.majorsopa.shikari;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.WorldUtils;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

// todo make static string constants for stuff like ChunkColor
/**
 * newchunks module
 *
 * @author majorsopa
 */
public class ShikariNewChunks extends ToggleableModule {
	Thread checkThread;


	// the cached BlockPos are relative to the chunk
	ConcurrentHashMap<LevelChunk, ConcurrentHashMap<BooleanSetting, CacheElement>> chunkCache = new ConcurrentHashMap<>();
	private final NumberSetting<Integer> maxDistance = new NumberSetting<>(
			"MaxDistance",
			"Max distance to check, in chunks (chessboard)",
			8,
			0,
			32
	)
			.incremental(1);
	private final NumberSetting<Double> renderY = new NumberSetting<>("RenderY", 0.0, -64.0, 320.0)
			.incremental(1.0);
	private final BooleanSetting misturnedDeepslate = new BooleanSetting("MisturnedDeepslate", "Chunks with misoriented deepslate", true);
	private final ColorSetting misturnedDeepslateChunkColor = new ColorSetting("ChunkColor", new Color(255, 0, 0, 30))
			.setAlphaAllowed(true)
			.setThemeSyncAllowed(false)
			.setRainbowAllowed(false);
	private final ColorSetting misturnedDeepslateBlockColor = new ColorSetting("BlockColor", new Color(255, 180, 0, 30))
			.setAlphaAllowed(true)
			.setThemeSyncAllowed(false)
			.setRainbowAllowed(false);
	private final BooleanSetting deepslateCheckSkeletonHead = new BooleanSetting("CheckSkeletonHead", "Check for nearby skeleton head", true);
	private final NumberSetting<Integer> deepslateSkeletonHeadDistance = new NumberSetting<>(
			"SkeletonHeadDistance",
			"Distance to check for skeleton head, in blocks (chessboard)",
			10,
			1,
			32
	)
			.incremental(1);
	private final BooleanSetting missingBrute = new BooleanSetting("MissingBrute", "Chunks with misoriented deepslate", true);  // brute check distance is hardcoded 1 chunks for now
	private final ColorSetting missingBruteChunkColor = new ColorSetting("ChunkColor", new Color(255, 200, 0, 30))
			.setAlphaAllowed(true)
			.setThemeSyncAllowed(false)
			.setRainbowAllowed(false);


	public ShikariNewChunks() {
		super("ShikariNewChunks", "find stuff", ModuleCategory.WORLD);

		this.deepslateCheckSkeletonHead.addSubSettings(
				this.deepslateSkeletonHeadDistance
		);
		this.misturnedDeepslate.addSubSettings(
				this.misturnedDeepslateChunkColor,
				this.misturnedDeepslateBlockColor,
				this.deepslateCheckSkeletonHead
		);

		this.missingBrute.addSubSettings(
				this.missingBruteChunkColor
		);


		this.registerSettings(
				this.renderY,
				this.maxDistance,
				this.misturnedDeepslate,
				this.missingBrute
		);
	}

	@Subscribe
	private void onRender3D(EventRender3D event) {
		final IRenderer3D renderer = event.getRenderer();

		//begin renderer
		renderer.begin(event.getMatrixStack());

		for (Map.Entry<LevelChunk, ConcurrentHashMap<BooleanSetting, CacheElement>> entry : this.chunkCache.entrySet()) {
			LevelChunk chunk = entry.getKey();
			ConcurrentHashMap<BooleanSetting, CacheElement> values = entry.getValue();
			for (Map.Entry<BooleanSetting, CacheElement> cacheElementSet : values.entrySet()) {
				BooleanSetting check = cacheElementSet.getKey();
				CacheElement cacheElement = cacheElementSet.getValue();
				List<BlockPos> blocks = cacheElement.getBlocks();
				List<Integer> ids = cacheElement.getIds();

				handleChunkRender(renderer, chunk, check);
				if (!blocks.isEmpty()) {
					handleBlocksRender(renderer, chunk, blocks, check);
				}
				for (int id : ids) {
					assert Minecraft.getInstance().level != null;
					Entity entity = Minecraft.getInstance().level.getEntity(id);
					if (entity != null) {
						handleEntityRender(renderer, event, entity, check);
					}
				}
			}
		}

		//end renderer
		renderer.end();
	}

	private void handleChunkRender(IRenderer3D renderer, LevelChunk chunk, @NotNull BooleanSetting check) {
		final int color = getColorFromCheck(check, "ChunkColor");

		final double y = this.renderY.getValue();

		renderChunk(renderer, chunk, y, true, true, color);
	}

	private void handleBlocksRender(IRenderer3D renderer, LevelChunk chunk, List<BlockPos> blocks, @NotNull BooleanSetting check) {
		final int color = getColorFromCheck(check, "BlockColor");
		ChunkPos chunkOffset = chunk.getPos();
		for (BlockPos pos : blocks) {
			renderer.drawBox(pos.offset(chunkOffset.x * 16, 0, chunkOffset.z * 16),true, true, color);
		}
	}

	private void handleEntityRender(IRenderer3D renderer, EventRender3D event, Entity entity, @NotNull BooleanSetting check) {
		final int color = getColorFromCheck(check, "EntityColor");
		renderer.drawBox(entity, event.getPartialTicks(), true, true, color);
	}

	private int getColorFromCheck(BooleanSetting check, String subSettingName) {
		final ColorSetting colorSetting = ((ColorSetting) check.getSubSetting(subSettingName));
		return ColorUtils.transparency(colorSetting.getValueRGB(), colorSetting.getAlpha() / 255f);
	}

	private void renderChunk(@NotNull IRenderer3D renderer, @NotNull LevelChunk chunk, double y, boolean fill, boolean outline, int color) {
		renderer.drawPlane(
				chunk.getPos().x * 16,
				y,
				chunk.getPos().z * 16,
				16.0,
				16.0,
				Direction.UP,
				fill,
				outline,
				color
		);
	}

	private ConcurrentHashMap<String, ArrayList<BlockPos>> checkChunkForBlocks(int minCheckY, int maxCheckY, LevelChunk chunk, ConcurrentHashMap<BooleanSetting, BiFunction<CheckableObject, BooleanSetting, Integer>> checkBlockPos) {
		ConcurrentHashMap<String, ArrayList<BlockPos>> blocks = new ConcurrentHashMap<>();

		for (int x = 0; x < 16; x++) {
			for (int y = minCheckY; y < maxCheckY; y++) {
				for (int z = 0; z < 16; z++) {
					BlockPos relativePos = new BlockPos(x, y, z);
					CheckableObject checkableObject = new CheckableObject(chunk, relativePos);
					for (Map.Entry<BooleanSetting, BiFunction<CheckableObject, BooleanSetting, Integer>> checkBlockPosFunction : checkBlockPos.entrySet()) {
						BooleanSetting check = checkBlockPosFunction.getKey();
						BiFunction<CheckableObject, BooleanSetting, Integer> checkFunction = checkBlockPosFunction.getValue();
						int checkResult = checkFunction.apply(checkableObject, check);
						if (checkResult != 0) {
							if (!blocks.containsKey(check.getName())) {
								blocks.put(check.getName(), new ArrayList<>());
							}
							blocks.get(check.getName()).add(relativePos);
						}
					}
				}
			}
		}

		return blocks;
	}

	@Override
	public void onEnable() {
		this.checkThread = new Thread(() -> {
			while (true) {
				{
					{
						if (!this.isToggled()) {
							break;
						}
						if (Minecraft.getInstance().level == null) {
							try {
								Thread.sleep(500);  // buh
							} catch (InterruptedException e) {
								throw new RuntimeException(e);
							}
							continue;
						}
					}

					final boolean inOverworld = Minecraft.getInstance().level.dimension().equals(Level.OVERWORLD);
					final int minCheckY;
					final int maxCheckY;
					if (inOverworld) {
						minCheckY = -64;
						maxCheckY = 319;
					} else {
						minCheckY = 0;
						maxCheckY = 127;
					}

					int maxDistance = this.maxDistance.getValue();
					final ArrayList<LevelChunk> checkChunks = getChunksInRange(maxDistance);

					ConcurrentHashMap<BooleanSetting, BiFunction<CheckableObject, BooleanSetting, Integer>> blockChecks = new ConcurrentHashMap<>();
					if (this.misturnedDeepslate.getValue()) {
						blockChecks.put(this.misturnedDeepslate, Checks::isMisturnedDeepslate);
					}
					if (this.missingBrute.getValue()) {
						blockChecks.put(this.missingBrute, Checks::hasGildedBlackstone);
					}


					ConcurrentHashMap<LevelChunk, ConcurrentHashMap<BooleanSetting, CacheElement>> newCache = new ConcurrentHashMap<>();
					// i love inefficient code
					for (LevelChunk chunk : checkChunks) {
						ConcurrentHashMap<String, ArrayList<BlockPos>> checkedChunk = this.checkChunkForBlocks(minCheckY, maxCheckY, chunk, blockChecks);

						ArrayList<BlockPos> glidedBlackstones = checkedChunk.remove(this.missingBrute.getName());
						if (glidedBlackstones != null) {
							ArrayList<LevelChunk> surroundingChunks = getChunksAroundChunk(2, chunk);  // 2 distance = 5x5 area
							if (surroundingChunks.size() == 25) {  // only bother rendering if you can see all the chunks. maybe make this a setting?
								boolean foundBrute = false;
								for (LevelChunk nearbyChunk : surroundingChunks) {
									if (checkChunkForEntity(nearbyChunk, EntityType.PIGLIN_BRUTE) != 0) {
										foundBrute = true;
									}
								}
								if (!foundBrute) {
									for (LevelChunk nearbyChunk : surroundingChunks) {
										if (!newCache.containsKey(nearbyChunk)) {
											newCache.put(nearbyChunk, new ConcurrentHashMap<>());
										}
										ConcurrentHashMap<BooleanSetting, CacheElement> cacheMap = newCache.get(nearbyChunk);
										if (!cacheMap.containsKey(this.missingBrute)) {
											cacheMap.put(this.missingBrute, new CacheElement());
										}
									}
								}
							}
						}

						for (Map.Entry<String, ArrayList<BlockPos>> entry : checkedChunk.entrySet()) {
							if (!newCache.containsKey(chunk)) {
								newCache.put(chunk, new ConcurrentHashMap<>());
							}
							ConcurrentHashMap<BooleanSetting, CacheElement> chunkEntry = newCache.get(chunk);
							if (!chunkEntry.containsKey((BooleanSetting) this.getSetting(entry.getKey()))) {
								chunkEntry.put((BooleanSetting) this.getSetting(entry.getKey()), new CacheElement());
							}
							chunkEntry.get((BooleanSetting) this.getSetting(entry.getKey())).addBlockPossNoDupe(entry.getValue());
						}
					}


					//ArrayList<Tuple<BooleanSetting, BiFunction<CheckableObject, BooleanSetting, Integer>>> entityChecks = new ArrayList<>();


					for (Map.Entry<LevelChunk, ConcurrentHashMap<BooleanSetting, CacheElement>> entry : newCache.entrySet()) {
						LevelChunk chunk = entry.getKey();
						for (Map.Entry<BooleanSetting, CacheElement> cacheElement : entry.getValue().entrySet()) {
							if (!this.chunkCache.containsKey(chunk)) {
								this.chunkCache.put(chunk, new ConcurrentHashMap<>());
							}
							ConcurrentHashMap<BooleanSetting, CacheElement> chunkEntry = this.chunkCache.get(chunk);
							if (!chunkEntry.containsKey(cacheElement.getKey())) {
								chunkEntry.put(cacheElement.getKey(), new CacheElement());
							}
							chunkEntry.get(cacheElement.getKey()).addAllNoDupe(cacheElement.getValue());
						}
					}
				}
			}
		});
		this.checkThread.start();
	}
	
	@Override
	public void onDisable() {
		//this.checkThread.interrupt();
		try {
			this.checkThread.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		this.chunkCache.clear();
	}

	private static ArrayList<LevelChunk> getChunksInRange(int maxDistance) {
		final ArrayList<LevelChunk> checkChunks = (ArrayList<LevelChunk>) WorldUtils.getChunks();
		checkChunks.removeIf(chunk -> {
			assert mc.player != null;
			return mc.player.chunkPosition().getChessboardDistance(chunk.getPos()) > maxDistance;
		});
		return checkChunks;
	}

	private static ArrayList<LevelChunk> getChunksAroundChunk(int maxDistance, LevelChunk chunk) {  // checks chunks around a chunk, distance is chessboard distance
		final ArrayList<LevelChunk> checkChunks = (ArrayList<LevelChunk>) WorldUtils.getChunks();
		checkChunks.removeIf(chunk1 -> chunk.getPos().getChessboardDistance(chunk1.getPos()) > maxDistance);
		return checkChunks;
	}

	private static int checkChunkForEntity(LevelChunk chunk, EntityType<? extends Entity> entityType) {
		int found = 0;

		ArrayList<Entity> entities = new ArrayList<>();
		for (Entity entity : WorldUtils.getEntities()) {
			if (entity.getType() == entityType) {
				entities.add(entity);
			}
		}

		for (Entity entity : entities) {
			if (entity.chunkPosition().equals(chunk.getPos())) {
				found++;
			}
		}

		return found;
	}
}

class Checks {
	// todo think of a better way to pass the check but only sometimes
	static int isMisturnedDeepslate(CheckableObject checkableObject, BooleanSetting check) {
		LevelChunk chunk = checkableObject.getChunk();
		BlockPos blockPos = checkableObject.getBlockPos();

		boolean misturned = chunk.getBlockState(blockPos).getBlock() == Blocks.DEEPSLATE && chunk.getBlockState(blockPos) != chunk.getBlockState(blockPos).getBlock().defaultBlockState();
		assert Minecraft.getInstance().level != null;
		// search nearby blocks for skeleton head, avoiding false positives from deep darks
		BooleanSetting checkSkeletonHead = (BooleanSetting) check.getSubSetting("CheckSkeletonHead");
		if (misturned && checkSkeletonHead.getValue()) {
			int checkDistance = ((NumberSetting<Integer>)checkSkeletonHead.getSubSetting("SkeletonHeadDistance")).getValue();
			// todo use getBlockStates method
			for (int x = -checkDistance; x <= checkDistance; x++) {
				// todo see how low it's possible to start checking, also maybe stop looking if there is more misturned deepslate close to the original block
				for (int y = -checkDistance; y <= 0; y++) {  // skeleton heads are always lower
					for (int z = -checkDistance; z <= checkDistance; z++) {
						if (Minecraft.getInstance().level.getBlockState(
								new BlockPos(
										chunk.getPos().x * 16 + blockPos.getX() + x,
										blockPos.getY() + y,
										chunk.getPos().z * 16 + blockPos.getZ() + z
								)
						).getBlock() == Blocks.SKELETON_SKULL) {
							misturned = false;
						}
					}
				}
			}
		}
		if (misturned) {
			return 1;
		} else {
			return 0;
		}
	}

	static int hasGildedBlackstone(CheckableObject checkableObject, BooleanSetting check) {
		LevelChunk chunk = checkableObject.getChunk();
		BlockPos blockPos = checkableObject.getBlockPos();

		if (chunk.getBlockState(blockPos).getBlock() == Blocks.GILDED_BLACKSTONE) {
			return 1;
		} else {
			return 0;
		}
	}
}

class CheckableObject {
	private LevelChunk chunk = null;
	private BlockPos blockPos = null;
	private EntityType<Entity> entityType = null;

	public CheckableObject(LevelChunk chunk, BlockPos blockPos) {
		//this.checkableType = CheckableType.BLOCK;
		this.chunk = chunk;
		this.blockPos = blockPos;
	}

	public CheckableObject(LevelChunk chunk, EntityType<Entity> entityType) {
		//this.checkableType = CheckableType.ENTITY;
		this.entityType = entityType;
	}

	public LevelChunk getChunk() {
		return this.chunk;
	}

	public BlockPos getBlockPos() {
		return this.blockPos;
	}

	public EntityType<Entity> getEntityType() {
		return this.entityType;
	}

}

class CacheElement {
	private CacheList<BlockPos> blocks;
	private CacheList<Integer> ids;

	public CacheElement() {
		this.blocks = new CacheList<>();
		this.ids = new CacheList<>();
	}

	public List<BlockPos> getBlocks() {
		return this.blocks.getList();
	}

	public List<Integer> getIds() {
		return this.ids.getList();
	}

	public void addBlockPoss(List<BlockPos> blockPos) {
		this.blocks.getList().addAll(blockPos);
	}

	public void addBlockPossNoDupe(List<BlockPos> blockPos) {
		for (BlockPos pos : blockPos) {
			if (!this.blocks.getList().contains(pos)) {
				this.blocks.getList().add(pos);
			}
		}
	}

	public void addIds(List<Integer> ids) {
		this.ids.getList().addAll(ids);
	}

	public void addIdsNoDupe(List<Integer> ids) {
		for (int id : ids) {
			if (!this.ids.getList().contains(id)) {
				this.ids.getList().add(id);
			}
		}
	}

	public void addAll(CacheElement other) {
		this.addBlockPoss(other.getBlocks());
		this.addIds(other.getIds());
	}

	public void addAllNoDupe(CacheElement other) {
		this.addBlockPossNoDupe(other.getBlocks());
		this.addIdsNoDupe(other.getIds());
	}

	class CacheList<T> {
		private List<T> inner;

		public CacheList() {
			this.inner = Collections.synchronizedList(new ArrayList<>());
		}

		public List<T> getList() {
			return this.inner;
		}
	}
}
