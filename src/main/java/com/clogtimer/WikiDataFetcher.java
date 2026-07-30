package com.clogtimer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class WikiDataFetcher
{
	private static final String WIKI_API_URL =
		"https://oldschool.runescape.wiki/api.php?action=parse&page=Collection_log/Table&format=json&prop=text";
	private static final String USER_AGENT = "collection-log-timetocomplete-runelite-plugin";

	private final OkHttpClient httpClient;

	private final Map<Integer, WikiItemEntry> itemDataByItemId = new ConcurrentHashMap<>();
	private final Map<String, WikiItemEntry> itemDataByName = new ConcurrentHashMap<>();
	private volatile long lastFetchTime = 0;
	private static final long CACHE_DURATION_MS = 60 * 60 * 1000; // 1 hour

	@Inject
	public WikiDataFetcher(OkHttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	public void fetchData()
	{
		if (System.currentTimeMillis() - lastFetchTime < CACHE_DURATION_MS && !itemDataByItemId.isEmpty())
		{
			return;
		}

		try
		{
			Request request = new Request.Builder()
				.url(WIKI_API_URL)
				.header("User-Agent", USER_AGENT)
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful() || response.body() == null)
				{
					log.warn("Failed to fetch wiki data: {}", response.code());
					return;
				}

				String body = response.body().string();
				parseWikiResponse(body);
				lastFetchTime = System.currentTimeMillis();
				log.debug("Fetched wiki data: {} items", itemDataByItemId.size());
			}
		}
		catch (IOException e)
		{
			log.warn("Error fetching wiki collection log data", e);
		}
	}

	private void parseWikiResponse(String jsonBody)
	{
		int textStart = jsonBody.indexOf("\"text\":");
		if (textStart == -1)
		{
			return;
		}

		Map<Integer, WikiItemEntry> newItemData = new HashMap<>();
		Map<String, WikiItemEntry> newNameData = new HashMap<>();

		int pos = 0;
		while (true)
		{
			int trStart = jsonBody.indexOf("data-item-id=\\\"", pos);
			if (trStart == -1)
			{
				break;
			}

			trStart += "data-item-id=\\\"".length();
			int trEnd = jsonBody.indexOf("\\\"", trStart);
			if (trEnd == -1)
			{
				break;
			}

			int itemId;
			try
			{
				itemId = Integer.parseInt(jsonBody.substring(trStart, trEnd));
			}
			catch (NumberFormatException e)
			{
				pos = trEnd;
				continue;
			}

			String itemName = extractItemName(jsonBody, trEnd);
			String category = extractCategory(jsonBody, trEnd);
			double completionPct = extractCompletionPercent(jsonBody, trEnd);

			if (itemName != null)
			{
				WikiItemEntry entry = new WikiItemEntry(itemId, itemName, category, completionPct);
				newItemData.put(itemId, entry);
				newNameData.put(itemName.toLowerCase(), entry);
			}

			pos = trEnd + 1;
		}

		if (!newItemData.isEmpty())
		{
			long withPct = newItemData.values().stream()
				.filter(e -> e.getCompletionPercent() > 0).count();
			log.info("Wiki parse: {} items total, {} with completion%, {} categories",
				newItemData.size(), withPct,
				newItemData.values().stream().map(WikiItemEntry::getCategory).distinct().count());

			newItemData.values().stream()
				.filter(e -> e.getCompletionPercent() > 0)
				.limit(3)
				.forEach(e -> log.info("  Sample: {} (id={}) cat={} pct={}%",
					e.getItemName(), e.getItemId(), e.getCategory(), e.getCompletionPercent()));

			itemDataByItemId.clear();
			itemDataByItemId.putAll(newItemData);
			itemDataByName.clear();
			itemDataByName.putAll(newNameData);
		}
	}

	private String extractItemName(String html, int searchFrom)
	{
		String titleMarker = "title=\\\"";
		int nameIdx = html.indexOf(titleMarker, searchFrom);
		if (nameIdx == -1 || nameIdx > searchFrom + 500)
		{
			return null;
		}
		nameIdx += titleMarker.length();

		int endIdx = html.indexOf("\\\"", nameIdx);
		if (endIdx == -1)
		{
			return null;
		}

		String name = html.substring(nameIdx, endIdx);
		if (name.contains("File:") || name.isEmpty())
		{
			int nextNameIdx = html.indexOf(titleMarker, endIdx);
			if (nextNameIdx != -1 && nextNameIdx < searchFrom + 800)
			{
				nextNameIdx += titleMarker.length();
				int nextEnd = html.indexOf("\\\"", nextNameIdx);
				if (nextEnd != -1)
				{
					name = html.substring(nextNameIdx, nextEnd);
				}
			}
		}

		return name.contains("File:") ? null : decodeHtmlEntities(name);
	}

	private String decodeHtmlEntities(String text)
	{
		return text
			.replace("&#39;", "'")
			.replace("&#38;", "&")
			.replace("&#34;", "\"")
			.replace("&#lt;", "<")
			.replace("&#gt;", ">")
			.replace("&amp;", "&")
			.replace("&apos;", "'")
			.replace("&quot;", "\"")
			.replace("&lt;", "<")
			.replace("&gt;", ">");
	}

	private int findTdEnd(String html, int searchFrom)
	{
		int idx1 = html.indexOf("</td>", searchFrom);
		int idx2 = html.indexOf("<\\/td>", searchFrom);
		if (idx1 == -1)
		{
			return idx2;
		}
		if (idx2 == -1)
		{
			return idx1;
		}
		return Math.min(idx1, idx2);
	}

	private int tdEndLength(String html, int tdEndPos)
	{
		return html.startsWith("<\\/td>", tdEndPos) ? 6 : 5;
	}

	private String extractCategory(String html, int searchFrom)
	{
		int firstTdEnd = findTdEnd(html, searchFrom);
		if (firstTdEnd == -1)
		{
			return "Unknown";
		}

		int secondTdStart = html.indexOf("<td", firstTdEnd);
		if (secondTdStart == -1 || secondTdStart > firstTdEnd + 200)
		{
			return "Unknown";
		}

		int gtIdx = html.indexOf(">", secondTdStart);
		if (gtIdx == -1)
		{
			return "Unknown";
		}

		int secondTdEnd = findTdEnd(html, gtIdx);
		if (secondTdEnd == -1)
		{
			return "Unknown";
		}

		String categoryHtml = html.substring(gtIdx + 1, secondTdEnd);
		String category = categoryHtml
			.replaceAll("\\\\n", "")
			.replaceAll("<[^>]*>", "")
			.replaceAll("\\\\\"", "\"")
			.trim();
		return decodeHtmlEntities(category);
	}

	private double extractCompletionPercent(String html, int searchFrom)
	{
		int firstTdEnd = findTdEnd(html, searchFrom);
		if (firstTdEnd == -1)
		{
			return 0;
		}
		int secondTdEnd = findTdEnd(html, firstTdEnd + 1);
		if (secondTdEnd == -1)
		{
			return 0;
		}

		int thirdTdStart = html.indexOf("<td", secondTdEnd);
		if (thirdTdStart == -1 || thirdTdStart > secondTdEnd + 200)
		{
			return 0;
		}

		int gtIdx = html.indexOf(">", thirdTdStart);
		if (gtIdx == -1)
		{
			return 0;
		}

		int thirdTdEnd = findTdEnd(html, gtIdx);
		if (thirdTdEnd == -1)
		{
			return 0;
		}

		String pctStr = html.substring(gtIdx + 1, thirdTdEnd)
			.replaceAll("<[^>]*>", "")
			.replaceAll("\\\\n", "")
			.replace("%", "")
			.trim();

		try
		{
			return Double.parseDouble(pctStr);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	public WikiItemEntry getByItemId(int itemId)
	{
		return itemDataByItemId.get(itemId);
	}

	public WikiItemEntry getByName(String name)
	{
		return name == null ? null : itemDataByName.get(name.toLowerCase());
	}

	public Map<Integer, WikiItemEntry> getAllItems()
	{
		return itemDataByItemId;
	}

	public boolean hasData()
	{
		return !itemDataByItemId.isEmpty();
	}

	@Data
	public static class WikiItemEntry
	{
		private final int itemId;
		private final String itemName;
		private final String category;
		private final double completionPercent;

		public WikiItemEntry(int itemId, String itemName, String category, double completionPercent)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.category = category;
			this.completionPercent = completionPercent;
		}
	}
}
