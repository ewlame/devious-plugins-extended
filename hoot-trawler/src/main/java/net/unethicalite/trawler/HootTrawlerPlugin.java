package net.unethicalite.trawler;

import com.google.inject.Provides;
import net.runelite.api.widgets.WidgetInfo;
import net.unethicalite.api.entities.NPCs;
import net.unethicalite.api.entities.Players;
import net.unethicalite.api.entities.TileObjects;
import net.unethicalite.api.items.DepositBox;
import net.unethicalite.api.items.Inventory;
import net.unethicalite.api.movement.Movement;
import net.unethicalite.api.plugins.LoopedPlugin;
import net.unethicalite.api.scene.Tiles;
import net.unethicalite.api.widgets.Dialog;
import net.unethicalite.api.widgets.Widgets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Locatable;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.unethicalite.api.commons.Time;
import net.unethicalite.api.entities.NPCs;
import net.unethicalite.api.entities.Players;
import net.unethicalite.api.entities.TileObjects;
import net.unethicalite.api.events.PacketSent;
import net.unethicalite.api.packets.Packets;
import net.unethicalite.api.packets.WidgetPackets;
import net.unethicalite.api.plugins.LoopedPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginDescriptor;
import net.unethicalite.api.query.widgets.WidgetQuery;
import net.unethicalite.api.widgets.Dialog;
import net.unethicalite.api.widgets.Widgets;
import org.pf4j.Extension;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginDescriptor;
import net.unethicalite.api.items.DepositBox;
import javax.inject.Inject;

@PluginDescriptor(name = "Hoot Trawler", enabledByDefault = false)
@Extension
@Slf4j
public class HootTrawlerPlugin extends LoopedPlugin
{
	@Inject
	private Client client;
	private final WorldArea lobby = new WorldArea(2669, 3165, 5, 16, 1);

	private final WorldArea rewardZone = new WorldArea(2664,3160,4,8,0);
	private final WorldPoint entrancePoint = new WorldPoint(2675, 3170, 0);

	private final WorldArea boatBottom = new WorldArea(1881, 4823, 18, 5, 0);
	private final WorldArea boatBottom1 = new WorldArea(2011, 4823, 18, 5, 0);

	private final WorldArea boatDeck = new WorldArea(1881, 4823, 18, 5, 1);
	private final WorldArea boatDeck1 = new WorldArea(2011, 4823, 18, 5, 1);

	private final WorldPoint boatLadderPoint = new WorldPoint(1884, 4826, 0);
	private final WorldPoint boatLadderPoint1 = new WorldPoint(2012, 4826, 0);

	//onChatMessage for empty net?
	@Override
	protected int loop()
	{
		Player player = client.getLocalPlayer();
		Player local = Players.getLocal();
		Widget bankAllWidget;
		boolean finished = false;

		if(player.isMoving()){
			log.info("player is moving, pls check back later");
			Time.sleepTick();
			return -1;
		}
		//Widget deposit = Widgets.get(192,4);
		//deposit.interact("Deposit inventory");
		TileObject rewardNet = TileObjects.getNearest(x -> x.hasAction("Inspect"));
		if(rewardNet!=null)
			finished = rewardZone.contains(rewardNet);
		if(finished){
			if(!player.isMoving()){
				bankAllWidget = Widgets.get(367,19);
				if(bankAllWidget==null) {
					rewardNet.interact("Inspect");
					log.info("inspecting");
					if(Dialog.isOpen() || player.isAnimating()){
						log.info("no rewards available");
						TileObject gangplank = TileObjects.getFirstAt(entrancePoint, x -> x.hasAction("Cross"));
						if (gangplank != null){
							gangplank.interact("Cross");
							if (lobby.contains(local))
							{
								log.info("Waiting for game start");
								return -1;
							}

							log.error("Start plugin near fishing trawler");
							return -1;
						}
						return -1;

					}
				}
				//Time.sleepTicks(15);
				if(bankAllWidget!=null){
					log.info("widget: " + bankAllWidget.getName());
					log.info("bank all screen visible");
					bankAllWidget.interact("Bank-all");
					System.out.println("Widget Actions: " + Arrays.toString(bankAllWidget.getActions()));
					//bankAllWidget.interact(Arrays.toString(bankAllWidget.getActions()));
					//WidgetPackets.widgetAction(bankAllWidget,"Bank-all");
					log.info("Banked");
					return -1;
				}
				return -1;
			}
			System.out.println("here");
			//return to reward zone?
			return -1;
		}


		if (boatDeck.contains(local) || boatDeck1.contains(local))
		{

			NPC tentacle = NPCs.getNearest(x -> x.hasAction("Chop") && x.getAnimation() == 8953);
			var plank = TileObjects.getNearest(x->x.hasAction("Fix"));
			if(plank!= null){
				plank.interact("Fix");
				log.info("fixing plank");
				return -1;
			}
			if (tentacle != null)
			{
				tentacle.interact("Chop");
				return -1;
			}
			log.info("Waiting for tentacle to spawn");
			return -1;
		}

		if (boatBottom.contains(local) || boatBottom1.contains(local))
		{
			TileObject ladder = TileObjects.getFirstAt(boatLadderPoint, x -> x.hasAction("Climb-up"));
			TileObject ladder1 = TileObjects.getFirstAt(boatLadderPoint1, x -> x.hasAction("Climb-up"));
			if (ladder != null)
			{
				ladder.interact("Climb-up");
				return -1;
			}

			if (ladder1 != null)
			{
				ladder1.interact("Climb-up");
				return -1;
			}

			return -1;
		}
		return 1000;
	}

	@Provides
	public HootTrawlerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HootTrawlerConfig.class);
	}
}
