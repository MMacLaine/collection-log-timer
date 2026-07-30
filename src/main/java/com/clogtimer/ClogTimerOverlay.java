package com.clogtimer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.SpriteID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class ClogTimerOverlay extends Overlay
{
	private static final int TOGGLE_W = 22;
	private static final int TOGGLE_H = 18;
	private static final Color TEXT_GOLD = new Color(255, 200, 0);
	private static final Color TEXT_GOLD_DIM = new Color(120, 100, 50);

	private final Client client;
	private final ClogTimerPlugin plugin;
	private final ClogTimerConfig config;
	private final SpriteManager spriteManager;

	@Getter
	private Rectangle toggleBounds;

	private BufferedImage buttonSprite;
	private BufferedImage buttonSpriteSelected;

	@Inject
	public ClogTimerOverlay(Client client, ClogTimerPlugin plugin, ClogTimerConfig config, SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	private void loadSprites()
	{
		if (buttonSprite == null)
		{
			buttonSprite = spriteManager.getSprite(SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL, 0);
		}
		if (buttonSpriteSelected == null)
		{
			buttonSpriteSelected = spriteManager.getSprite(SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED, 0);
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget collectionLogWidget = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		if (collectionLogWidget == null || collectionLogWidget.isHidden())
		{
			toggleBounds = null;
			return null;
		}

		Rectangle containerBounds = collectionLogWidget.getBounds();
		if (containerBounds == null || containerBounds.width == 0)
		{
			toggleBounds = null;
			return null;
		}

		boolean overlayOn = config.showOverlay() && plugin.isPanelOverlayEnabled();
		drawToggleButton(graphics, containerBounds, overlayOn);

		if (!overlayOn)
		{
			return null;
		}

		Widget[] children = collectionLogWidget.getDynamicChildren();
		if (children == null)
		{
			return null;
		}

		WikiDataFetcher wikiDataFetcher = plugin.getWikiDataFetcher();
		if (wikiDataFetcher == null || !wikiDataFetcher.hasData())
		{
			return null;
		}

		java.awt.Shape originalClip = graphics.getClip();
		graphics.setClip(containerBounds);
		graphics.setFont(FontManager.getRunescapeSmallFont());

		for (Widget itemWidget : children)
		{
			if (itemWidget == null || itemWidget.isHidden())
			{
				continue;
			}

			int itemId = itemWidget.getItemId();
			if (itemId <= 0)
			{
				continue;
			}

			WikiDataFetcher.WikiItemEntry wikiEntry = wikiDataFetcher.getByItemId(itemId);
			if (wikiEntry == null || wikiEntry.getCompletionPercent() <= 0)
			{
				continue;
			}

			Rectangle bounds = itemWidget.getBounds();
			if (bounds == null || bounds.width == 0)
			{
				continue;
			}

			if (!containerBounds.intersects(bounds))
			{
				continue;
			}

			boolean obtained = itemWidget.getOpacity() == 0;

			String pctText = String.format("%.0f%%", wikiEntry.getCompletionPercent());
			Color textColor = getCompletionColor(wikiEntry.getCompletionPercent());

			if (obtained)
			{
				textColor = textColor.darker();
			}

			int textX = bounds.x + bounds.width - graphics.getFontMetrics().stringWidth(pctText) - 1;
			int textY = bounds.y + bounds.height - 2;

			graphics.setColor(Color.BLACK);
			graphics.drawString(pctText, textX + 1, textY + 1);

			graphics.setColor(textColor);
			graphics.drawString(pctText, textX, textY);
		}

		graphics.setClip(originalClip);

		return null;
	}

	private void drawToggleButton(Graphics2D graphics, Rectangle containerBounds, boolean overlayOn)
	{
		loadSprites();

		int x = containerBounds.x + containerBounds.width - TOGGLE_W - 3;
		int y = containerBounds.y + containerBounds.height - TOGGLE_H - 3;
		toggleBounds = new Rectangle(x, y, TOGGLE_W, TOGGLE_H);

		BufferedImage sprite = overlayOn ? buttonSpriteSelected : buttonSprite;
		if (sprite != null)
		{
			graphics.drawImage(sprite, x, y, TOGGLE_W, TOGGLE_H, null);
		}

		graphics.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		String label = "%";
		int textW = graphics.getFontMetrics().stringWidth(label);
		int textH = graphics.getFontMetrics().getAscent();
		int textX = x + (TOGGLE_W - textW) / 2;
		int textY = y + (TOGGLE_H + textH) / 2 - 1;

		graphics.setColor(Color.BLACK);
		graphics.drawString(label, textX + 1, textY + 1);

		graphics.setColor(overlayOn ? TEXT_GOLD : TEXT_GOLD_DIM);
		graphics.drawString(label, textX, textY);
	}

	public void toggleOverlay()
	{
		if (plugin != null)
		{
			plugin.togglePanelOverlay();
		}
	}

	private Color getCompletionColor(double pct)
	{
		if (pct >= 50)
		{
			return new Color(0, 190, 0);
		}
		if (pct >= 20)
		{
			return new Color(220, 180, 0);
		}
		if (pct >= 5)
		{
			return new Color(220, 100, 0);
		}
		return new Color(200, 50, 50);
	}
}
