# SL-DEOBF

The Starloader deobfuscator is not your traditional deobfuscator but rather a debug information regeneration tool.
This means that decompilers will have an easier time decompiling enums, switch-on-enums, generic signatures, inner classes and more.
It does not work well for anything that was put in something other than progard as the deobfuscator is meant to facilitate
the decompilation and linking of galimulator, which is only lightly obfuscated by prograd.

Additionally Sl-deobf comes with a remapper which is meant to take in ASM Classnodes and to spit them out in a remapped state.
The reason I did not use ASM's remapper is because I do not trust it (and knew about it too late).
It also comes with a WIP Stack walker and some other goodies.

As of right now this project is far from a stable release, which I will probably never do.
