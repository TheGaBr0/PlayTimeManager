
# Join Streak Rewards – Configuration Guide

📁 A downloadable folder is available that contains a **template configuration for a 30-day join streak**.  
This includes:
- Days **1–9**, **11–19**, and **21–29** with standard rewards
- Days **10**, **20**, and **30** with special milestone rewards

This template is **intended for testing purposes** — simply **replace your `Rewards` folder** with the one from the download.  
Feel free to experiment and **edit the rewards** to explore how different settings behave!

💡 **Tip for Testing**:  
Use the following cron expression in your config to reset the streak every **30 seconds**, making it easier to test reward cycles:

```yaml
streak-reset-schedule: "0/30 * * ? * *"
```

👉 [**Click here to download**](https://downgit.github.io/#/home?url=https://github.com/TheGaBr0/PlayTimeManager/tree/dev/Tutorials/Rewards) the template from the GitHub repository.

---

The Join Streak system in **PlayTimeManager** allows you to define rewards that players can unlock by logging into the server across multiple days. These rewards can include messages, sounds, commands, permission grants, and more — all triggered based on a player's current join streak.

---

## 🧠 Understanding Join Streaks

There are two key types of streaks tracked for each player:

### 🔸 Absolute Join Streak  
This is the total number of consecutive days a player has logged in **without skipping # times**, where **#** is determined by `reset-joinstreak.missed-joins`. It is **always tracked**, even if the Join Streak system is deactivated or there are no rewards configured. The streak will only reset if the player breaks the streak.

> **Note:** Absolute Join Streaks cannot be used in the Join Streak system, as they are always tracked regardless of system configuration.

### 🔹 Relative Join Streak  
This is the **looping streak counter** that resets after the last configured reward has been given. This is the **active streak** used by the Join Streak system to determine when rewards should be given. 

---


## ⏰ Streak Reset Scheduling & Conditions

The system includes robust streak reset mechanisms:

### 🔃 Automatic Reset by Missed Joins  
You can enable automatic resets if a player misses a set number of times:

```yaml
reset-joinstreak:
  enabled: true
  missed-joins: 1
```

In this example, the streak resets after just one missed login.

### 🕰 Scheduled Resets via Cron  
Login validity can be **scheduled using a cron expression** via the `streak-reset-schedule` field. The cron does **not** directly reset streaks, but instead defines how long a time window is considered valid for a login. If players miss their valid login window more times than allowed in `reset-joinstreak.missed-joins`, their streak will be reset.
- `"0 0 0 * * ?"` → Every day at midnight (time window of 1 day)
- `"0 0 0 ? * MON"` → Every Monday at midnight (time window of 1 week)
- `"0 0 6 * * ?"` → Every day at 6 AM  (time window of 1 day)

#### ⏳ Timezone Options  
You can choose which timezone the cron job follows using:

```yaml
reset-schedule-timezone: server  # or "utc"
```

- `server`: Uses the system's local timezone.  
- `utc`: Uses Coordinated Universal Time (UTC), which can help maintain consistency across global servers.

#### ⚙️ Handling Server Restarts with Cron
It's important to understand how server restarts affect streaks when using scheduled resets with the Cron system:
- If you stop and restart the server during an active login window defined by your cron, the plugin will not reset streaks immediately. It will wait for the next scheduled check time. This behavior is generally safe during automated reboots, which are usually quick (a few minutes).
- However, in the case of unexpected crashes or scheduled longer downtimes, streaks might be incorrectly reset on startup. This happens because the plugin compares the new schedule against each player's last seen timestamp, possibly considering them as having missed a login.

**To protect** join streaks during restarts, especially with longer downtimes, disable streak resets temporarily:

```yaml
reset-joinstreak:
  enabled: false
  missed-joins: 1
```
Then, re-enable it after the **new login window begins**. This ensures the plugin does not mistakenly treat the downtime as a missed login.

---

## 🎁 Reward Configuration Overview

Each reward is tied to a specific join count (or a range of counts). When a player reaches that number of joins in their current relative streak, the reward becomes available.

You can configure:
- The **range of required joins** (e.g., `5-5` for the 5th join, or `5-10` for a reward spread across days 5 to 10).
- **Messages** shown to the player.
- **Sounds** to play when the reward is triggered.
- **Permissions** to grant.
- **Commands** to execute (e.g., giving items or announcing a milestone).
- An **item icon** for GUI display (which will appear in `/claimrewards` GUI as well).
- A short **description** and detailed **reward-description**.

Rewards setup can be done via the `/playtimejoinstreak` GUI directly in-game.

---

## 🗂️ Claiming & Managing Rewards

Players can claim their earned rewards using the `/claimrewards` command, which opens a dedicated GUI that:
- **Displays all claimable rewards**, both current and from previous streak cycles.
- **Visually differentiates** between claimed, unclaimed, and pending rewards.
- Allows **manual claiming**, unless auto-claim is enabled.

Players with the `playtime.joinstreak.claim.automatic` permission will receive their rewards automatically.

Unclaimed rewards from previous streak cycles must be claimed before the same reward can be obtained again. For example, if a player receives a reward at a 2-day streak but never claims it, and their streak resets after reaching day 30, they will **not** receive the 2-day reward again in the next cycle until the first one is claimed.

Since the system is currently based on **looping streaks** — once a player completes all configured rewards, their relative streak resets to 1 and starts over. This enables smooth weekly or monthly reward cycles with no manual configuration needed.

When a new time window begins, players receive their reward and the corresponding join messages upon their first login during that period. If **they're already online** when the window starts, the reward and messages are triggered automatically as if they had just logged in.

---

## 🧩 GUI & Message Customization

### 🔧 GUI Layout  
The `/claimrewards` interface can be customized through:

```
Translations/GUIs/GUIs-config.yml
```

#### 🧭 Admin View
Admins with the `playtime.joinstreak.seeplayer` permission can use:

`
/playtimejoinstreak seeplayer <playername>
`

This opens the **same visual GUI** as `/claimrewards`, but shows the **target player’s** claimed, unclaimed, and locked rewards. It is a powerful tool for monitoring player progress or investigating support issues.

> ⚠️ **Important:** This GUI is **read-only** when viewing another player’s rewards. Admins **cannot claim or modify** rewards through this interface.

---

### 💬 Global Reward Messages  
Customize common reward messages via `config.yml`:
- `join-warn-claim-message`: For manual claimers when a reward is earned.
- `join-warn-autoclaim-message`: For players who receive rewards automatically.
- `join-unclaimed-previous-message`: Warns when a previous unclaimed reward blocks the current one.

These messages apply globally to all rewards.
