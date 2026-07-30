package com.clogtimer;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import java.util.concurrent.ScheduledExecutorService;
import java.util.HashMap;

@Slf4j
@PluginDescriptor(
	name = "Collection Log Timer",
	description = "Shows estimated completion time and global unlock rates for collection log items",
	tags = {"collection", "log", "time", "ehc", "clog", "completion", "timer"}
)
public class ClogTimerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClogTimerConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	@Getter
	private WikiDataFetcher wikiDataFetcher;

	@Inject
	@Getter
	private TempleDataFetcher templeDataFetcher;

	@Inject
	private ClogTimerOverlay overlay;

	private ClogTimerPanel panel;
	private NavigationButton navButton;

	private final Map<Integer, Boolean> obtainedItems = new HashMap<>();
	private final Map<Integer, String> itemNames = new HashMap<>();

	private final MouseListener overlayToggleMouseListener = new MouseListener()
	{
		@Override
		public MouseEvent mouseClicked(MouseEvent e)
		{
			Rectangle toggleBounds = overlay.getToggleBounds();
			if (toggleBounds != null && toggleBounds.contains(e.getPoint()))
			{
				togglePanelOverlay();
				e.consume();
			}
			return e;
		}

		@Override
		public MouseEvent mousePressed(MouseEvent e) { return e; }
		@Override
		public MouseEvent mouseReleased(MouseEvent e) { return e; }
		@Override
		public MouseEvent mouseEntered(MouseEvent e) { return e; }
		@Override
		public MouseEvent mouseExited(MouseEvent e) { return e; }
		@Override
		public MouseEvent mouseDragged(MouseEvent e) { return e; }
		@Override
		public MouseEvent mouseMoved(MouseEvent e) { return e; }
	};

	@Override
	protected void startUp() throws Exception
	{
		panel = new ClogTimerPanel(config);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		log.info("Collection Log Timer icon loaded: {}, size: {}x{}", icon != null,
			icon != null ? icon.getWidth() : 0, icon != null ? icon.getHeight() : 0);

		navButton = NavigationButton.builder()
			.tooltip("Collection Log Timer")
			.icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		log.info("Collection Log Timer navigation button added to toolbar");

		overlayManager.add(overlay);
		mouseManager.registerMouseListener(overlayToggleMouseListener);

		executor.submit(this::fetchExternalData);

		log.info("Collection Log Timer started successfully");
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(overlayToggleMouseListener);
		obtainedItems.clear();
		itemNames.clear();
		log.debug("Collection Log Timer stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			executor.submit(this::fetchExternalData);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			obtainedItems.clear();
			itemNames.clear();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION)
		{
			clientThread.invokeLater(this::readCollectionLogItems);
		}
	}

	private void readCollectionLogItems()
	{
		Widget itemsWidget = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		if (itemsWidget == null)
		{
			return;
		}

		Widget[] children = itemsWidget.getDynamicChildren();
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			if (child == null)
			{
				continue;
			}

			int itemId = child.getItemId();
			if (itemId <= 0)
			{
				continue;
			}

			boolean obtained = child.getOpacity() == 0;
			obtainedItems.put(itemId, obtained);

			String name = child.getName();
			if (name != null && !name.isEmpty())
			{
				name = name.replaceAll("<[^>]*>", "").trim();
				if (!name.isEmpty())
				{
					itemNames.put(itemId, name);
				}
			}
		}

		rebuildPanelData();
	}

	private void fetchExternalData()
	{
		panel.setStatus("Fetching...");

		if (config.useTempleData())
		{
			templeDataFetcher.fetchStaticData();
			templeDataFetcher.fetchReferenceEhc();
		}

		if (config.useWikiData())
		{
			wikiDataFetcher.fetchData();
		}

		if (config.useTempleData() && client.getLocalPlayer() != null)
		{
			String playerName = client.getLocalPlayer().getName();
			if (playerName != null)
			{
				templeDataFetcher.fetchPlayerData(playerName);
			}
		}

		rebuildPanelData();

		boolean hasAnyData = wikiDataFetcher.hasData() || templeDataFetcher.hasStaticData();
		panel.setStatus(hasAnyData ? "Ready" : "No data");
	}

	private void rebuildPanelData()
	{
		Map<Integer, WikiDataFetcher.WikiItemEntry> wikiItems = wikiDataFetcher.getAllItems();

		TempleDataFetcher.TemplePlayerData templePlayer = null;
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			templePlayer = templeDataFetcher.getCachedData(client.getLocalPlayer().getName());
		}

		Map<Integer, CollectionLogItemData> itemMap = new HashMap<>();

		for (WikiDataFetcher.WikiItemEntry wikiEntry : wikiItems.values())
		{
			int itemId = wikiEntry.getItemId();
			Boolean isObtained = obtainedItems.get(itemId);

			double ehc = 0;
			String category = wikiEntry.getCategory();

			if (templePlayer != null)
			{
				TempleDataFetcher.TempleItemData templeItem = templePlayer.getItem(itemId);
				if (templeItem != null)
				{
					ehc = templeItem.getEhcHours();
					if (templeItem.getCategoryName() != null)
					{
						category = templeItem.getCategoryName();
					}
					if (isObtained == null)
					{
						isObtained = templeItem.isObtained();
					}
				}
			}

			if (ehc <= 0)
			{
				ehc = templeDataFetcher.getReferenceEhc(itemId);
			}

			TempleDataFetcher.TempleItemInfo staticInfo = templeDataFetcher.getStaticItemInfo(itemId);
			if (staticInfo != null && staticInfo.getCategoryName() != null
				&& (category == null || category.equals("Unknown")))
			{
				category = staticInfo.getCategoryName();
			}

			itemMap.put(itemId, CollectionLogItemData.builder()
				.itemId(itemId)
				.itemName(wikiEntry.getItemName())
				.category(category)
				.wikiCompletionPercent(wikiEntry.getCompletionPercent())
				.ehcHours(ehc)
				.obtained(isObtained != null && isObtained)
				.build());
		}

		if (templePlayer != null)
		{
			for (TempleDataFetcher.TempleItemData templeItem : templePlayer.getItems().values())
			{
				if (itemMap.containsKey(templeItem.getItemId()))
				{
					continue;
				}

				String itemName = templeItem.getItemName();
				if (itemName == null)
				{
					TempleDataFetcher.TempleItemInfo info = templeDataFetcher.getStaticItemInfo(templeItem.getItemId());
					itemName = info != null ? info.getName() : "Item #" + templeItem.getItemId();
				}

				Boolean isObtained = obtainedItems.get(templeItem.getItemId());
				if (isObtained == null)
				{
					isObtained = templeItem.isObtained();
				}

				itemMap.put(templeItem.getItemId(), CollectionLogItemData.builder()
					.itemId(templeItem.getItemId())
					.itemName(itemName)
					.category(templeItem.getCategoryName() != null ? templeItem.getCategoryName() : "Unknown")
					.wikiCompletionPercent(0)
					.ehcHours(templeItem.getEhcHours())
					.obtained(isObtained)
					.build());
			}
		}

		panel.updateItems(new ArrayList<>(itemMap.values()));
	}

	public boolean isPanelOverlayEnabled()
	{
		return panel != null && panel.isOverlayEnabled();
	}

	public void togglePanelOverlay()
	{
		if (panel != null)
		{
			panel.setOverlayEnabled(!panel.isOverlayEnabled());
		}
	}

	@Provides
	ClogTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClogTimerConfig.class);
	}
}
