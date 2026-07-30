# Troubleshooting

Fixes for problems that turn out to be environmental rather than mod bugs.

## Screen flashes green/blue/black on Intel graphics (Minecraft 1.7.10)

> **Confirmed fix:** installing Neodymium + UniMixins cleared this on an Intel UHD
> (i7-10610U), driver build 31.0.101.2134, 1.7.10 Forge.

### Symptom

While playing, the whole screen flashes solid green, blue, or black. It tends to
show up during actions like picking up an item, but the trigger varies. The game
keeps running underneath; only the rendering is corrupted.

### This is not a mod bug

The flashing happens in 1.7.10 with mods removed, so it is not caused by any
particular mod. Minegasm's client code was checked directly and does no world or
framebuffer rendering during gameplay, so it can be ruled out.

### Root cause

Recent Intel graphics drivers disable OpenGL texture compression. Minecraft 1.7.10
and older rely on that path, so on newer Intel drivers the textures render as
garbage and the screen flashes solid colors.

Affected setup in this report:

- GPU: Intel(R) UHD Graphics
- Driver: Build 31.0.101.2134
- Minecraft: 1.7.10 (Forge 10.13.4.1614)

Any Intel driver newer than roughly early 2022 can show the problem.

### Fixes

1. **Install the Neodymium mod** (easiest, no driver changes). It reimplements
   chunk rendering with modern OpenGL and works around the Intel breakage.
2. **Roll back the Intel graphics driver** to a build from before early 2022. The
   commonly reported known-good version is `30.0.100.9955` (October 2021).
3. **Use a dedicated GPU** if the machine has one. Set Minecraft to run on the
   dedicated card so the newer Intel driver is out of the picture.

In-game video settings (OptiFine, VBOs, Fast Render) do not reliably clear it,
since the cause is the disabled compression at the driver level rather than a
render option. Try Neodymium first, since it is just two files in a folder and
avoids fighting Windows over drivers.

#### Installing Neodymium

Use the maintained **Unofficial** fork. It needs **UniMixins** as a dependency or
it will not load. Drop both `.jar` files into the `mods` folder next to your other
mods.

- Neodymium Unofficial (latest `0.4.3-unofficial`, Dec 2024):
  https://www.curseforge.com/minecraft/mc-mods/neodymium-unofficial/files/all
- Neodymium Unofficial source: https://github.com/FalsePattern/NeodymiumUnofficial
- Original Neodymium: https://github.com/makamys/Neodymium
- UniMixins (required): https://www.curseforge.com/minecraft/mc-mods/unimixins

#### Rolling back the Intel driver

The i7-10610U (UHD Graphics, Comet Lake) is a supported target for build
`30.0.100.9955`.

- Official Intel download center (safest, search for the version):
  https://www.intel.com/content/www/us/en/download-center/home.html
- Version reference:
  https://www.geeks3d.com/20211016/intel-graphics-driver-30-0-100-9955-released-vulkan-1-2-190/

Notes:

- This is a downgrade, so Windows may refuse a normal install. Use Device Manager,
  Display adapters, Intel UHD Graphics, Update driver, "Browse my computer", "Let
  me pick", and point at the extracted driver folder. Or run the Intel installer
  and let it clean-install over the newer one.
- Windows Update or the laptop OEM will try to silently reinstall the newer driver
  later. After rolling back, pause updates or block the graphics driver so it
  sticks.
- Download from Intel's own site. Third-party mirrors that repackage drivers are a
  malware risk.

### References

- HbmMods/Hbm's-Nuclear-Tech-GIT #985, "Minecraft 1.7.10 Graphics Issues":
  https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT/issues/985
- Intel Community, "Minecraft showing as one solid colour":
  https://community.intel.com/t5/Graphics/Minecraft-showing-as-one-solid-colour/td-p/1511040
- Intel Community, "Minecraft showing as one solid color":
  https://community.intel.com/t5/Graphics/Minecraft-showing-as-one-solid-color/td-p/1450228
- Minecraft Forum, "1.7.10 Custom Modded, Screen Flickering":
  https://www.minecraftforum.net/forums/support/java-edition-support/3150839-1-7-10-custom-modded-screen-flickering
