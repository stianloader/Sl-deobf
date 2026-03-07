This repository has moved to Codeberg! It is now available under the following URL: <https://codeberg.org/stianloader/stianloader-deobf>
Hopefully this is the last time we change the URL of this project.

Reason for moving to codeberg is an increasing unpredictable US government, which isn't great from a perspective of a small open source project
already arguably skirting around the rule of the law. Further, no contributors could be found on github anyways, so it's not like we'd be
loosing anything with such a change.

=== 

# SL-DEOBF

The Starloader deobfuscator is not your traditional deobfuscator but rather a debug information regeneration tool.
This means that decompilers will have an easier time decompiling enums, switch-on-enums, generic signatures, inner classes and more.
It does not work well for anything that was put in something other than progard as the deobfuscator is meant to facilitate
the decompilation and linking of galimulator, which is only lightly obfuscated by prograd.

Additionally Sl-deobf comes with a remapper which is meant to take in ASM Classnodes and to spit them out in a remapped state.
The reason I did not use ASM's remapper is because I do not trust it (and knew about it too late).
It also comes with a WIP Stack walker and some other goodies.
