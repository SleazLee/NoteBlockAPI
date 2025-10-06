# NoteBlockAPI
[![](https://jitpack.io/v/koca2000/NoteBlockAPI.svg)](https://jitpack.io/#koca2000/NoteBlockAPI) [![Build Status](http://ci.haprosgames.com/buildStatus/icon?job=NoteBlockAPI)](http://ci.haprosgames.com/job/NoteBlockAPI)

For information about this Spigot/Bukkit API, go to https://www.spigotmc.org/resources/noteblockapi.19287/

Dev builds are available at [Jenkins](http://ci.haprosgames.com/job/NoteBlockAPI/ "Jenkins")

## Folia Support Update Guide

This fork introduces first-class Folia support while keeping backwards compatibility with traditional Bukkit and Paper servers. The following notes outline the key changes and how to adopt them inside your own integrations.

### Runtime Folia Detection & Scheduler Routing

- A central `Scheduler` utility now lives in `com.xxmicloxx.NoteBlockAPI.utils.Scheduler`. It detects Folia at runtime and transparently dispatches every task to either the Folia schedulers or the legacy Bukkit scheduler depending on availability.【F:src/main/java/com/xxmicloxx/NoteBlockAPI/utils/Scheduler.java†L1-L117】
- The helper exposes synchronous (`run`, `runLater`, `runTimer`), asynchronous (`runAsync`, `runAsyncLater`, `runAsyncTimer`), and region-aware (`run(Location, ...)`, `runLater(Location, ...)`, `runTimer(Location, ...)`) variants. Each method returns a lightweight `Task` wrapper that can cancel either Folia or Bukkit task handles safely.【F:src/main/java/com/xxmicloxx/NoteBlockAPI/utils/Scheduler.java†L35-L211】
- Replace any direct `Bukkit.getScheduler()` calls with the matching helper to ensure code paths work on both scheduling models. This repository already migrated its internal usage; custom add-ons should follow the same pattern for consistency.【F:src/main/java/com/xxmicloxx/NoteBlockAPI/NoteBlockAPI.java†L211-L236】

### Plugin Metadata

- `plugin.yml` now declares `folia-supported: true`, which signals Folia-aware servers that the plugin uses the new scheduling model and is safe to run in multi-threaded environments.【F:src/main/resources/plugin.yml†L1-L8】

### Practical Migration Tips

- When porting existing code, prefer the global helpers (`run*`) for logic that does not touch world data and the location-based helpers when a region context is required.
- The scheduler guards against invalid zero-tick delays on Folia by running tasks immediately and returning an empty `Task` handle; callers no longer need custom checks for that edge case.【F:src/main/java/com/xxmicloxx/NoteBlockAPI/utils/Scheduler.java†L61-L211】
- During plugin shutdown, continue cancelling outstanding tasks via the returned `Task` handles or, when necessary, the legacy Bukkit cancellation path that remains in place for non-Folia servers.【F:src/main/java/com/xxmicloxx/NoteBlockAPI/NoteBlockAPI.java†L211-L236】

Adhering to these guidelines ensures NoteBlockAPI-based projects remain compatible across Bukkit, Paper, and Folia without maintaining separate code paths.
