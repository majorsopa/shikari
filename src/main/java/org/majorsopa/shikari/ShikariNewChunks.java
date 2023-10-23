package org.majorsopa.shikari;

import com.mojang.datafixers.util.Function3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.BindSetting;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.WorldUtils;
import org.rusherhack.core.bind.key.NullKey;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.StringSetting;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * newchunks module
 *
 * @author majorsopa
 */
public class ShikariNewChunks extends ToggleableModule {
	Thread checkThread;


	// the cached BlockPos are relative to the chunk
	ConcurrentHashMap<LevelChunk, ArrayList<BlockPos>> chunkCache = new ConcurrentHashMap<>();

	private final BooleanSetting misturnedDeepslate = new BooleanSetting("MisturnedDeepslate", "Chunks with misoriented deepslate", true);
	private final ColorSetting misturnedDeepslateChunkColor = new ColorSetting("ChunkColor", Color.RED)
			.setAlphaAllowed(true)
			.setThemeSyncAllowed(false)
			.setRainbowAllowed(false);
	private final ColorSetting misturnedDeepslateBlockColor = new ColorSetting("BlockColor", Color.ORANGE)
			.setAlphaAllowed(true)
			.setThemeSyncAllowed(false)
			.setRainbowAllowed(false);
	private final NumberSetting<Double> misturnedDeepslateRenderY = new NumberSetting<>("RenderY", 0.0, -64.0, 320.0)
			.incremental(1.0);
	private final NumberSetting<Integer> deepslateMaxCheckY = new NumberSetting<>("MaxCheckY", 10, -64, 320)
			.incremental(1);
	private final NumberSetting<Integer> deepslateMinCheckY = new NumberSetting<>("MinCheckY", -50, -64, 320)
			.incremental(1);
	private final NumberSetting<Integer> deepslateMaxDistance = new NumberSetting<>(
			"MaxDistance",
			"Max distance to check, in chunks (chessboard)",
			8,
			0,
			32
	)
			.incremental(1);
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
	private final NumberSetting<Double> missingBruteRenderY = new NumberSetting<>("RenderY", 0.0, -64.0, 320.0)
			.incremental(1.0);
	private final NumberSetting<Integer> missingBruteMaxDistance = new NumberSetting<>(
			"MaxDistance",
			"Max distance to check, in chunks (chessboard)",
			8,
			0,
			32
	)
			.incremental(1);


	public ShikariNewChunks() {
		super("ShikariNewChunks", "find stuff", ModuleCategory.WORLD);

		this.deepslateCheckSkeletonHead.addSubSettings(
				this.deepslateSkeletonHeadDistance
		);
		this.misturnedDeepslate.addSubSettings(
				this.misturnedDeepslateChunkColor,
				this.misturnedDeepslateBlockColor,
				this.misturnedDeepslateRenderY,
				this.deepslateMinCheckY,
				this.deepslateMaxCheckY,
				this.deepslateMaxDistance,
				this.deepslateCheckSkeletonHead
		);

		this.registerSettings(
				this.misturnedDeepslate
		);
	}

	@Subscribe
	private void onRender3D(EventRender3D event) {
		final IRenderer3D renderer = event.getRenderer();

		//begin renderer
		renderer.begin(event.getMatrixStack());

		for (Map.Entry<LevelChunk, ArrayList<BlockPos>> entry : this.chunkCache.entrySet()) {
			LevelChunk chunk = entry.getKey();
			ArrayList<BlockPos> blocks = entry.getValue();

			if (blocks.isEmpty()) {
				continue;
			}

			handleChunkRender(renderer, chunk, this.misturnedDeepslate);
			handleBlocksRender(renderer, chunk, blocks, this.misturnedDeepslate);
		}

		//end renderer
		renderer.end();
	}

	private void handleChunkRender(IRenderer3D renderer, LevelChunk chunk, @NotNull BooleanSetting check) {
		final int color = getColorFromCheck(check, "ChunkColor");

		final double y = (double) check.getSubSetting("RenderY").getValue();

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

	@SafeVarargs
	private void checkChunkForBlock(int minCheckY, int maxCheckY, int maxDistance, BooleanSetting check, Function3<LevelChunk, BlockPos, BooleanSetting, Boolean>... checkBlockPos) {
		assert mc.player != null;

		final ArrayList<LevelChunk> checkChunks = (ArrayList<LevelChunk>) WorldUtils.getChunks();
		checkChunks.removeIf(chunk -> mc.player.chunkPosition().getChessboardDistance(chunk.getPos()) > maxDistance);

		for (LevelChunk chunk : checkChunks) {
			if (this.chunkCache.containsKey(chunk)) {
				continue;
			} else {
				this.chunkCache.put(chunk, new ArrayList<>());
			}
			for (int x = 0; x < 16; x++) {
				for (int y = minCheckY; y < maxCheckY; y++) {
					for (int z = 0; z < 16; z++) {
						BlockPos relativePos = new BlockPos(x, y, z);
						for (Function3<LevelChunk, BlockPos, BooleanSetting, Boolean> blockCheck : checkBlockPos) {
							if (blockCheck.apply(chunk, relativePos, check)) {
								this.chunkCache.get(chunk).add(relativePos);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void onEnable() {
		this.checkThread = new Thread(() -> {
			if (this.misturnedDeepslate.getValue()) {
				int maxCheckY = ((NumberSetting<Integer>)this.misturnedDeepslate.getSubSetting("MaxCheckY")).getValue();
				int minCheckY = ((NumberSetting<Integer>)this.misturnedDeepslate.getSubSetting("MinCheckY")).getValue();
				int maxDistance = ((NumberSetting<Integer>)this.misturnedDeepslate.getSubSetting("MaxDistance")).getValue();
				if (minCheckY <= maxCheckY) {

					checkChunkForBlock(
							minCheckY,
							maxCheckY,
							maxDistance,
							this.misturnedDeepslate,
							BlockChecks::isMisturnedDeepslate
					);
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
}

class BlockChecks {
	static boolean isMisturnedDeepslate(LevelChunk chunk, BlockPos blockPos, BooleanSetting check) {
		boolean misturned = chunk.getBlockState(blockPos).getBlock() == Blocks.DEEPSLATE && chunk.getBlockState(blockPos) != chunk.getBlockState(blockPos).getBlock().defaultBlockState();
		assert Minecraft.getInstance().level != null;
		// search nearby blocks for skeleton head, avoiding false positives from deep darks
		BooleanSetting checkSkeletonHead = (BooleanSetting) check.getSubSetting("CheckSkeletonHead");
		if (misturned && checkSkeletonHead.getValue()) {
			int checkDistance = ((NumberSetting<Integer>)checkSkeletonHead.getSubSetting("SkeletonHeadDistance")).getValue();
			// todo use getBlockStates method
			for (int x = -checkDistance; x <= checkDistance; x++) {
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
		return misturned;
	}
}
