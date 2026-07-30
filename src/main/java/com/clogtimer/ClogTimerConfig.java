package com.clogtimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("clogtimer")
public interface ClogTimerConfig extends Config
{
	@ConfigSection(
		name = "Display",
		description = "Display settings",
		position = 0
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Data Sources",
		description = "Configure which data sources to use",
		position = 1
	)
	String dataSection = "data";

	@ConfigItem(
		keyName = "showPanel",
		name = "Show Sidebar Panel",
		description = "Show the Collection Log Timer sidebar panel",
		position = 0,
		section = displaySection
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Collection Log Overlay",
		description = "Show completion data overlaid on the in-game collection log",
		position = 1,
		section = displaySection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sortMode",
		name = "Default Sort",
		description = "How to sort items in the sidebar panel",
		position = 2,
		section = displaySection
	)
	default SortMode sortMode()
	{
		return SortMode.COMPLETION_PCT_DESC;
	}

	@ConfigItem(
		keyName = "showObtained",
		name = "Show Obtained Items",
		description = "Include items you've already obtained in the list",
		position = 3,
		section = displaySection
	)
	default boolean showObtained()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useWikiData",
		name = "Wiki Completion %",
		description = "Fetch global completion percentages from the OSRS Wiki",
		position = 0,
		section = dataSection
	)
	default boolean useWikiData()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTempleData",
		name = "Temple OSRS EHC",
		description = "Fetch EHC (Efficient Hours Clogged) data from Temple OSRS",
		position = 1,
		section = dataSection
	)
	default boolean useTempleData()
	{
		return true;
	}

	@ConfigItem(
		keyName = "filterCategory",
		name = "Filter Category",
		description = "Only show items from a specific category (leave empty for all)",
		position = 4,
		section = displaySection
	)
	default String filterCategory()
	{
		return "";
	}
}
