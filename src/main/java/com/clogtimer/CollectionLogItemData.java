package com.clogtimer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollectionLogItemData
{
	private final int itemId;
	private final String itemName;
	private final String category;

	private double wikiCompletionPercent;

	private double ehcHours;

	private boolean obtained;
}
