![alt text](https://i.imgur.com/cMGDraE.png "PlayTime Logo")
Hello and welcome to PlayTimeManager! I initially created this plugin for the server on which I am currently a developer. We don't have thousands or hundreds of players, but we had to find a way to track players' playtime to promote one whenever it reaches a certain amount. I looked around on the web, but I couldn't find any plugin that was able to satisfy our issue: we needed a playtime plugin, somewhat efficient, that could automatically promote a player to a new rank through LuckPerms. Well, that's what this plugin is supposed to do! <br> <br>
I thought this could be useful to other server administrators as well, so I decided to share it on Spigot! If interest arises, I'll keep it updated happily :)
## What's PlayTimeManager?
PlayTimeManager is a plugin designed to track and store players' playtime using SQLite and HikariCP. It allows you to manually modify playtime through specific commands, enabling you to either penalize players or assist them in reaching their goals. With integration into LuckPerms, you can automatically promote players who achieve a certain amount of playtime. Additionally, it supports PAPI, allowing you to create leaderboards or let players view their own playtime.

While the plugin primarily supports English, player-facing messages can be customized in the config.yml. It also features an automatic configuration update system using a config-version field, ensuring that your config.yml stays current with the latest settings while preserving your customizations.

Both Paper and Spigot are supported.



## Commands and permissions
| Command                                                                       | Permission              | Default | Description                                                                                                                            |
| ------------------------------------------------------------------------------|-------------------------|:-------:|----------------------------------------------------------------------------------------------------------------------------------------|
| /playtime                                                                     | playtime                | yes     | Allows you to get your own own playtime                                                                                                |
| /playtime \<playername\>                                                      | playtime.others         | no      | Allows you to get other players' playtime                                                                                              |
| /playtime \<playername\> [add\|remove] <time>                                 | playtime.others.modify  | no      | Allows you to modify other players' playtime                                                                                           |
| /playtimeaverage                                                              | playtime.average        | no      | Allows you to get the average playtime of all players stored                                                                           |
| /playtimepercentage \<time\>                                                  | playtime.percentage     | no      | Allows you to get the percentage (and numbers) of players that<br>have a playtime greater than or equal to the specified one           |
| /playtimetop \<amount of players\> [page number]                              | playtime.top            | no      | Allows you to get the top n players with highest playtime                                                                              |
| /playtimegroup \<subcommand\> <br>**Subcommands available:**<ul><li>set <groupname> setTime:\<timerequired\>                                                        </li>  <li> remove \<groupname\> </li> <li>list </li>                                                                                     | playtime.group          | no      | Allows you to to configure/remove or view LuckPerms groups that will be assigned automatically when the required time is reached by the player |
| /playtimereload                                                               | playtime.reload         | no      | Allows you to reload the config.yml and restart the LuckPerms check schedule                                                           |
| **                                                                                       | playtime.*              | op      | Gives you access to all permissions                                                                                                    |
## PlaceHoldersApi
* `%PTM_PlayTime%` : This placeholder shows to the player its playtime
* `%PTM_PlayTime_Top_#%` : This placeholder shows the playtime of the player in position # with the highest playtime [100 is the maximum value].
* `%PTM_Nickname_Top_#%` : This placeholder shows the name of the player in position # with the highest playtime [100 is the maximum value].
Example: <br>
![alt text](https://i.imgur.com/tbK5mH4.gif "PlayTime Leaderboard example")
## Examples of use
* `/playtime TheGabro add 1d` -> This command will manually add 1 day of playtime to the specified player. <br> If a player with playtime.others.modify permission tries to get the playtime of TheGabro (who has a manually modified playtime), he will also be shown the added/removed time in addition to the normal time. <br> ![alt text](https://i.imgur.com/Aqd1Yh3.png "PlayTime addition example") <br> Multiple time formats at a time are supported (e.g. 1d,4h,3m)
* `/playtimepercentage 1d`: <br> ![alt text](https://i.imgur.com/wQndA7j.png "PlayTime percentage example") <br> Multiple time formats at a time are supported (e.g. 1d,4h,3m)
* `/playtimegroup set member setTime:1d` -> This command will configure the plugin to automatically promote to "member" players who reach 1 day of playtime <br> Multiple time formats at a time are supported (e.g. 1d,4h,3m) <br>
![alt text](https://i.imgur.com/iWvqAlw.png "PlayTime group creation example")
## To do
Will add suggestions as they are proposed!
