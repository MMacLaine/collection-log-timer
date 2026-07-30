package com.clogtimer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class TempleDataFetcher
{
	private static final String TEMPLE_PLAYER_API_URL =
		"https://templeosrs.com/api/collection-log/player_collections.php?player=%s";
	private static final String TEMPLE_STATIC_DATA_URL =
		"https://templeosrs.com/collection-log/scripts/clog_data.js?50";
	private static final String BROWSER_USER_AGENT =
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

	private final OkHttpClient httpClient;
	private final Gson gson;

	private final Map<String, TemplePlayerData> playerDataCache = new ConcurrentHashMap<>();
	private final Map<String, Long> lastFetchTimeByPlayer = new ConcurrentHashMap<>();
	private static final long CACHE_DURATION_MS = 5 * 60 * 1000;

	private final Map<Integer, TempleItemInfo> staticItemData = new ConcurrentHashMap<>();
	private final Map<String, TempleCategoryInfo> staticCategoryData = new ConcurrentHashMap<>();
	private volatile boolean staticDataLoaded = false;

	private final Map<Integer, Double> referenceEhc = new ConcurrentHashMap<>();
	private volatile boolean referenceEhcLoaded = false;

	@Inject
	public TempleDataFetcher(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	public void fetchStaticData()
	{
		if (staticDataLoaded)
		{
			return;
		}

		try
		{
			Request request = new Request.Builder()
				.url(TEMPLE_STATIC_DATA_URL)
				.header("User-Agent", BROWSER_USER_AGENT)
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful() || response.body() == null)
				{
					log.warn("Failed to fetch Temple static data: {}", response.code());
					return;
				}

				String js = response.body().string();
				parseStaticData(js);
				staticDataLoaded = true;
				log.debug("Loaded Temple static data: {} items, {} categories",
					staticItemData.size(), staticCategoryData.size());
			}
		}
		catch (IOException e)
		{
			log.warn("Error fetching Temple static data", e);
		}
	}

	private void parseStaticData(String js)
	{
		parseItems(js);
		parseCategories(js);
	}

	private void parseItems(String js)
	{
		int itemsStart = js.indexOf("const ITEMS = {");
		if (itemsStart == -1)
		{
			return;
		}
		itemsStart += "const ITEMS = {".length();

		int braceDepth = 1;
		int pos = itemsStart;
		while (pos < js.length() && braceDepth > 0)
		{
			char c = js.charAt(pos);
			if (c == '{')
			{
				braceDepth++;
			}
			else if (c == '}')
			{
				braceDepth--;
			}
			pos++;
		}

		String itemsBlock = js.substring(itemsStart, pos - 1);

		int searchPos = 0;
		while (searchPos < itemsBlock.length())
		{
			int quoteStart = itemsBlock.indexOf("\"", searchPos);
			if (quoteStart == -1)
			{
				break;
			}
			int quoteEnd = itemsBlock.indexOf("\"", quoteStart + 1);
			if (quoteEnd == -1)
			{
				break;
			}

			String itemIdStr = itemsBlock.substring(quoteStart + 1, quoteEnd);

			int nameStart = itemsBlock.indexOf("\"name\"", quoteEnd);
			if (nameStart == -1)
			{
				break;
			}
			int nameValStart = itemsBlock.indexOf("\"", nameStart + 6);
			if (nameValStart == -1)
			{
				break;
			}
			int nameValEnd = itemsBlock.indexOf("\"", nameValStart + 1);
			if (nameValEnd == -1)
			{
				break;
			}

			try
			{
				int itemId = Integer.parseInt(itemIdStr);
				String name = itemsBlock.substring(nameValStart + 1, nameValEnd);
				staticItemData.put(itemId, new TempleItemInfo(itemId, name));
			}
			catch (NumberFormatException ignored)
			{
			}

			searchPos = nameValEnd + 1;
		}
	}

	private void parseCategories(String js)
	{
		int catStart = js.indexOf("const CATEGORIES = {");
		if (catStart == -1)
		{
			return;
		}
		catStart += "const CATEGORIES = {".length();

		int braceDepth = 1;
		int pos = catStart;
		while (pos < js.length() && braceDepth > 0)
		{
			char c = js.charAt(pos);
			if (c == '{')
			{
				braceDepth++;
			}
			else if (c == '}')
			{
				braceDepth--;
			}
			pos++;
		}

		String catBlock = js.substring(catStart, pos - 1);

		int searchPos = 0;
		while (searchPos < catBlock.length())
		{
			int keyStart = catBlock.indexOf("\"", searchPos);
			if (keyStart == -1)
			{
				break;
			}
			int keyEnd = catBlock.indexOf("\"", keyStart + 1);
			if (keyEnd == -1)
			{
				break;
			}

			String categoryKey = catBlock.substring(keyStart + 1, keyEnd);

			int rateIdx = catBlock.indexOf("\"rate\"", keyEnd);
			int nextKeyIdx = catBlock.indexOf("\n    \"", keyEnd + 1);
			if (nextKeyIdx == -1)
			{
				nextKeyIdx = catBlock.length();
			}

			if (rateIdx == -1 || rateIdx > nextKeyIdx)
			{
				searchPos = keyEnd + 1;
				continue;
			}

			double rate = extractNumber(catBlock, rateIdx + 6);

			String bossName = categoryKey;
			int bossNameIdx = catBlock.indexOf("\"boss_name\"", keyEnd);
			if (bossNameIdx != -1 && bossNameIdx < nextKeyIdx)
			{
				int bnStart = catBlock.indexOf("\"", bossNameIdx + 11);
				if (bnStart != -1)
				{
					int bnEnd = catBlock.indexOf("\"", bnStart + 1);
					if (bnEnd != -1)
					{
						bossName = catBlock.substring(bnStart + 1, bnEnd);
					}
				}
			}

			int itemsIdx = catBlock.indexOf("\"items\"", keyEnd);
			int[] itemIds = new int[0];
			if (itemsIdx != -1 && itemsIdx < nextKeyIdx)
			{
				int arrStart = catBlock.indexOf("[", itemsIdx);
				int arrEnd = catBlock.indexOf("]", arrStart);
				if (arrStart != -1 && arrEnd != -1)
				{
					String arrStr = catBlock.substring(arrStart + 1, arrEnd).trim();
					if (!arrStr.isEmpty())
					{
						String[] parts = arrStr.split(",");
						itemIds = new int[parts.length];
						for (int i = 0; i < parts.length; i++)
						{
							try
							{
								itemIds[i] = Integer.parseInt(parts[i].trim());
							}
							catch (NumberFormatException ignored)
							{
							}
						}
					}
				}
			}

			TempleCategoryInfo catInfo = new TempleCategoryInfo(categoryKey, bossName, rate, itemIds);
			staticCategoryData.put(categoryKey, catInfo);

			for (int itemId : itemIds)
			{
				TempleItemInfo item = staticItemData.get(itemId);
				if (item != null)
				{
					item.setCategoryKey(categoryKey);
					item.setCategoryName(bossName);
				}
			}

			searchPos = nextKeyIdx;
		}
	}

	private double extractNumber(String str, int startFrom)
	{
		int colonIdx = str.indexOf(":", startFrom);
		if (colonIdx == -1)
		{
			return 0;
		}

		int commaIdx = str.indexOf(",", colonIdx);
		int braceIdx = str.indexOf("}", colonIdx);
		int endIdx = Math.min(
			commaIdx == -1 ? str.length() : commaIdx,
			braceIdx == -1 ? str.length() : braceIdx
		);

		String numStr = str.substring(colonIdx + 1, endIdx).trim();
		if (numStr.equals("null"))
		{
			return 0;
		}

		try
		{
			return Double.parseDouble(numStr);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	public void fetchReferenceEhc()
	{
		if (referenceEhcLoaded)
		{
			return;
		}

		String[] referencePlayers = {"lynx titan", "clannad", "woox"};
		for (String refPlayer : referencePlayers)
		{
			try
			{
				String url = String.format(TEMPLE_PLAYER_API_URL, refPlayer.replace(' ', '+'));
				Request request = new Request.Builder()
					.url(url)
					.header("User-Agent", BROWSER_USER_AGENT)
					.header("Referer", "https://templeosrs.com/collection-log/view-collections.php")
					.build();

				try (Response response = httpClient.newCall(request).execute())
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						continue;
					}

					String body = response.body().string();
					JsonObject root = gson.fromJson(body, JsonObject.class);
					if (root.has("error"))
					{
						continue;
					}

					JsonObject data = root.getAsJsonObject("data");
					if (data == null)
					{
						continue;
					}

					JsonObject items = data.getAsJsonObject("items");
					if (items == null)
					{
						continue;
					}

					for (Map.Entry<String, JsonElement> entry : items.entrySet())
					{
						try
						{
							int itemId = Integer.parseInt(entry.getKey());
							JsonObject itemObj = entry.getValue().getAsJsonObject();
							int count = itemObj.has("count") ? itemObj.get("count").getAsInt() : 0;
							double ehc;
							if (count > 0)
							{
								ehc = itemObj.has("hours") ? itemObj.get("hours").getAsDouble() : 0;
							}
							else
							{
								ehc = itemObj.has("missing_hours") ? itemObj.get("missing_hours").getAsDouble() : 0;
							}
							if (ehc > 0)
							{
								referenceEhc.put(itemId, ehc);
							}
						}
						catch (NumberFormatException ignored)
						{
						}
					}

					referenceEhcLoaded = true;
					log.info("Loaded reference EHC data: {} items with EHC values", referenceEhc.size());
					return;
				}
			}
			catch (IOException e)
			{
				log.debug("Failed to fetch reference EHC from {}", refPlayer);
			}
		}

		log.warn("Could not load reference EHC data from any reference player");
	}

	public double getReferenceEhc(int itemId)
	{
		Double ehc = referenceEhc.get(itemId);
		return ehc != null ? ehc : 0;
	}

	public TemplePlayerData fetchPlayerData(String playerName)
	{
		if (playerName == null || playerName.isEmpty())
		{
			return null;
		}

		String normalizedName = playerName.toLowerCase().replace(' ', '+');

		Long lastFetch = lastFetchTimeByPlayer.get(normalizedName);
		if (lastFetch != null && System.currentTimeMillis() - lastFetch < CACHE_DURATION_MS)
		{
			return playerDataCache.get(normalizedName);
		}

		try
		{
			String url = String.format(TEMPLE_PLAYER_API_URL, normalizedName);
			Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", BROWSER_USER_AGENT)
				.header("Referer", "https://templeosrs.com/collection-log/view-collections.php")
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful() || response.body() == null)
				{
					log.debug("Temple OSRS returned {} for player {}", response.code(), playerName);
					return null;
				}

				String body = response.body().string();
				TemplePlayerData data = parsePlayerResponse(body, playerName);

				if (data != null)
				{
					playerDataCache.put(normalizedName, data);
					lastFetchTimeByPlayer.put(normalizedName, System.currentTimeMillis());
					log.debug("Fetched Temple data for {}: {} EHC, {}/{} items",
						playerName, data.getTotalEhc(), data.getObtainedCount(), data.getTotalCount());
				}

				return data;
			}
		}
		catch (IOException e)
		{
			log.warn("Error fetching Temple OSRS data for {}", playerName, e);
			return null;
		}
	}

	private TemplePlayerData parsePlayerResponse(String jsonBody, String playerName)
	{
		try
		{
			JsonObject root = gson.fromJson(jsonBody, JsonObject.class);

			if (root.has("error"))
			{
				log.debug("Temple API error for {}: {}", playerName, root.get("error"));
				return null;
			}

			JsonObject data = root.getAsJsonObject("data");
			if (data == null)
			{
				return null;
			}

			double totalEhc = data.has("total_ehc") ? data.get("total_ehc").getAsDouble() : 0;

			JsonObject items = data.getAsJsonObject("items");
			if (items == null)
			{
				return null;
			}

			Map<Integer, TempleItemData> itemDataMap = new HashMap<>();
			int obtained = 0;
			int total = 0;

			for (Map.Entry<String, JsonElement> entry : items.entrySet())
			{
				try
				{
					int itemId = Integer.parseInt(entry.getKey());
					JsonObject itemObj = entry.getValue().getAsJsonObject();

					int count = itemObj.has("count") ? itemObj.get("count").getAsInt() : 0;
					boolean isObtained = count > 0;

					double ehcHours;
					if (isObtained)
					{
						ehcHours = itemObj.has("hours") ? itemObj.get("hours").getAsDouble() : 0;
					}
					else
					{
						ehcHours = itemObj.has("missing_hours") ? itemObj.get("missing_hours").getAsDouble() : 0;
					}

					String itemName = null;
					String categoryName = null;
					TempleItemInfo staticInfo = staticItemData.get(itemId);
					if (staticInfo != null)
					{
						itemName = staticInfo.getName();
						categoryName = staticInfo.getCategoryName();
					}

					itemDataMap.put(itemId, new TempleItemData(itemId, itemName, categoryName, count, ehcHours, isObtained));

					total++;
					if (isObtained)
					{
						obtained++;
					}
				}
				catch (NumberFormatException ignored)
				{
				}
			}

			return new TemplePlayerData(playerName, totalEhc, obtained, total, itemDataMap);
		}
		catch (Exception e)
		{
			log.warn("Error parsing Temple data for {}", playerName, e);
			return null;
		}
	}

	public TemplePlayerData getCachedData(String playerName)
	{
		if (playerName == null)
		{
			return null;
		}
		return playerDataCache.get(playerName.toLowerCase().replace(' ', '+'));
	}

	public TempleItemInfo getStaticItemInfo(int itemId)
	{
		return staticItemData.get(itemId);
	}

	public TempleCategoryInfo getCategoryInfo(String categoryKey)
	{
		return staticCategoryData.get(categoryKey);
	}

	public boolean hasStaticData()
	{
		return staticDataLoaded;
	}

	@lombok.Data
	public static class TempleItemInfo
	{
		private final int itemId;
		private final String name;
		private String categoryKey;
		private String categoryName;

		public TempleItemInfo(int itemId, String name)
		{
			this.itemId = itemId;
			this.name = name;
		}
	}

	@lombok.Data
	public static class TempleCategoryInfo
	{
		private final String key;
		private final String bossName;
		private final double killsPerHour;
		private final int[] itemIds;
	}

	@lombok.Data
	public static class TempleItemData
	{
		private final int itemId;
		private final String itemName;
		private final String categoryName;
		private final int count;
		private final double ehcHours;
		private final boolean obtained;
	}

	@lombok.Data
	public static class TemplePlayerData
	{
		private final String playerName;
		private final double totalEhc;
		private final int obtainedCount;
		private final int totalCount;
		private final Map<Integer, TempleItemData> items;

		public TempleItemData getItem(int itemId)
		{
			return items.get(itemId);
		}

		public double getCompletionPercent()
		{
			return totalCount > 0 ? (double) obtainedCount / totalCount * 100 : 0;
		}
	}
}
