# Collection Log Timer

A RuneLite plugin that combines two public data sources to help players identify which collection log items are easiest and fastest to obtain:

- **OSRS Wiki Completion %** — what percentage of tracked players have each item
- **Temple OSRS EHC** (Efficient Hours Clogged) — estimated hours to obtain each item

## Features

### Sidebar Panel
- Browse all 1700+ collection log items with completion percentages and EHC hours
- **Recommended Next** section showing the most common items you're missing and the fastest EHC items
- Search and filter by item name or category
- Sort by completion %, EHC, or alphabetically
- Group by category toggle
- Color-coded stats (green = easy/common, red = rare/slow)
- Progress bars per item
- Summary stats: total items, obtained count, total EHC, EHC remaining

### In-Game Overlay
- Completion percentages rendered directly on collection log items
- Color-coded by rarity
- Clips properly to the item grid (no floating text)
- Toggle on/off via an OSRS-styled button on the collection log, or from the sidebar panel

## Data Sources

| Source | What it provides | Update frequency |
|--------|-----------------|------------------|
| [OSRS Wiki](https://oldschool.runescape.wiki/w/Collection_log/Table) | Global completion % per item (via WikiSync) | Cached 1 hour |
| [Temple OSRS](https://templeosrs.com/collection-log/) | EHC per item, categories, kill rates | Cached 5 minutes |

EHC data works for all players — if your account isn't tracked on Temple, reference EHC values are used as a baseline.

## Building

Requires JDK 11+ (17 recommended).

```bash
./gradlew build
```

## Running (Developer Mode)

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew run
```

This launches RuneLite in developer mode with the plugin loaded.

## Configuration

In the RuneLite plugin settings (search "Collection Log Timer"):

- **Show Sidebar Panel** — toggle the sidebar panel
- **Show Collection Log Overlay** — toggle the in-game overlay
- **Default Sort** — choose default sort order
- **Show Obtained Items** — include/exclude items you already have
- **Wiki Completion %** — enable/disable wiki data fetching
- **Temple OSRS EHC** — enable/disable Temple data fetching

## License

BSD 2-Clause
