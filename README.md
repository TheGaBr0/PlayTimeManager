![alt text](https://i.imgur.com/cMGDraE.png "PlayTime Logo")
<div align="center">
  <a href="https://discord.gg/yRHpgsjtRK">
    <img src="https://i.imgur.com/Vd5Rfxy.png" alt="Discord">
  </a>
  <a href="https://github.com/TheGaBr0/PlayTimeManager/wiki">
    <img src="https://i.imgur.com/PU7u3HM.png" alt="Wiki">
  </a>
</div>


<br>
Hello and welcome to PlayTimeManager! I initially created this plugin for the server on which I am currently a developer. We don't have thousands or hundreds of players, but we had to find a way to track players' playtime to promote one whenever it reaches a certain amount. I looked around on the web, but I couldn't find any plugin that was able to satisfy our issue: we needed a playtime plugin, somewhat efficient, that could automatically execute a specific task like promoting a player through Luckperms. Well, that's what this plugin is supposed to do! <br> <br>
I thought this could be useful to other server administrators as well, so I decided to share it on Spigot! If interest arises, I'll keep it updated happily :)

## What's PlayTimeManager?
PlayTimeManager is a high-performance plugin that tracks and rewards player engagement through efficient playtime monitoring. It features a comprehensive goal system where you can:
-   **Set** custom playtime goals
-   **Grant** automatic permissions and group promotions (Thanks to a LuckPerms integration)
-   **Execute** custom commands upon goal completion
-   **Play** celebration sounds
-   **Display** custom messages

The plugin's efficiency comes from smart technical strategies:
- **Caching**: Intelligent in-memory caching reduces database calls and computational load.
- **Database Optimization**: Uses lightweight SQLite for fast, low-impact storage with high-concurrency reads.
- **Connection Management**: HikariCP ensures quick, pre-established database connections with no delays.
This ensures lightning-fast performance with negligible server resource consumption.


PlaceHoldersApi (PAPI) is supported, allowing you to create leaderboards or let players view their own playtime through the use of placeholders.

While the plugin primarily supports English, player-facing messages can be customized in the config.yml. It also includes an **automatic configuration update system** that utilizes a config-version field, ensuring your config.yml file stays up to date with the latest settings while preserving your customizations. Additionally, the plugin seamlessly manages database and file updates, with a **backup automatically created** before any updates to the database. This ensures that your data remains secure while benefiting from the latest features and improvements.

Both Paper and Spigot are supported.


## Commands and permissions
For a complete list of commands, permissions, and placeholders, please visit the [PlayTimeManager Wiki](https://github.com/TheGaBr0/PlayTimeManager/wiki).

## Examples of use
### Commands
* `/playtime TheGabro add 1d` -> This command will manually add 1 day of playtime to the specified player. <br> If a player with playtime.others.modify permission tries to get the playtime of TheGabro (who has a manually modified playtime), he will also be shown the added/removed time in addition to the normal time. <br> ![alt text](https://i.imgur.com/Aqd1Yh3.png "PlayTime addition example") <br> Multiple time formats at a time are supported (e.g. 1d,4h,3m,4s)
* `/playtimepercentage 1d`: <br> ![alt text](https://i.imgur.com/wQndA7j.png "PlayTime percentage example") <br> Multiple time formats at a time are supported (e.g. 1d,4h,3m,4s)
* `/playtimegoal set veteran time:40d`: <br> ![alt text](https://i.imgur.com/1GQEfed.png "Goal creation example") <br> If not specified, the goal will be set to inactive by default. This means that the plugin will not check for its completion until it is set to active through GUI, command, or by editing the veteran.yaml config.
### Placeholders
![alt text](https://i.imgur.com/tbK5mH4.gif "PlayTime Leaderboard example")
<br>
![alt text](https://i.imgur.com/saWMotz.gif "PlayTime last seen stats example")


## To do
- Anti-afk system?
- Add possibility to give custom items upon goal completion 
