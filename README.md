# Wurm Unlimited ActionNormalizer
Speeds up actions inversely proportionally to how many actions you took

#### What does it do: 
Ensures bots and players can do the same amount of actions per day without placing hard caps on anyone. That also goes to hardly active players and very active players, everyone gets leveled to the same level plus minus a couple of hours.
#### How it does: 
For every second you are not doing an action, said second is added to a counter, when you do an action, said action will have it's duration reduced proportionally by the counter, and the counter will lose as many seconds as the action took (or would've taken, I don't remember)
#### Known side effects: 
Exacerbates greatly how bad having alts is, since the alts will get buffed with everyone else. This mod on default settings is a 4x overall speed increase, so a 1x action 1x skill server will play like a 4x action 1x skill when this is added.
#### Performance Impact: 
Should be near zero due to there being no need to tick players to calculate anything, the total amount of time the player has spent not doing actions is calculated based on his currently accumulated and the last time he did an action.
The end result is that no matter if you play 1 hour per day or have a bot running 24/7, the total amount of actions will be roughly the same due to the bot being stuck at 1.01 speed multiplier and the inactive player doing everything 24x faster. Of course, if the inactive player decides, he can quickly burn through his speed multiplier to catch up to the bot.
#### Extra features:
Replaces rare window calculation so that they are also normalized (meaning bots don't get way more rares than other users, and idle users get as many as everyone else). By default the game awards 12 rares per day if you do an action for 24/7. This mod default is 6 times per day to simulate a player spending 12 hours doing actions nonstop.
Can define exceptions so that some actions can never be speed up or slowed down by this mod, useful for PVP actions
Actions cost 12x more sleep time if your action multiplier is 12x and so forth. There might be a need to couple this with a mod that buffs sleep timers so they don't feel useless.
Automatically disabled in presence of enemy players, so you can't dig a tunnel under an enemy town with your 12x multiplier
### Commands:
/aninfo => Available to players, retrieves the total amount of accumulated time, which is used to calculate their action speed multiplier.
Result example: "Multiplier: 12.01, Saved up time: 24 hours 1 minute 3 seconds."
/angive player time_in_ms => Available for admins, adds or retracts saved up time for a player
#### Considerations:
Not all actions will work out of the box, stuff like leveling terrain needs a special function that is implemented, but custom mod actions might fall under the same category and thus not work out of the box.
The server uptime is calculated internally by this mod since this is not a value I could figure out how to get out of the box, thus servers that add this mod will have a '0 seconds' server uptime.
Players keep accumulating time even when offline or even if the server is offline, since it's not a update function, but a 'last time since player has done an action' that is used to calculate how much time is accumulated.
