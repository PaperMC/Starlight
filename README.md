Starlight (beta)
==
Fabric mod for completely rewriting the vanilla light engine.

## Results
![Starlight crushing all](https://i.imgur.com/D90AY4t.png)
- seed: `vanillakekw`
- CPU: Intel i7-8750H @ 2.20GHz (Turboboost disabled)
- Benchmark 101x101 chunks syncloaded centered at (0,0), Warmup 101x101 chunks syncloaded centered at (-10000, -10000)
- CPU time was measured by forcing the light executor to be single threaded, which allowed
  very simple CPU thread time measuring via [visualvm](https://visualvm.github.io/).
- Starlight implementation was tested on [Tuinity](https://github.com/Spottedleaf/Tuinity/tree/dev/lighting) 

The data shows everything we need to know. Starlight took ~8 seconds to generate the light,
while Paper's light engine took ~300 seconds. Phosphor took ~170s. Compared to the best
mod available, Starlight is about 20 times faster at generating light for chunks - and 
about 40 times faster than the vanilla light engine.

When loading the data off of disk, Paper took 11.78s. (Same test as above, except that save data was kept)
Yup, this light engine can generate light data for chunks faster than Vanilla can LOAD them.
Phosphor improves this to be approximately ~5.5s, but that's hardly an improvement when you consider
the data already exists. 

Starlight will relight vanilla lit chunks due to a difference in how sky light is managed, however
the **new lighting data is completely compatible with the vanilla light engine, meaning you can load a world in 
vanilla that's been lit by Starlight without issue**. This means for loading vanilla worlds,
the light engine impact will be exactly the same for generating new light for chunks, but as documented that's rather
minimal. Once the chunks are relight, no additional processing is performed which means there is no light engine impact
when loading chunks.

## Purpose
The performance of the vanilla engine is just awful. Existing
modifications like Phosphor or Paper's light engine changes (some of 
Paper's changes include Phosphor's) fail to fix this issue.

Phosphor's changes to the existing light engine are very good, 
but there's only so much that can be done to vanilla's code to
improve performance.

Paper also suffers from the same issue as Phosphor, as they also opt
for changing vanilla code. However, Paper's changes have some technical problems 
that result in lighting broken lighting. See https://i.imgur.com/bK38jgS.png. 
For reference, this is what it should look like (generated via starlight): 
https://i.imgur.com/VND58Yr.jpg

Could these technical issues be fixed? Maybe, nobody knows what exactly
is causing the issue, and when the vanilla light engine is
overcomplicated debugging it was such a mess that everyone working on it gave up.
Paper's changes also don't even target obvious issues with the light engine...

Effectively, it's really hard to fix the performance of the light engine by
editing it - it's such a catastrophe it's better off burning it to the ground
and starting anew.

## Known Issues (This is why we're in BETA)
- The Starlight fabric mod, unlike the [Tuinity](https://github.com/Spottedleaf/Tuinity/tree/dev/lighting) 
implementation does not have general improvements around the chunk system and shape math that Tuinity/Paper has, which need
to be ported. This means the fabric mod might not live up to the performance of the generation results,
as those were obtained on the Tuinity Starlight implementation. Notably, lighting snowy areas is
notably worse on the fabric mod compared to the Tuinity implementation.

- There are lighting issues on the client that have yet to be resolved