
![Modrinth Downloads](https://img.shields.io/modrinth/dt/playtimemanager?logo=modrinth&label=Modrinth%20downloads&color=1ad96b&link=https%3A%2F%2Fmodrinth.com%2Fplugin%2Fplaytimemanager)
![Hangar Downloads](https://img.shields.io/hangar/dt/PlayTimeManager?logo=&style=flat&label=Hangar%20downloads&color=3271ec&link=https%3A%2F%2Fhangar.papermc.io%2FTheGabro%2FPlayTimeManager)
![Spiget Downloads](https://img.shields.io/spiget/downloads/118284?logo=spigotmc&label=Spigot%20downloads&color=f0972e&cacheSeconds=https%3A%2F%2Fwww.spigotmc.org%2Fresources%2Fplaytimemanager.118284%2F&link=https%3A%2F%2Fwww.spigotmc.org%2Fresources%2Fplaytimemanager.118284%2F)


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

---

> PlayTimeManager is optimized for Paper due to its superior performance, flexibility, and additional features not available on Spigot. These enhancements allow PlayTimeManager to run more efficiently and provide a better  experience for both server administrators and players. As a result, **Spigot is no longer supported**.

![alt text](https://i.imgur.com/ViNDStn.png "Divider 1")

PlayTimeManager is designed to work seamlessly with **both offline and online servers**, ensuring optimal compatibility for server administrators. **Data integrity is a top priority**, with robust techniques in place to safeguard player statistics and prevent data loss or alteration. 

The plugin features an **automatic update system** that ensures database and configuration files transition smoothly across versions while preserving previous settings. 

Additionally, **player data is not strictly tied to the server’s statistics**, providing flexibility for importing, exporting, and modifying records. When a player joins, PlayTimeManager initially retrieves their playtime from the server’s built-in statistics as a starting point. From there, the plugin tracks and stores all additional increments in its database, ensuring accurate and persistent records. This approach allows data to remain intact and transferable, even when upgrading your server’s jar file or making adjustments to player statistics.

![alt text](https://i.imgur.com/hpgk2V5.png "Divider 2")

<div align="center">
  <img src="https://i.imgur.com/Pr4a2KF.png" alt="Customization Preview">
  <br>
  <br>
</div> 

PlayTimeManager aims to provide a highly customizable experience, allowing you to tailor its features to match your server’s unique style. It supports **hex colors alongside legacy formatting and styles**, ensuring vibrant and personalized text displays. 

Player-facing messages can be fully customized, with plans to expand customization options even further—your suggestions are always welcome! The plugin also includes **specific placeholders** for displaying formatted playtime, converting it into different time units, or simplifying it for easier readability. Playtime formatting can be **fully customized** allowing you to create personalized time display formats with **complete control** over how playtime appears throughout your server.

Additionally, you can create a **customizable playtime leaderboard**, both in chat and through placeholders, with support for **LuckPerms prefixes**, making rankings even more dynamic and visually appealing.

<div align="center">
  <img src="https://i.imgur.com/0zAmQde.gif" alt="Formatted Playtime Example" width="45%">
  <img src="https://i.imgur.com/saWMotz.gif" alt="Playtime Stats Example" width="45%">
</div>

![alt text](https://i.imgur.com/2WSm6SA.png "Divider 3")

PlayTimeManager is designed to run efficiently with minimal impact on server resources. It utilizes **in-memory caching** to reduce database queries and improve processing speed, with an automatic reset system to prevent memory leaks. The plugin also leverages **lightweight SQLite** for low-overhead storage, ensuring smooth performance even with multiple concurrent reads. To further optimize efficiency, **HikariCP connection management** keeps database connections readily available, reducing delays.  


These optimizations help PlayTimeManager run smoothly and responsively while keeping resource usage low.
![alt text](https://i.imgur.com/mQDCfGD.png "Divider 4")

<div align="center">
  <img src="https://i.imgur.com/1LXoLZe.png" alt="Goals GUI Preview" width="35%">
  <img src="https://i.imgur.com/DRqOMyA.png" alt="Join streaks rewards GUI Preview" width="50%">
  <br>
  <br>
</div> 


PlayTimeManager offers two powerful and flexible systems for rewarding player activity: **Goals** and **Join Streak Rewards**.

- **Goals**: Reward players as they reach customizable milestones with flexible requirement systems that can be combined or used individually:
    - **Total playtime** tracking
    - **Permission checks** for rank-based goals
    - **PAPI placeholder** conditions that dynamically respond to **leaderboards**, **economy**, or **any plugin data** through customizable criteria based on placeholders

- **Join Streak Rewards**: Motivate regular engagement by rewarding players for consecutive joins within a customizable time window. Thanks to the plugin’s high flexibility, streaks aren't limited to daily logins, they can be configured to any time interval that fits your server’s style. The longer the streak, the greater the recognition.

Both systems share the same versatile reward types:

- Grant permissions
- Assign ranks (LuckPerms support)
- Run custom server commands
- Send tailored achievement messages
- Trigger celebratory sounds

All of this is easily managed through intuitive in-game GUIs, allowing administrators to build engaging progression systems without complex setup.


## Commands, permissions, placeholders and tutorials
Ready to get started? The comprehensive [PlayTimeManager Wiki](https://github.com/TheGaBr0/PlayTimeManager/wiki) contains everything you need including detailed command references, permission configurations, placeholder listings, and step-by-step setup tutorials. 


## To do
- Add possibility to give custom items upon goal completion
- Convert stats command into a customizable GUI
  
<br>...and your suggestions [here](https://github.com/TheGaBr0/PlayTimeManager/issues) :)
