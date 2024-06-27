![alt text](https://i.imgur.com/cMGDraE.png "PlayTime Logo")
Hello and welcome to PlayTimeManager! I initially created this plugin for the server on which I am currently a developer. We don't have thousands or hundreds of players, but we had to find a way to track players' playtime to promote one whenever it reaches a certain amount. I looked around on the web, but I couldn't find any plugin that was able to satisfy our issue: we needed a playtime plugin, somewhat efficient, that could automatically promote a player to a new rank through LuckPerms. Well, that's what this plugin is supposed to do!
## What's PlayTimeManager?
PlayTimeManager is a plugin designed to track and store players' playtime using SQLite and HikariCP. It allows manual modification of playtime through specific commands, enabling you to either penalize or assist players in reaching their goals. With integration to LuckPerms, you can set it to automatically promote players who achieve a certain playtime. Additionally, it supports PAPI, allowing you to create leaderboards or let players view their own playtime.
This plugin mainly supports english, however, messages that are meant to be seen by players can be edited in the config.yml. 
## Commands and permissions
| Command                                           | Permission              | Default | Description                                                                                                                            |
| --------------------------------------------------|-------------------------|:-------:|----------------------------------------------------------------------------------------------------------------------------------------|
| /playtime                                         | playtime                | yes     | Allows you to get your own own playtime                                                                                                |
| /playtime <playername>                            | playtime.others         | no      | Allows you to get other players' playtime                                                                                              |
| /playtime <playername> [add\|remove] <time>        | playtime.others.modify | no      | Allows you to modify other players' playtime                                                                                           |
| /playtimeaverage                                  | playtime.average        | no      | Allows you to get the average playtime of all players stored                                                                           |
| /playtimepercentage <time>                        | playtime.percentage     | no      | Allows you to get the percentage (and numbers) of players that<br>have a playtime greater than or equal to the specified one           |
| /playtimetop <amount of players> [page number]    | playtime.top            | no      | Allows you to get the top n players with highest playtime                                                                              |
| /playtimegroup <groupname> setTime:<timerequired> | playtime.group          | no      | Allows you to to configure a group of LuckPerms that will be<br>assigned automatically when the required time is reached by the player |
| /playtimereload                                   | playtime.reload         | no      | Allows you to reload the config.yml                                                                                                    |
| **                                                | playtime.*              | op      | Gives you access to all permissions                                                                                                    |
## PlaceHoldersApi
* `%PTM_PlayTime%` : This placeholder shows to the player his playtime
* `%PTM_PlayTime_Top_#%` : This placeholder shows the playtime of the player in position # with the highest playtime [100 is the maximum value].
* `%PTM_Nickname_Top_#%` : This placeholder shows the name of the player in position # with the highest playtime [100 is the maximum value].
