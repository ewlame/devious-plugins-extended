package net.unethicalite.plugins.chopper;

import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ConfigButtonClicked;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.ItemID;
import net.unethicalite.api.entities.Players;
import net.unethicalite.api.entities.TileObjects;
import net.unethicalite.api.items.Inventory;
import net.unethicalite.api.movement.Movement;
import net.unethicalite.api.movement.Reachable;
import net.unethicalite.api.movement.pathfinder.GlobalCollisionMap;
import net.unethicalite.api.plugins.LoopedPlugin;
import net.unethicalite.api.scene.Tiles;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Extension
@PluginDescriptor(
		name = "Unethical Chopper",
		description = "Chops trees",
		enabledByDefault = false
)
@Slf4j
public class ChopperPlugin extends LoopedPlugin
{
	@Inject
	private ChopperConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChopperOverlay chopperOverlay;

	@Inject
	private GlobalCollisionMap collisionMap;

	private int fmCooldown = 0;

	@Getter(AccessLevel.PROTECTED)
	private List<Tile> fireArea;

	private WorldPoint startLocation = null;

	@Getter(AccessLevel.PROTECTED)
	private boolean scriptStarted;

	@Override
	protected void startUp()
	{
		overlayManager.add(chopperOverlay);
	}

	@Override
	public void stop()
	{
		super.stop();
		overlayManager.remove(chopperOverlay);
	}

	@Subscribe
	public void onConfigButtonPressed(ConfigButtonClicked event)
	{
		if (!event.getGroup().contains("unethical-chopper") || !event.getKey().toLowerCase().contains("start"))
		{
			return;
		}

		if (scriptStarted)
		{
			scriptStarted = false;
		}
		else
		{
			var local = Players.getLocal();
			if (local == null)
			{
				return;
			}
			startLocation = local.getWorldLocation();
			fireArea = generateFireArea(3);
			this.scriptStarted = true;
			log.info("Script started");
		}
	}

	protected boolean isEmptyTile(Tile tile)
	{
		return tile != null
				&& TileObjects.getFirstAt(tile, a -> a instanceof GameObject) == null
				&& !collisionMap.fullBlock(tile.getWorldLocation());
	}

	@Override
	protected int loop()
	{
		if (fmCooldown > 0 || !scriptStarted)
		{
			return -1;
		}

		var local = Players.getLocal();
		if (local == null)
		{
			return -1;
		}

		var tree = TileObjects
				.getSurrounding(startLocation, 8, config.tree().getNames())
				.stream()
				.min(Comparator.comparing(x -> x.distanceTo(local.getWorldLocation())))
				.orElse(null);

		var logs = Inventory.getFirst(ItemID.MAGIC_LOGS);
		if (config.makeFire())
		{
			var bankersnote = Inventory.getFirst("Banker's note");
			if (logs != null && bankersnote != null)
			{
				var emptyTile = fireArea == null || fireArea.isEmpty() ? null : fireArea.stream()
						.filter(t ->
						{
							Tile tile = Tiles.getAt(t.getWorldLocation());
							return tile != null && isEmptyTile(tile);
						})
						.min(Comparator.comparingInt(wp -> wp.distanceTo(local)))
						.orElse(null);

				if (fireArea.isEmpty() || emptyTile == null)
				{
					fireArea = generateFireArea(3);
					log.debug("Generating fire area");
					return 1000;
				}

				if (emptyTile != null)
				{
					if (!emptyTile.getWorldLocation().equals(local.getWorldLocation()))
					{
						if (local.isMoving())
						{
							return 333;
						}

						Movement.walk(emptyTile);
						return 1000;
					}

					if (local.isAnimating())
					{
						return 333;
					}

					fmCooldown = 4;
					logs.useOn(bankersnote);
					return 500;
				}
			}
		}
		else
		{
			if (logs != null && !local.isAnimating())
			{
				logs.drop();
				return 500;
			}
		}

		if (local.isMoving() || local.isAnimating())
		{
			return 333;
		}

		if (tree == null)
		{
			log.debug("Could not find any trees");
			return 1000;
		}

		tree.interact("Chop down");
		return 1000;
	}

	@Subscribe
	private void onGameTick(GameTick e)
	{
		if (fmCooldown > 0)
		{
			fmCooldown--;
		}
	}

	@Provides
	ChopperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChopperConfig.class);
	}

	private List<Tile> generateFireArea(int radius)
	{
		return Tiles.getSurrounding(Players.getLocal().getWorldLocation(), radius).stream()
				.filter(tile -> tile != null
						&& isEmptyTile(tile)
						&& Reachable.isWalkable(tile.getWorldLocation()))
				.collect(Collectors.toUnmodifiableList());
	}
}