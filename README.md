Starlight (Forge) (BETA)
==
Forge mod for completely rewriting the vanilla light engine.

## Known Issues (This is why we're in BETA)
Check out [The issues tab](https://github.com/Spottedleaf/Starlight/issues)
Please note that since this is a beta, there could be some light corrupting issues. 
Do not use if you do not want to take that risk!

At this point in Starlight's development, I expect more problems with mod conflicts than
with lighting issues, though.

## Contact
[Discord](https://discord.gg/tuinity)

## Results
Please note that I do not expect this mod to improve FPS client side. This is mostly going to affect
chunk generation speed and FPS drops when people are building on large platforms in the sky. Starlight
also fixes [MC-162253](https://bugs.mojang.com/browse/MC-162253), which only affects
lag spikes specifically from lighting.
An increase in chunk generation speed can also negatively affect FPS by making the client 
render more chunks per frame. However, I only noticed this effect, even on non-optimal hardware,
when generating amplified worlds which see a very significant chunk generation speed uplift.

The graph below shows how much time the light engine was active while generating 10404 chunks:
![Light engine time chunk generation Graph](https://i.imgur.com/5aI8Eaf.png)
- Tested via [LightBench](https://github.com/Spottedleaf/lightbench)
- World seed: vanillakekw
- CPU: Ryzen 9 5950X

Below is a graph detailing how long light updates took for breaking/placing
a block on a large platform at y = 254 down to a large platform at y = 0:
![Block update at height graph](https://i.imgur.com/kKtbe9y.png)
- Tested via [LightBench](https://github.com/Spottedleaf/lightbench)
- World is just a flat world with bedrock at y = 0 and grass at y = 254
- CPU: Ryzen 9 5950X

Below is a graph detailing light update times for a simple glowstone
place/break:
![Simple glowstone block update](https://i.imgur.com/yCNK602.png)
- Tested via [LightBench](https://github.com/Spottedleaf/lightbench)
- World is just a flat world with bedrock at y = 0 and grass at y = 254
- CPU: Ryzen 9 5950X
- Tested breaking and placing the glowstone on the bedrock platform,
  where skylight could not interfere with the test.

The results are pretty clear. Starlight is the fastest, by 
an _unbelievable_ margin. 

If you want more details on these graphs (and how Starlight achieves these numbers), 
you can check out [TECHNICAL_DETAILS.md](TECHNICAL_DETAILS.md), but that document is
mostly for people who understand programming.

## Purpose
The performance of the Vanilla engine is just awful. Existing modifications like 
Phosphor or Paper's light engine changes (some of Paper's changes include Phosphor's) 
fail to fix this issue, even though they improve it.

Phosphor's changes to the existing light engine are very good, but there's only 
so much that can be done to Vanilla's code to improve performance.
Paper also suffers from the same issue as Phosphor, as they also opt for 
changing Vanilla code.

On inspection of the light engine code to see what was causing such
regressions in performance, it became obvious that there were
performance problems were everywhere in how Vanilla processed updates, 
It seemed like Vanilla was optimised to try and reduce light updates, 
but it came at the cost of slowing down each light update. So even 
when it did _fewer_ updates, it took more time.

So, If I wanted to fix them I would effectively have to end 
up rewriting most of the light engine, and at that point it's 
easier to write a new one than rework the one old. The 
Vanilla light engine simply had too many problems to fix.