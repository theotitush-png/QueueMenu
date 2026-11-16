# QueueMenu

A small Paper plugin that keeps a named chest in the fourth hotbar slot.

## Features
- Gives each player a chest named **QUEUE MENU**.
- Uses a bold pink-to-red two-colour title inspired by the supplied screenshot.
- Locks the item to hotbar slot 4 (Bukkit slot index 3).
- Cancels dropping and inventory movement of the protected item.
- Recreates the item on join/respawn and periodically repairs its slot.
- Removes the protected item from death drops.

## Requirements
- Paper 1.21.11
- Java 21

## Build
Run with Maven:

```bash
mvn -B package
```

The plugin JAR is `target/QueueMenu.jar`.

## Install
1. Copy `QueueMenu.jar` into the server's `plugins` folder.
2. Restart the Paper server.
3. The chest appears in the fourth hotbar slot.

## Notes
The item is a protected cosmetic hotbar item. This initial version does not open a menu when clicked; a menu action needs a server-specific queue/plugin integration.
