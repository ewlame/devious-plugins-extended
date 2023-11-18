package net.unethicalite.plugins.chopper;

import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.unethicalite.api.input.Keyboard;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginDescriptor;

import java.lang.reflect.Array;
import java.util.Arrays;
import net.runelite.client.ui.overlay.OverlayManager;
import net.unethicalite.api.entities.NPCs;
import net.unethicalite.api.entities.Players;
import net.unethicalite.api.entities.TileObjects;
import net.unethicalite.api.items.Bank;
import net.unethicalite.api.items.Inventory;
import net.unethicalite.api.movement.Movement;
import net.unethicalite.api.movement.Reachable;
import net.unethicalite.api.movement.pathfinder.GlobalCollisionMap;
import net.unethicalite.api.plugins.LoopedPlugin;
import net.unethicalite.api.scene.Tiles;

import net.unethicalite.api.widgets.Widgets;
import net.unethicalite.client.Static;
import org.pf4j.Extension;

import java.util.Random;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;
import static net.unethicalite.plugins.chopper.ChopperID.*;

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

	@Inject
	private Client client;
	private final List<Integer> saplingIngredients = new ArrayList<Integer>();
	private final List<GameObject> correctOrder = new ArrayList<>(0);
	private final GameObject[] saplingOrder = new GameObject[3];

	private boolean firstCheck;
	private final List<NPC> flowers = new ArrayList<>();
	private final List<NPC> activeFlowers = new ArrayList<>(2);
	private WorldPoint startLocation = null;
	Random rand = new Random();

	@Getter(AccessLevel.PROTECTED)
	private boolean scriptStarted;

	@Override
	protected void startUp()
	{
		firstCheck = true;
		flowers.clear();
		saplingIngredients.clear();
		correctOrder.clear();
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
		/*if(isRunning())
			Plugins.stopPlugin(this);*/
		else {
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
		if(event.getGroup().contains("drop")){
			config.drop();
		}
	}
	public void floweringForest( List<Player> surroundingPlayers){
		//var surroundingPlayers = Players.getAll();
		for (Player surroundingPlayer : surroundingPlayers) {
			var flower = (NPC) surroundingPlayer.getInteracting();
			if (flower != null && flower.getName().startsWith("Flowering") || flower != null && flower.getName().contains("bush")) {
				System.out.println("flowering event started");
				if (activeFlowers.size() < 2) {
					if (!activeFlowers.contains(flower) && Players.getLocal().distanceTo(flower) <= config.radius()) {
						System.out.println("adding: " + flower);
						activeFlowers.add(flower);
					}
					if (activeFlowers.contains(flower) && Players.getLocal().distanceTo(flower) <= config.radius()) {
						System.out.println("already contains: " + flower);
						return;
					}
				}
				if (activeFlowers.size() > 2 || activeFlowers.contains(null)) {
					System.out.println("size over 2: " + activeFlowers.size() + " activeFlowers.contains(null): " + activeFlowers.contains(null));
					activeFlowers.clear();
				}
				if (activeFlowers.size() == 2 && !activeFlowers.contains(null) && !Players.getLocal().isMoving()) {
					for (int i = 0; i < activeFlowers.size(); i++) {
						//System.out.println(local.getInteracting()!=activeFlower);
						activeFlowers.get(i).interact("Tend-to");
						//System.out.println(Players.getLocal().isMoving() + " : moving?");
						System.out.print("currently attempting to interact with: " + activeFlowers.get(i).getName() + "\n ");
						if (Players.getLocal().getInteracting() == activeFlowers.get(i)) {
							System.out.println("interacting w/: " + activeFlowers.get(i).getName());
							//continue;
						}
						if (activeFlowers.get(i).getName() == null) {
							System.out.println("dat shit null");
						}
						if (activeFlowers.isEmpty()) {
							System.out.println("empty");
						}
						//if (!local.isMoving() && !Dialog.isOpen() && local.getAnimation() != AnimationID.LOOKING_INTO) {
						activeFlowers.clear();
					}
					return;
				}
			}
		}

	}
	public void risingRoots(Player local){
		var greenRoots = TileObjects.getNearest(GREEN_ROOTS);
		var roots = TileObjects.getNearest(ROOTS);
		//Anima-infused Tree roots
		if (greenRoots != null && !Inventory.isFull() && greenRoots.distanceTo(local) < config.radius()) {
			greenRoots.interact("Chop down");
			//return -1;
		}
		if (greenRoots == null && roots != null && roots.distanceTo(local) < config.radius()) {
			if (!local.isAnimating() && !local.isMoving()) {
				System.out.println("Normal Roots");
				roots.interact("Chop down");
				//return -1;
			}
		}
		//drop config true - dropping in progress, need to update to drop entire inv
		if (Inventory.isFull() && config.drop()) {
			Item junk = Inventory.getFirst(item -> item.getName().contains("logs"));
			if (junk != null) {
				junk.interact("Drop");
				log.debug("Dropping junk");
			}
		}
	}

	public void bankRun(Player local){
		//banks the logs, keeps required items listed in config
		//need to add deposit from log basket as well, 2 ids for open and closed?
		if (config.bank() && Inventory.isFull() && !local.isMoving()) {
			TileObject bank = TileObjects.within(config.bankLocation().getArea().offset(2), obj -> obj.hasAction("Collect"))
					.stream()
					.min(Comparator.comparingInt(obj -> obj.distanceTo(Players.getLocal())))
					.orElse(null);
			List<Item> unneeded = Inventory.getAll(item ->
					(!Objects.equals(item.getName(), config.requiredItems()))
							&& item.getId() != ItemID.LOG_BASKET
							&& item.getId() != ItemID.LOG_BRACE);
			Widget bankPinWindow = Widgets.get(213, 1);
			var bankPinInChar = config.bankPin().toCharArray();
			if (bankPinWindow != null && parseInt(config.bankPin()) != 0) {
				for (int i = 0; i < bankPinInChar.length; i++) {
					if (Objects.equals(Widgets.get(213, i + 3).getText(), "?")) {
						//System.out.println(bankPinInChar[i]);
						Keyboard.type(bankPinInChar[i]);
						return;
					}
					//Keyboard.type(c);
					//Time.sleepTick();
				}
			}
			if (Bank.isOpen() && unneeded != null) {
				if (!unneeded.isEmpty()) {
					for (Item item : unneeded) {
						if (item != null) {
							System.out.println(item.getName());
							Bank.depositAll(item.getId());
						}
					}
				}
			}
			if (Bank.isOpen() && unneeded == null) {
				Bank.close();
			}

			Movement.walkTo(config.bankLocation());
			if (bank != null && bankPinWindow == null && !Bank.isOpen() && !local.isMoving()) {
				bank.interact("Bank", "Use");
			}
			NPC banker = NPCs.getNearest("Banker");
			if (banker != null && bankPinWindow == null && !Bank.isOpen() && !local.isMoving() && bank == null) {
				banker.interact("Bank");
			}
		}
	}

	public void cuttingTree(Player local){
		var tree = TileObjects
				.getSurrounding(startLocation, config.radius(), config.tree().getNames())
				.stream()
				.min(Comparator.comparing(x -> x.distanceTo(startLocation)))
				.orElse(null);
		if (tree == null) {
			System.out.println("Could not find any trees");
		}
		if (!local.isMoving() && !Inventory.isFull() && tree != null) {
			tree.interact("Chop down");
		}
	}
protected boolean isEmptyTile(Tile tile)
{
	return tile != null
			&& TileObjects.getFirstAt(tile, a -> a instanceof GameObject) == null
			&& !collisionMap.fullBlock(tile.getWorldLocation());
}

@Subscribe
private void onGameTick(GameTick e)
{
	if (fmCooldown > 0) {
		fmCooldown--;
	}
	var local = Static.getClient().getLocalPlayer();
	if(local==null){
		return;
	}
	saplingIngredients.clear();
	//check bank, then for forestry events, then last cut trees if nothing else going on
	if(TileObjects.getNearest(STRUGGLING_SAPLING)!=null) {
		System.out.println("sapling");
	}
	bankRun(local);
	floweringForest(Players.getAll());
	cuttingTree(local);
	risingRoots(local);

	var logs = Inventory.getFirst(x -> x.getName().toLowerCase(Locale.ROOT).contains("logs"));
	//make fire broken atm
	if (config.makeFire()) {
		var tinderbox = Inventory.getFirst("Banker's note");
		if (logs != null && tinderbox != null) {
			var emptyTile = fireArea == null || fireArea.isEmpty() ? null : fireArea.stream()
					.filter(t ->
					{
						Tile tile = Tiles.getAt(t.getWorldLocation());
						return tile != null && isEmptyTile(tile);
					})
					.min(Comparator.comparingInt(wp -> wp.distanceTo(local)))
					.orElse(null);

			if (fireArea.isEmpty() || emptyTile == null) {
				fireArea = generateFireArea(3);
				log.debug("Generating fire area");
			}
			if (emptyTile != null) {
				if (!emptyTile.getWorldLocation().equals(local.getWorldLocation())) {
					Movement.walk(emptyTile);
				}
				fmCooldown = 4;
				logs.useOn(tinderbox);
			}
		}
	}

}
@Subscribe
public void onChatMessage (ChatMessage chatMessage)	{
		ChatMessageType chatMessageType = chatMessage.getType();
		MessageNode msg = chatMessage.getMessageNode();
		if (msg.getValue().startsWith("The sapling seems to love")) {
			int ingredientNum = msg.getValue().contains("first") ? 1 : (msg.getValue().contains("second") ? 2 :
					(msg.getValue().contains("third") ? 3 : -1));
			log.info(String.valueOf(ingredientNum));
		}
		/*
		GameObject correctOrder [] = new GameObject[saplingIngredients.size()];ah.093
		for(int i = 0; i < saplingIngredients.size();i++) {
			if (correctOrder[i].getLocalLocation() != null) {
				for(int x = 0; i < saplingIngredients.size(); x++) {
					for (int j = 0; j < 3 - correctOrder.length; j++) {
						saplingIngredients.get(i).interact();
					}
					//use on sapling
					if (msg.getValue().startsWith("The sapling seems to love")) {
						int ingredientNum = msg.getValue().contains("first") ? 1 : (msg.getValue().contains("second") ? 2 :
								(msg.getValue().contains("third") ? 3 : -1));
						correctOrder[ingredientNum] = saplingIngredients.get(i);
						log.info(String.valueOf(ingredientNum));
					}
					log.info("Removing ingredient: " + saplingIngredients.get(i));
					saplingIngredients.remove(i);
					log.debug("Correct order: " + Arrays.stream(correctOrder).iterator().toString());
				}ah.093
			}
		}
		*/

				//if ingredientFound start at ingredients[x]

	}
	@Subscribe
	public void onAnimationChanged ( final AnimationChanged event){
		/*var actor = event.getActor();
		if (actor.getAnimation() == AnimationID.LOOKING_INTO && flowers.contains(actor.getInteracting())){
			var flower = (NPC) actor.getInteracting();
			if (!activeFlowers.contains(flower)) {
				if (activeFlowers.size() == 2) {
					log.debug("Flowers reset");ah.093
					activeFlowers.clear();
				}

				System.out.println("Tracked flower {}" + flower);
				activeFlowers.add(flower);
			}
		}
		*/
	}
	@Provides
	ChopperConfig provideConfig (ConfigManager configManager)
	{
		return configManager.getConfig(ChopperConfig.class);
	}

	private List<Tile> generateFireArea ( int radius)
	{
		return Tiles.getSurrounding(Players.getLocal().getWorldLocation(), radius).stream()
				.filter(tile -> tile != null
						&& isEmptyTile(tile)
						&& Reachable.isWalkable(tile.getWorldLocation()))
				.collect(Collectors.toUnmodifiableList());
	}
	@Override
	protected int loop ()
	{
		return 0;
	}
		/*
		var local = Players.getLocal();
		saplingIngredients.clear();
		//System.out.println("locale player: " + LocalPlayer.get().getName());
		var surroundingPlayers = Players.getAll();
ah.093
        for (Player surroundingPlayer : surroundingPlayers) {
			if(surroundingPlayer.getAnimation()==AnimationID.LOOKING_INTO && !Inventory.isFull()) {
				//System.out.println(surroundingPlayer.getName() + " interacting with: "
						//+ surroundingPlayer.getInteracting());
				var flower = (NPC) surroundingPlayer.getInteracting();
				if (flower!=null && !activeFlowers.contains(flower)) {
					System.out.println("Tracked flower {}" + flower);
					activeFlowers.add(flower);
					if (activeFlowers.size() == 2) {
						System.out.println("Flowers reset");
						activeFlowers.clear();
					}
				}
				if(flower!=null && activeFlowers.contains(flower)
						&& !local.isMoving() && !local.isAnimating()){
					for (NPC activeFlower : activeFlowers) {
						activeFlower.interact("Tend-to");
					}
					//return -1;
				}

				//surroundingPlayer.getInteracting().interact("Tend-to");
				//return -1;
			}
        }
		/*var flower = (NPC) actor.getInteracting();
		if (!activeFlowers.contains(flower)) {
			if (activeFlowers.size() == 2) {
				log.debug("Flowers reset");
				activeFlowers.clear();
			}

			System.out.println("Tracked flower {}" + flower);
			activeFlowers.add(flower);*/
			//System.out.println("local player interacting, animation ID: " + Players.getLocal().getName());
		/*System.out.println("interacting w/ : " + flowerPollinator.getInteracting().getName());
		System.out.println("Animation of closest: " + flowerPollinator.getAnimation());
		if(flowerPollinator.getAnimation()==AnimationID.WOODCUTTING_DRAGON){
			System.out.println("someone is pollinating");
			var flower = (NPC) flowerPollinator.getInteracting();
			flower.interact("Tend-to");
		}*/
		/*if (fmCooldown > 0 || !scriptStarted)
		{
			return -1;
		}*/

        /*var tree = TileObjects
				.getSurrounding(startLocation, 15, config.tree().getNames())
				.stream()
				.min(Comparator.comparing(x -> x.distanceTo(local.getWorldLocation())))
				.orElse(null);
		//banks the logs, keeps required items listed in config
		//need to add deposit from log basket as well, 2 ids for open and closed?
		if (config.bank() && Inventory.isFull())
		{
			TileObject bank = TileObjects.within(config.bankLocation().getArea().offset(2), obj -> obj.hasAction("Collect"))
					.stream()
					.min(Comparator.comparingInt(obj -> obj.distanceTo(Players.getLocal())))
					.orElse(null);
			if(bank==null)
				return -1;

			List<Item> unneeded = Inventory.getAll(item ->
					(!Objects.equals(item.getName(), config.requiredItems()))
							&& item.getId() != ItemID.LOG_BASKET
							&& item.getId() != ItemID.LOG_BRACE);
			Widget bankPinWindow = Widgets.get(213,1);
			var bankPinInChar = config.bankPin().toCharArray();
			if(bankPinWindow!=null && parseInt(config.bankPin())!=0){
                for (char c : bankPinInChar) {
                    Keyboard.type(c);
                    Time.sleepTick();
                }
				return -1;
			}
			if(Bank.isOpen()){
				if (!unneeded.isEmpty())
				{
					for (Item item : unneeded)
					{
						Bank.depositAll(item.getId());
						Time.sleep(100);
					}
					return -1;
				}
			}
			if (Movement.isWalking())
			{
				return -4;
			}

			if (bank != null)
			{
				bank.interact("Bank", "Use");
				return -4;
			}
			NPC banker = NPCs.getNearest("Banker");
			if (banker != null)
			{
				banker.interact("Bank");ah.093
				return -4;
			}

			Movement.walkTo(config.bankLocation());
			return -4;
		}

		/*
		if(TileObjects.getNearest(STRUGGLING_SAPLING)!=null && saplingIngredients.isEmpty()) {
			saplingIngredients.add((GameObject) TileObjects.getNearest(ROTTING_LEAVES));
			if (TileObjects.getNearest(GREEN_LEAVES) != null)
				saplingIngredients.add((GameObject) TileObjects.getNearest(GREEN_LEAVES));
			if (TileObjects.getNearest(SPLINTERED_BARK) != null)
				saplingIngredients.add((GameObject) TileObjects.getNearest(SPLINTERED_BARK));
			if (TileObjects.getNearest(DROPPINGS) != null)
				saplingIngredients.add((GameObject) TileObjects.getNearest(DROPPINGS));
			if (TileObjects.getNearest(ROTTING_LEAVES) != null)ah.093
				saplingIngredients.add((GameObject) TileObjects.getNearest(ROTTING_LEAVES));
			if (TileObjects.getNearest(WILD_MUSHROOMS) != null)
				saplingIngredients.add((GameObject) TileObjects.getNearest(WILD_MUSHROOMS));
			if (TileObjects.getNearest(WILD_MUSHROOMS_47497) != null)
				saplingIngredients.add((GameObject) TileObjects.getNearest(WILD_MUSHROOMS_47497));
			if (TileObjects.getNearest(WILD_MUSHROOMS_47498) != null)
				saplingIngredients.add((GameObject) TileObjects.getNearest(WILD_MUSHROOMS_47498));
			for(int i = 0; i < 3; i++) {
				if(!local.isMoving()) {
					log.info("Sapling ingredient name" + saplingIngredients.get(i).getName());
					saplingIngredients.get(i).interact("Collect");
				}
				else Time.sleepTick();
			}
			TileObjects.getNearest(STRUGGLING_SAPLING).interact("Add-mulch");
		*/
		/*
			//if(firstCheck){
				//saplingIngredients.get(rand.nextInt(saplingIngredients.size())).interact("Collect");
				//firstCheck = false;
			//}

        }/*
		for(int i = 0; i < saplingIngredients.size();i++) {
			if (correctOrder.get(i).getLocalLocation() != null) {
				for (int x = 0; i < saplingIngredients.size(); x++) {
					for (int j = 0; j < 3 - correctOrder.size(); j++) {
						saplingIngredients.get(i).interact();
					}
				}
			}
		}
			/*
			ChatMessageType chatMessageType = chatMessage.getType();ah.093
		MessageNode msg = chatMessage.getMessageNode();
		GameObject correctOrder [] = new GameObject[saplingIngredients.size()];
		for(int i = 0; i < saplingIngredients.size();i++) {
			if (correctOrder[i].getLocalLocation() != null) {
				for(int x = 0; i < saplingIngredients.size(); x++) {
					for (int j = 0; j < 3 - correctOrder.length; j++) {
						saplingIngredients.get(i).interact();
					}
					//use on sapling
					if (msg.getValue().startsWith("The sapling seems to love")) {
						int ingredientNum = msg.getValue().contains("first") ? 1 : (msg.getValue().contains("second") ? 2 :
								(msg.getValue().contains("third") ? 3 : -1));
						correctOrder[ingredientNum] = saplingIngredients.get(i);
						log.info(String.valueOf(ingredientNum));
					}
					log.info("Removing ingredient: " + saplingIngredients.get(i));
					saplingIngredients.remove(i);ah.093
					log.debug("Correct order: " + Arrays.stream(correctOrder).iterator().toString());

			 */
		/*var logs = Inventory.getFirst(x -> x.getName().toLowerCase(Locale.ROOT).contains("logs"));
		var greenRoots = TileObjects.getNearest(GREEN_ROOTS);
		var roots = TileObjects.getNearest(ROOTS);
		if(greenRoots !=null  && greenRoots.distanceTo(local) < 15)
		{
			if (!local.isAnimating())
			{
				System.out.println("Green Roots");
				greenRoots.interact("Chop down");
				//return -1;
			}
			return -1;
		}
		if (greenRoots == null && roots != null && roots.distanceTo(local) < 15)ah.093
		{
			if (!local.isAnimating())
			{
				System.out.println("Normal Roots");
				roots.interact("Chop down");
				//return -1;
			}
			return -1;
		}
		//drop config true - dropping in progress, need to update to drop entire inv
		if(Inventory.isFull() && config.drop()){
			Item junk = Inventory.getFirst(item -> item.getName().contains("logs"));
			if (junk != null)
			{
				junk.interact("Drop");
				log.debug("Dropping junk");
				return -1;ah.093
			}

		}
		//make fire broken atm
		if (config.makeFire())
		{
			var tinderbox = Inventory.getFirst("Tinderbox");
			if (logs != null && tinderbox != null)
			{
				var emptyTile = fireArea == null || fireArea.isEmpty() ? null : fireArea.stream()
						.filter(t ->
						{
							Tile tile = Tiles.getAt(t.getWorldLocation());
							return tile != null && isEmptyTile(tile);
						})
						.min(Comparator.comparingInt(wp -> wp.distanceTo(local)))
						.orElse(null);ah.093

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
					tinderbox.useOn(logs);
					return 500;
				}
			}
		}

		if (tree == null)ah.093
		{
			System.out.println("Could not find any trees");
			return 1;
		}

		if (!local.isMoving() && !local.isAnimating())
		{
			tree.interact("Chop down");
			return 1;
		}
		return 333;
	}*/
}