# PilagersEviction

A Minecraft **Paper** plugin that prevents **pillagers** from spawning at a **Pillager Outpost** after it has been “cleared”.  
Cleared outposts are stored in **Redis**, so the state survives restarts.

---

## Features

- ✅ Stops **pillager spawns** in a configurable radius after an outpost is marked cleared
- ✅ Two configurable clear triggers:
    - **Chest looted** (optionally requires an item to be taken)
    - **Destroyed** (block-break activity near the outpost + optional verification scan)
- ✅ **Admin-only marker item** to mark outposts that were destroyed **before** the plugin was installed
- ✅ Redis-backed persistence (survives reboot)
- ✅ Configurable spawn reasons to block (PATROL / STRUCTURE / NATURAL / etc.)
- ✅ Option to ignore raids (so raids still work normally)

---

## Requirements

- Paper **1.21.x** (tested on 1.21.11)
- **Java 21**
- Redis (optional but recommended)

---

## Installation

1. Build/download the plugin jar.
2. Put the jar in `plugins/`.
3. Start the server once to generate `plugins/PilagersEviction/config.yml`.
4. Configure Redis and plugin settings, then restart.

---

## Configuration

Example `config.yml`:

```yml
redis:
  enabled: true
  host: "127.0.0.1"
  port: 6379
  password: ""
  database: 0
  keyPrefix: "pilagerseviction"

clearTriggers:
  chestLooted:
    enabled: true
    requireItemTaken: true

  destroyed:
    enabled: true
    verifyByBlockScan: true
    debounceSeconds: 3
    scan:
      radius: 48
      signatureBlocks:
        - DARK_OAK_LOG
        - DARK_OAK_PLANKS
        - COBBLESTONE
        - MOSSY_COBBLESTONE
        - DARK_OAK_FENCE
        - LADDER
      remainingThreshold: 120

prevention:
  radius: 72
  ignoreRaids: true
  blockSpawnReasons:
    - NATURAL
    - STRUCTURE
    - PATROL
    - CHUNK_GEN
```
---
## Tuning notes
 - `prevention.radius`
    - Size of the “no pillager spawn” zone after cleared.
 - `destroyed.scan.remainingThreshold`
    - Lower = easier to count as destroyed
    - Higher = requires more destruction before it counts.
 - `prevention.blockSpawnReasons`
    - Only spawns with these reasons are cancelled.
---
## How it works
When a clear trigger fires, the plugin:
1. Locates the nearest pillager outpost (structure lookup)
2. Creates a stable outpost ID (world + chunk coords)
3. Stores the cleared outpost in memory + Redis
4. Cancels future pillager spawns within prevention.radius
---
## Marking outposts destroyed before installing the plugin
Use the **admin marker item**.

### Permission
 - `pilagerseviction.admin` (default: op)

`plugin.yml`
```yaml
permissions:
  pilagerseviction.admin:
    default: op
```

### Give yourself the marker item
As OP:
```text
/give @p nether_star[custom_name='{"text":"Outpost Marker","italic":false}'] 1
```

### Use the marker
1. Go near where the outpost used to be
2. Hold the **Outpost Marker** (nether star)
3. Right-click
4. The nearest outpost is marked cleared

---
## Redis persistence (important)

Redis must be configured to persist to disk or you can lose data on reboot.

Recommended Redis config (AOF):
```text
appendonly yes
appendfsync everysec
```

### Redis keys used
Using `keyPrefix: pilagerseviction`:
 - `pilagerseviction:index` -> Set of cleared outpost IDs
 - `pilagerseviction:zone:<zoneId>` -> Hash with `world`, `x`, `y`, `z`, `clearedAt`, `clearedBy` (optional)
---

## Troubleshooting

### Pillagers still spawn
 - Mark the outpost as cleared (use marker item)
 - Increase `prevention.radius`
 - Add missing spawn reasons in `blockSpawnReasons`
 - Confirm you’re not expecting raids to be blocked if `ignoreRaids: true`