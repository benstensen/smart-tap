_Under development!_

Smart Tap
=======
I'm currently working on this mod to add a better workflow for tap automation from Productive Metalworks / Tinker's Construct fluid containers. My motivating use case is based on the frustration I feel when I've spun up a foundry and I can't easily set up continuous pours to automate emptying a foundry of its contents. 

Say I have (bottom to top in the foundry) 3 gold blocks, 2 gold ingots, 5 iron blocks, and 7 iron ingots. If I were to set a tap on the foundry and continuously pour into a basin, I would end up with a partial pour of 2 gold ingots in the casting basin, blocking further processing. Ever since a slower block / ingot cooling rate was added to basins & tables, the manual workflow has become unbearable to the point where I would rather just use a furnace and forgo the ore multiplication afforded by the foundry.

My dream workflow is 2 taps on the foundry -- one automatically pouring all possible blocks into a basin, and one automatically pouring the remaining ingots into a table with an ingot cast. Continuous operation that removes the need for me to babysit the foundry and wait through the cooling process for poured blocks & ingots. It is possible to achieve this using other logistics mods like AE 2 or XNET, but that feels like a complicated solution for what I believe is a simple and ubiquitous use case.

I want the Smart Tap added by this mod to support that core use case, and also be generalizable to support other pouring workflows from suitable fluid containers, including filtering for particular types of fluids or pouring certain amounts of fluids.

Note on AI Usage
=======
While I've played Minecraft more than any other game in my life, I actually haven't done any development for the game (aside from a short Bukkit programming stint 6 years ago). So, I'm using Claude Code to assist in making this mod come to fruition.

I am **not** giving prompts like "Hey Claude, design and make a Smart Tap mod for me" -- I am the originator of the goals of this mod and the approach to implementation. I **am** using Claude to create scaffolding that will generally help me with getting to the point where I can write the core logic for the mod, not having to worry as much about the idiosyncrasies of how NeoForge deals with registering blocks / items, etc. With that being said, I am **not** using Claude to forgo reading NeoForge documentation or blindly green-lighting decisions regarding design or implementation. I **am** using Claude Code as a programming partner to produce high quality code and automate menial tasks like simple git operations. This README, up to the "Installation information" section below, is written by me without the usage of AI.

Different people have different levels of tolerance with respect to the role that AI plays in development. I find it a capable tool to help jump into programming with different libraries and APIs and get to the point where I can apply my own expertise as a software developer. I'm not simply handing the keys over to the AI and claiming its outputs as something that "I made". You can view [mindmap.md](/mindmap.md) which contains my initial musings for the mod, and [TODO.md](/TODO.md) to get a sense of the roadmap that I'm following to have the mod come to fruition.


Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/
