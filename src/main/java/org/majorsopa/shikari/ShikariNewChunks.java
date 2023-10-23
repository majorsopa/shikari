package org.majorsopa.shikari;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Tuple;
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
import java.util.*;
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
	ConcurrentHashMap<LevelChunk, CacheElement> chunkCache = new ConcurrentHashMap<>();
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
	private final ColorSetting misturnedDeepslateChunkColor = new ColorSetting("ChunkColor", Color.RED)
			.setAlphaAllowed(true)
			.setThemeSyncAllowed(false)
			.setRainbowAllowed(false);
	private final ColorSetting misturnedDeepslateBlockColor = new ColorSetting("BlockColor", Color.ORANGE)
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
	private final BooleanSetting missingBrute = new BooleanSetting("MissingBrute", "Chunks with misoriented deepslate", true);
	private final ColorSetting missingBruteChunkColor = new ColorSetting("ChunkColor", Color.RED)
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

		for (Map.Entry<LevelChunk, CacheElement> entry : this.chunkCache.entrySet()) {
			LevelChunk chunk = entry.getKey();
			CacheElement value = entry.getValue();
			BooleanSetting check = value.getCheck();
			ArrayList<BlockPos> blocks = value.getBlocks();
			ArrayList<Integer> ids = value.getIds();

			handleChunkRender(renderer, chunk, check);
			handleBlocksRender(renderer, chunk, blocks, check);
			for (int id : ids) {
				assert Minecraft.getInstance().level != null;
				Entity entity = Minecraft.getInstance().level.getEntity(id);
				if (entity != null) {
					handleEntityRender(renderer, event, entity, check);
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

	private void handleBlocksRender(IRenderer3D renderer, LevelChunk chunk, ArrayList<BlockPos> blocks, @NotNull BooleanSetting check) {
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

	private ConcurrentHashMap<String, ArrayList<BlockPos>> checkChunkForBlocks(int minCheckY, int maxCheckY, LevelChunk chunk, ArrayList<Tuple<BooleanSetting, BiFunction<CheckableObject, BooleanSetting, Integer>>> checkBlockPos) {
		ConcurrentHashMap<String, ArrayList<BlockPos>> blocks = new ConcurrentHashMap<>();

		for (int x = 0; x < 16; x++) {
			for (int y = minCheckY; y < maxCheckY; y++) {
				for (int z = 0; z < 16; z++) {
					BlockPos relativePos = new BlockPos(x, y, z);
					CheckableObject checkableObject = new CheckableObject(chunk, relativePos);
					for (Tuple<BooleanSetting, BiFunction<CheckableObject, BooleanSetting, Integer>> checkBlockPosFunction : checkBlockPos) {
						BooleanSetting check = checkBlockPosFunction.getA();
						BiFunction<CheckableObject, BooleanSetting, Integer> checkFunction = checkBlockPosFunction.getB();
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
			assert Minecraft.getInstance().level != null;
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

			ArrayList<Tuple<BooleanSetting, BiFunction<CheckableObject, BooleanSetting, Integer>>> blockChecks = new ArrayList<>();
			if (this.misturnedDeepslate.getValue()) {
				blockChecks.add(new Tuple<>(this.misturnedDeepslate, Checks::isMisturnedDeepslate));
			}
			if (this.missingBrute.getValue()) {
				blockChecks.add(new Tuple<>(this.missingBrute, Checks::hasGildedBlackstone));
			}


			ConcurrentHashMap<LevelChunk, CacheElement> newCache = new ConcurrentHashMap<>();
			// i love inefficient code
			for (LevelChunk chunk : checkChunks) {
				ConcurrentHashMap<String, ArrayList<BlockPos>> checkedChunk = this.checkChunkForBlocks(minCheckY, maxCheckY, chunk, blockChecks);
				for (Map.Entry<String, ArrayList<BlockPos>> entry : checkedChunk.entrySet()) {
					if (!newCache.containsKey(chunk)) {
						newCache.put(chunk, new CacheElement((BooleanSetting) this.getSetting(entry.getKey())));
					}
					CacheElement cacheElement = newCache.get(chunk);
					cacheElement.addBlockPoss(entry.getValue());
				}
			}


			//ArrayList<Tuple<BooleanSetting, BiFunction<CheckableObject, BooleanSetting, Integer>>> entityChecks = new ArrayList<>();


			for (Map.Entry<LevelChunk, CacheElement> entry : newCache.entrySet()) {
				LevelChunk chunk = entry.getKey();
				CacheElement cacheElement = entry.getValue();
				if (!this.chunkCache.containsKey(chunk)) {
					this.chunkCache.put(chunk, cacheElement);
				} else {
					this.chunkCache.get(chunk).addAll(cacheElement);
				}
			}
		});
		this.checkThread.start();
	}
	
	@Override
	public void onDisable() {
		this.checkThread.interrupt();
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

	static int checkChunkForEntity(CheckableObject checkableObject, BooleanSetting check) {
		int found = 0;

		ArrayList<Entity> entities = (ArrayList<Entity>) WorldUtils.getEntities();
		entities.removeIf(entity -> entity.getType() != checkableObject.getEntityType());

		for (Entity entity : entities) {
			if (entity.chunkPosition().equals(checkableObject.getChunk().getPos())) {
				found++;
			}
		}

		return found;
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
	private BooleanSetting check;
	private ArrayList<BlockPos> blocks;
	private ArrayList<Integer> ids;

	public CacheElement(BooleanSetting check) {
		this.check = check;
		this.blocks = new ArrayList<>();
		this.ids = new ArrayList<>();
	}

	public BooleanSetting getCheck() {
		return this.check;
	}

	public ArrayList<BlockPos> getBlocks() {
		return this.blocks;
	}

	public ArrayList<Integer> getIds() {
		return this.ids;
	}

	public void addBlockPoss(ArrayList<BlockPos> blockPos) {
		this.blocks.addAll(blockPos);
	}

	public void addIds(ArrayList<Integer> ids) {
		this.ids.addAll(ids);
	}

	public void addAll(CacheElement other) {
		this.addBlockPoss(other.getBlocks());
		this.addIds(other.getIds());
	}
}
