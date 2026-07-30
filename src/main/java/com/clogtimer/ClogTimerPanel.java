package com.clogtimer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class ClogTimerPanel extends PluginPanel
{
	private static final Color HEADER_BG = new Color(30, 30, 30);
	private static final Color ROW_BG_EVEN = new Color(40, 40, 40);
	private static final Color ROW_BG_ODD = new Color(35, 35, 35);
	private static final Color CATEGORY_BG = new Color(25, 25, 25);
	private static final Color OBTAINED_COLOR = new Color(50, 150, 50);
	private static final Color EASY_COLOR = new Color(0, 190, 0);
	private static final Color MEDIUM_COLOR = new Color(220, 180, 0);
	private static final Color HARD_COLOR = new Color(220, 100, 0);
	private static final Color VERY_HARD_COLOR = new Color(200, 50, 50);
	private static final Color BRAND_ORANGE = new Color(220, 138, 0);
	private static final Color RECOMMENDED_BG = new Color(35, 50, 35);

	private final ClogTimerConfig config;

	private JPanel itemListPanel;
	private JComboBox<SortMode> sortCombo;
	private JTextField searchField;
	private JLabel statusLabel;
	private JLabel totalItemsLabel;
	private JLabel obtainedLabel;
	private JLabel ehcLabel;
	private JLabel ehcTotalLabel;
	private JToggleButton groupByCategoryToggle;
	private JToggleButton overlayToggle;

	@lombok.Getter
	private boolean overlayEnabled = true;

	private List<CollectionLogItemData> allItems = new ArrayList<>();

	public ClogTimerPanel(ClogTimerConfig config)
	{
		super(true);
		this.config = config;

		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		add(buildHeaderPanel(), BorderLayout.NORTH);

		itemListPanel = new JPanel();
		itemListPanel.setLayout(new BoxLayout(itemListPanel, BoxLayout.Y_AXIS));
		itemListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(itemListPanel, BorderLayout.CENTER);
	}

	private JPanel buildHeaderPanel()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(HEADER_BG);
		header.setBorder(new EmptyBorder(8, 8, 8, 8));

		JLabel title = new JLabel("Collection Log Timer");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(BRAND_ORANGE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		header.add(Box.createVerticalStrut(6));

		JPanel statsPanel = new JPanel(new GridLayout(3, 2, 4, 2));
		statsPanel.setBackground(HEADER_BG);
		statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		totalItemsLabel = createStatLabel("Items: --");
		obtainedLabel = createStatLabel("Got: --");
		ehcTotalLabel = createStatLabel("EHC Total: --");
		ehcLabel = createStatLabel("EHC Left: --");
		statusLabel = createStatLabel("Loading...");

		statsPanel.add(totalItemsLabel);
		statsPanel.add(obtainedLabel);
		statsPanel.add(ehcTotalLabel);
		statsPanel.add(ehcLabel);
		statsPanel.add(statusLabel);
		header.add(statsPanel);
		header.add(Box.createVerticalStrut(6));

		searchField = new JTextField();
		searchField.setBackground(new Color(50, 50, 50));
		searchField.setForeground(Color.WHITE);
		searchField.setCaretColor(Color.WHITE);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(60, 60, 60)),
			new EmptyBorder(4, 6, 4, 6)
		));
		searchField.setToolTipText("Search items or categories...");
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshItemList(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshItemList(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshItemList(); }
		});
		header.add(searchField);
		header.add(Box.createVerticalStrut(4));

		JPanel controlsRow = new JPanel(new BorderLayout(4, 0));
		controlsRow.setBackground(HEADER_BG);
		controlsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		controlsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		JPanel sortPanel = new JPanel(new BorderLayout(4, 0));
		sortPanel.setBackground(HEADER_BG);

		JLabel sortLabel = new JLabel("Sort:");
		sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sortLabel.setFont(FontManager.getRunescapeSmallFont());
		sortPanel.add(sortLabel, BorderLayout.WEST);

		sortCombo = new JComboBox<>(SortMode.values());
		sortCombo.setSelectedItem(config.sortMode());
		sortCombo.setBackground(new Color(50, 50, 50));
		sortCombo.setForeground(Color.WHITE);
		sortCombo.setFont(FontManager.getRunescapeSmallFont());
		sortCombo.addActionListener(e -> refreshItemList());
		sortPanel.add(sortCombo, BorderLayout.CENTER);

		controlsRow.add(sortPanel, BorderLayout.CENTER);

		JPanel togglesPanel = new JPanel(new GridLayout(1, 2, 2, 0));
		togglesPanel.setBackground(HEADER_BG);

		groupByCategoryToggle = new JToggleButton("Grp");
		groupByCategoryToggle.setSelected(true);
		groupByCategoryToggle.setFont(FontManager.getRunescapeSmallFont());
		groupByCategoryToggle.setToolTipText("Group by category");
		groupByCategoryToggle.setPreferredSize(new Dimension(40, 22));
		groupByCategoryToggle.addActionListener(e -> refreshItemList());
		togglesPanel.add(groupByCategoryToggle);

		overlayToggle = new JToggleButton("%");
		overlayToggle.setSelected(true);
		overlayToggle.setFont(FontManager.getRunescapeSmallFont());
		overlayToggle.setToolTipText("Show % overlay on collection log");
		overlayToggle.setPreferredSize(new Dimension(30, 22));
		overlayToggle.addActionListener(e -> overlayEnabled = overlayToggle.isSelected());
		togglesPanel.add(overlayToggle);

		controlsRow.add(togglesPanel, BorderLayout.EAST);

		header.add(controlsRow);

		return header;
	}

	private JLabel createStatLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	public void setOverlayEnabled(boolean enabled)
	{
		this.overlayEnabled = enabled;
		SwingUtilities.invokeLater(() -> overlayToggle.setSelected(enabled));
	}

	public void updateItems(List<CollectionLogItemData> items)
	{
		this.allItems = new ArrayList<>(items);
		SwingUtilities.invokeLater(this::refreshItemList);
	}

	public void setStatus(String status)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText(status));
	}

	private void refreshItemList()
	{
		itemListPanel.removeAll();

		String searchText = searchField.getText().toLowerCase().trim();
		SortMode sort = (SortMode) sortCombo.getSelectedItem();
		boolean showObtained = config.showObtained();
		boolean groupByCategory = groupByCategoryToggle.isSelected();

		List<CollectionLogItemData> filtered = new ArrayList<>();
		int obtainedCount = 0;
		double totalEhc = 0;
		double totalMissingEhc = 0;

		for (CollectionLogItemData item : allItems)
		{
			totalEhc += item.getEhcHours();
			if (item.isObtained())
			{
				obtainedCount++;
			}
			else
			{
				totalMissingEhc += item.getEhcHours();
			}

			if (!showObtained && item.isObtained())
			{
				continue;
			}

			if (!searchText.isEmpty() && !item.getItemName().toLowerCase().contains(searchText)
				&& !item.getCategory().toLowerCase().contains(searchText))
			{
				continue;
			}

			filtered.add(item);
		}

		int totalItems = allItems.size();
		totalItemsLabel.setText("Items: " + totalItems);
		obtainedLabel.setText("Got: " + obtainedCount + "/" + totalItems);
		ehcTotalLabel.setText(String.format("EHC Total: %.0fh", totalEhc));
		ehcLabel.setText(String.format("EHC Left: %.0fh", totalMissingEhc));

		if (sort != null)
		{
			filtered.sort(getComparator(sort));
		}

		if (!filtered.isEmpty())
		{
			buildRecommendedSection(filtered);
		}

		if (groupByCategory)
		{
			buildGroupedView(filtered);
		}
		else
		{
			buildFlatView(filtered);
		}

		if (filtered.isEmpty())
		{
			JLabel empty = new JLabel(allItems.isEmpty() ? "Open your Collection Log in-game" : "No matching items");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setAlignmentX(Component.CENTER_ALIGNMENT);
			empty.setBorder(new EmptyBorder(20, 10, 20, 10));
			itemListPanel.add(empty);
		}

		statusLabel.setText(filtered.size() + " shown");

		itemListPanel.revalidate();
		itemListPanel.repaint();
	}

	private void buildRecommendedSection(List<CollectionLogItemData> filtered)
	{
		List<CollectionLogItemData> missing = filtered.stream()
			.filter(i -> !i.isObtained())
			.collect(Collectors.toList());

		if (missing.isEmpty())
		{
			return;
		}

		List<CollectionLogItemData> quickWins = missing.stream()
			.filter(i -> i.getWikiCompletionPercent() > 0)
			.sorted(Comparator.comparingDouble(CollectionLogItemData::getWikiCompletionPercent).reversed())
			.limit(3)
			.collect(Collectors.toList());

		List<CollectionLogItemData> fastEhc = missing.stream()
			.filter(i -> i.getEhcHours() > 0)
			.sorted(Comparator.comparingDouble(CollectionLogItemData::getEhcHours))
			.limit(3)
			.collect(Collectors.toList());

		if (quickWins.isEmpty() && fastEhc.isEmpty())
		{
			return;
		}

		JPanel recPanel = new JPanel();
		recPanel.setLayout(new BoxLayout(recPanel, BoxLayout.Y_AXIS));
		recPanel.setBackground(RECOMMENDED_BG);
		recPanel.setBorder(new EmptyBorder(4, 6, 4, 6));
		recPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		recPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

		JLabel recTitle = new JLabel("Recommended Next");
		recTitle.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		recTitle.setForeground(EASY_COLOR);
		recPanel.add(recTitle);
		recPanel.add(Box.createVerticalStrut(2));

		if (!quickWins.isEmpty())
		{
			JLabel quickLabel = new JLabel("Most Common:");
			quickLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.PLAIN, 10f));
			quickLabel.setForeground(new Color(160, 160, 160));
			recPanel.add(quickLabel);

			for (CollectionLogItemData item : quickWins)
			{
				recPanel.add(buildCompactItemRow(item));
			}

			recPanel.add(Box.createVerticalStrut(3));
		}

		if (!fastEhc.isEmpty())
		{
			JLabel fastLabel = new JLabel("Fastest EHC:");
			fastLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.PLAIN, 10f));
			fastLabel.setForeground(new Color(160, 160, 160));
			recPanel.add(fastLabel);

			for (CollectionLogItemData item : fastEhc)
			{
				recPanel.add(buildCompactItemRow(item));
			}
		}

		itemListPanel.add(recPanel);
		itemListPanel.add(Box.createVerticalStrut(4));
	}

	private JPanel buildCompactItemRow(CollectionLogItemData item)
	{
		JPanel row = new JPanel(new BorderLayout(2, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		JLabel name = new JLabel("  " + item.getItemName());
		name.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.PLAIN, 10f));
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);

		StringBuilder stats = new StringBuilder();
		if (item.getWikiCompletionPercent() > 0)
		{
			stats.append(String.format("%.0f%%", item.getWikiCompletionPercent()));
		}
		if (item.getEhcHours() > 0)
		{
			if (stats.length() > 0)
			{
				stats.append(" | ");
			}
			stats.append(formatEhcHours(item.getEhcHours()));
		}

		JLabel statsLabel = new JLabel(stats.toString());
		statsLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.PLAIN, 10f));
		statsLabel.setForeground(new Color(180, 180, 180));
		row.add(statsLabel, BorderLayout.EAST);

		return row;
	}

	private void buildGroupedView(List<CollectionLogItemData> filtered)
	{
		Map<String, List<CollectionLogItemData>> groups = new LinkedHashMap<>();
		for (CollectionLogItemData item : filtered)
		{
			groups.computeIfAbsent(item.getCategory(), k -> new ArrayList<>()).add(item);
		}

		for (Map.Entry<String, List<CollectionLogItemData>> entry : groups.entrySet())
		{
			String category = entry.getKey();
			List<CollectionLogItemData> items = entry.getValue();

			long catObtained = items.stream().filter(CollectionLogItemData::isObtained).count();

			JPanel catHeader = new JPanel(new BorderLayout(4, 0));
			catHeader.setBackground(CATEGORY_BG);
			catHeader.setBorder(new EmptyBorder(4, 6, 4, 6));
			catHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
			catHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			JLabel catName = new JLabel(category);
			catName.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
			catName.setForeground(BRAND_ORANGE);
			catHeader.add(catName, BorderLayout.CENTER);

			JLabel catCount = new JLabel(catObtained + "/" + items.size());
			catCount.setFont(FontManager.getRunescapeSmallFont());
			catCount.setForeground(catObtained == items.size() ? EASY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
			catCount.setHorizontalAlignment(SwingConstants.RIGHT);
			catHeader.add(catCount, BorderLayout.EAST);

			itemListPanel.add(catHeader);

			for (int i = 0; i < items.size(); i++)
			{
				itemListPanel.add(buildItemRow(items.get(i), i));
			}

			itemListPanel.add(Box.createVerticalStrut(2));
		}
	}

	private void buildFlatView(List<CollectionLogItemData> filtered)
	{
		for (int i = 0; i < filtered.size(); i++)
		{
			itemListPanel.add(buildItemRow(filtered.get(i), i));
		}
	}

	private JPanel buildItemRow(CollectionLogItemData item, int index)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(index % 2 == 0 ? ROW_BG_EVEN : ROW_BG_ODD);
		row.setBorder(new EmptyBorder(3, 6, 3, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setOpaque(false);

		JLabel nameLabel = new JLabel(item.getItemName());
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(item.isObtained() ? OBTAINED_COLOR : Color.WHITE);
		leftPanel.add(nameLabel);

		if (!groupByCategoryToggle.isSelected())
		{
			JLabel categoryLabel = new JLabel(item.getCategory());
			categoryLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.PLAIN, 9f));
			categoryLabel.setForeground(new Color(120, 120, 120));
			leftPanel.add(categoryLabel);
		}

		row.add(leftPanel, BorderLayout.CENTER);

		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setOpaque(false);

		if (item.getWikiCompletionPercent() > 0)
		{
			JLabel pctLabel = new JLabel(String.format("%.1f%%", item.getWikiCompletionPercent()));
			pctLabel.setFont(FontManager.getRunescapeSmallFont());
			pctLabel.setForeground(getCompletionColor(item.getWikiCompletionPercent()));
			pctLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			pctLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
			pctLabel.setToolTipText(String.format("%.1f%% of tracked players have this", item.getWikiCompletionPercent()));
			rightPanel.add(pctLabel);
		}

		if (item.getEhcHours() > 0)
		{
			JLabel ehcLbl = new JLabel(formatEhcHours(item.getEhcHours()));
			ehcLbl.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.PLAIN, 10f));
			ehcLbl.setForeground(getEhcColor(item.getEhcHours()));
			ehcLbl.setHorizontalAlignment(SwingConstants.RIGHT);
			ehcLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
			ehcLbl.setToolTipText(String.format("%.1f efficient hours to obtain", item.getEhcHours()));
			rightPanel.add(ehcLbl);
		}

		row.add(rightPanel, BorderLayout.EAST);

		JPanel progressBar = new JPanel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				super.paintComponent(g);
				if (item.getWikiCompletionPercent() > 0)
				{
					int width = (int) (getWidth() * item.getWikiCompletionPercent() / 100.0);
					g.setColor(getCompletionColor(item.getWikiCompletionPercent()).darker());
					g.fillRect(0, 0, width, getHeight());
				}
			}
		};
		progressBar.setPreferredSize(new Dimension(0, 2));
		progressBar.setBackground(new Color(25, 25, 25));
		row.add(progressBar, BorderLayout.SOUTH);

		return row;
	}

	private String formatEhcHours(double hours)
	{
		if (hours < 1)
		{
			return String.format("%.0fm EHC", hours * 60);
		}
		if (hours >= 100)
		{
			return String.format("%.0fh EHC", hours);
		}
		return String.format("%.1fh EHC", hours);
	}

	private Color getCompletionColor(double pct)
	{
		if (pct >= 50)
		{
			return EASY_COLOR;
		}
		if (pct >= 20)
		{
			return MEDIUM_COLOR;
		}
		if (pct >= 5)
		{
			return HARD_COLOR;
		}
		return VERY_HARD_COLOR;
	}

	private Color getEhcColor(double hours)
	{
		if (hours <= 5)
		{
			return EASY_COLOR;
		}
		if (hours <= 20)
		{
			return MEDIUM_COLOR;
		}
		if (hours <= 100)
		{
			return HARD_COLOR;
		}
		return VERY_HARD_COLOR;
	}

	private Comparator<CollectionLogItemData> getComparator(SortMode mode)
	{
		switch (mode)
		{
			case COMPLETION_PCT_DESC:
				return Comparator.comparingDouble(CollectionLogItemData::getWikiCompletionPercent).reversed();
			case COMPLETION_PCT_ASC:
				return Comparator.comparingDouble(CollectionLogItemData::getWikiCompletionPercent);
			case EHC_ASC:
				return Comparator.comparingDouble(CollectionLogItemData::getEhcHours);
			case EHC_DESC:
				return Comparator.comparingDouble(CollectionLogItemData::getEhcHours).reversed();
			case ALPHABETICAL:
				return Comparator.comparing(CollectionLogItemData::getItemName, String.CASE_INSENSITIVE_ORDER);
			default:
				return Comparator.comparingDouble(CollectionLogItemData::getWikiCompletionPercent).reversed();
		}
	}
}
