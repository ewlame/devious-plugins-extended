package net.unethicalite.plugins.chopper;

import net.runelite.client.config.Button;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.unethicalite.api.movement.pathfinder.model.BankLocation;

@ConfigGroup("unethical-chopper")
public interface ChopperConfig extends Config
{
	@ConfigItem(
			keyName = "tree",
			name = "Tree type",
			description = "The type of tree to chop",
			position = 0
	)
	default Tree tree()
	{
		return Tree.REGULAR;
	}

	@ConfigItem(
			keyName = "makeFire",
			name = "Make fire",
			description = "Make fire while chopping",
			position = 1
	)
	default boolean makeFire()
	{
		return false;
	}
	@ConfigItem(
			keyName = "dropLogs",
			name = "Drop Logs",
			description = "Drop cut logs",
			position = 2,
			disabledBy = "bankItems"
	)
	default boolean drop(){ return false; };
	@ConfigItem(
			keyName = "bankItems",
			name = "Bank Items",
			description = "banks logs",
			position = 3,
			disabledBy = "dropLogs"
	)
	default boolean bank(){ return true; }
	@ConfigItem(
			keyName = "bankLocation",
			name = "Bank Location",
			description = "",
			position = 4
	)
	default BankLocation bankLocation()
	{
		return BankLocation.getNearest();
	}
	@ConfigItem(
			keyName = "requiredItem",
			name = "Required items",
			description = "",
			position = 4
	)
	default String requiredItems()
	{
		return "Log Basket";
	}
	@ConfigItem(
			keyName = "bankPin",
			name = "Bank pin",
			description = "Input bank pin if wanting to bank",
			position = 5
	)
	default String bankPin()
	{
		return ("0");
	}
	@ConfigItem(
			keyName = "setRadius",
			name = "Set Radius",
			description = "Set the radius to search for trees/roots (FROM START TILE)",
			position = 4

	)
	default int radius()
	{
		return 15;
	}
	@ConfigItem(
		keyName = "Start",
		name = "Start/Stop",
		description = "Start/Stop button",
		position = 5)
	default Button startStopButton()
	{
		return new Button();
	}
}
