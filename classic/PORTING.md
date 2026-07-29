# Classic Minecraft API differences (1.7.10 / 1.8.9 / 1.12.2)

The three Classic versions run the same shared engine but talk to three different Minecraft/Forge APIs.
Most of the Minecraft-facing code is therefore per version, not shared. This is the reference for *why*:
every row is a spot where the API moved between these lines, verified against the mapped jars (javap on
the MCP-mapped Forge jar unimined produces per version), not from memory. If you touch the Classic
Minecraft layer, check here first.

What **is** shared (Minecraft-free, in `classic/common`): the command parser (`ClassicCommands`), the
block-name to material classifier (`MaterialClassifier`), the editable config model
(`ClassicConfigModel`), and the static client handle (`ClassicClientHolder`). Everything below is why the
rest is not.

## Loader / entrypoint

| Concern | 1.7.10 | 1.8.9 | 1.12.2 |
| --- | --- | --- | --- |
| FML package | `cpw.mods.fml.*` | `net.minecraftforge.fml.*` | `net.minecraftforge.fml.*` |
| `@Mod` client-only flag | none (no `clientSideOnly`) | `clientSideOnly = true` | `clientSideOnly = true` |
| Client guard | manual: `FMLCommonHandler.instance().getSide().isClient()` in `init` | flag handles it | flag handles it |
| Lifecycle | `@Mod.EventHandler preInit/init` | same | same |

Because 1.7.10 has no `clientSideOnly`, the mod loads on a dedicated server and its `init` must not touch
any client-only type (see the guard in `1.7.10-forge/.../MinegasmClassicMod.java`). 1.8.9 and 1.12.2 share one
entrypoint under `classic/forge`; 1.7.10 has its own.

## Client tick / bus / keybindings

| Concern | 1.7.10 | 1.8.9 | 1.12.2 |
| --- | --- | --- | --- |
| Tick event | `cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent` | `net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent` | same as 1.8.9 |
| Tick event bus | `FMLCommonHandler.instance().bus()` | `MinecraftForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| `@SubscribeEvent` | `cpw.mods.fml.common.eventhandler` | `net.minecraftforge.fml.common.eventhandler` | same as 1.8.9 |
| `ClientRegistry` (keybinds) | `cpw.mods.fml.client.registry` | `net.minecraftforge.fml.client.registry` | same as 1.8.9 |
| `KeyBinding` ctor | `(name, key, category)` | `(name, key, category)` | `(name, KeyConflictContext, key, category)` used |
| Key held this frame | `getIsKeyPressed()` | `isKeyDown()` | `isKeyDown()` |
| Key edge (consume) | `isPressed()` | `isPressed()` | `isPressed()` |

`KeyConflictContext` is absent in the 1.8.9 Forge build here, present in 1.12.2. Key code constant is
LWJGL2 `org.lwjgl.input.Keyboard.KEY_NONE` on all three (all pre-1.13).

## Player / world / sampling

| Concern | 1.7.10 | 1.8.9 | 1.12.2 |
| --- | --- | --- | --- |
| Player field | `mc.thePlayer` | `mc.thePlayer` | `mc.player` |
| Player type | `EntityClientPlayerMP` | `EntityPlayerSP` | `EntityPlayerSP` |
| World field | `mc.theWorld` | `mc.theWorld` | `mc.world` |
| Look target | `mc.objectMouseOver` : `MovingObjectPosition` | same : `MovingObjectPosition` | `mc.objectMouseOver` : `RayTraceResult` |
| Hit type enum | `MovingObjectPosition.MovingObjectType` (`.BLOCK`/`.ENTITY`) | same | `RayTraceResult.Type` (`.BLOCK`/`.ENTITY`) |
| Hit block position | int fields `blockX/blockY/blockZ` | `getBlockPos()` : `net.minecraft.util.BlockPos` | `getBlockPos()` : `net.minecraft.util.math.BlockPos` |
| Block at position | `world.getBlock(x,y,z)` : `Block` (+ `getBlockMetadata`) | `world.getBlockState(pos)` : `IBlockState` | `world.getBlockState(pos)` : `IBlockState` |
| Air test | `block == Blocks.air` | `state.getBlock() == Blocks.air` | `state.getBlock() == Blocks.AIR` |
| Block id string | `Block.blockRegistry.getNameForObject(block)` : `String` | `block.getRegistryName()` : `String` | `block.getRegistryName()` : `ResourceLocation` (`.toString()`); or `getTranslationKey()` |
| Block hardness | `block.getBlockHardness(world, x, y, z)` | `block.getBlockHardness(world, pos)` | `block.getBlockHardness(state, world, pos)` |
| Mining in progress | no getter (`isHittingBlock` is private) -> inferred from attack key + block target | `playerController.getIsHittingBlock()` | `playerController.getIsHittingBlock()` |
| Held item | `getHeldItem()` | `getHeldItem()` | `getHeldItemMainhand()` |
| Empty stack test | null check | null check | `ItemStack.isEmpty()` (added 1.11) |

There is no `IBlockState` and no `BlockPos` before 1.8, which is the single biggest reason the 1.7.10
sampler is a full rewrite rather than a variant of the 1.8.9 one. `blockHardness` is a protected field on
`Block` everywhere, so the public `getBlockHardness(...)` is used (and on 1.7.10 the value is captured a
frame early, while the block still exists, for the block-break event). `getFoodStats().getFoodLevel()`,
`getHealth()`, `getAbsorptionAmount()`, `experienceLevel/experience/experienceTotal`, `isBurning()`,
`isInWater()`, `isInsideOfMaterial(Material.water)`, `fishEntity`, `motionX/Y/Z`, `getEntityId()`,
`mc.isGamePaused()`, and `mc.addScheduledTask(Runnable)` are the same across all three.

## Commands (no brigadier before 1.13)

`ICommand` is hand-implemented on every version, but the interface itself changed at 1.8 and again at
1.13 (not relevant here). Method names:

| Concern | 1.7.10 | 1.8.9 | 1.12.2 |
| --- | --- | --- | --- |
| Name | `getCommandName()` | `getCommandName()` | `getName()` |
| Usage | `getCommandUsage(sender)` | `getCommandUsage(sender)` | `getUsage(sender)` |
| Aliases | `getCommandAliases()` (raw `List`) | `getCommandAliases()` | `getAliases()` |
| Execute | `processCommand(sender, args)` | `processCommand(sender, args)` | `execute(server, sender, args)` |
| Permission | `canCommandSenderUseCommand(sender)` | `canCommandSenderUseCommand(sender)` | `checkPermission(server, sender)` |
| Tab complete | `addTabCompletionOptions(sender, args)` (no pos) | `addTabCompletionOptions(sender, args, BlockPos)` | `getTabCompletions(server, sender, args, BlockPos)` |
| `compareTo` | `compareTo(Object)` (raw `Comparable`) | `compareTo(ICommand)` | `compareTo(ICommand)` |
| Registration | `net.minecraftforge.client.ClientCommandHandler.instance` (all three) | same | same |

The `/mg` alias comes from `getAliases()`/`getCommandAliases()` on all three.

## Chat feedback

| Concern | 1.7.10 | 1.8.9 | 1.12.2 |
| --- | --- | --- | --- |
| Text class | `ChatComponentText` : `IChatComponent` | `ChatComponentText` : `IChatComponent` | `TextComponentString` : `ITextComponent` |
| Send to player | `player.addChatMessage(component)` | `player.addChatMessage(component)` | `player.sendMessage(component)` |

Sent on the client thread via `mc.addScheduledTask(...)` on all three, so provider-thread callbacks
(async connect results) are safe.

## Config screen (mods-list "Config" button)

| Concern | 1.7.10 | 1.8.9 | 1.12.2 |
| --- | --- | --- | --- |
| `IModGuiFactory` package | `cpw.mods.fml.client` | `net.minecraftforge.fml.client` | `net.minecraftforge.fml.client` |
| Factory shape | `mainConfigGuiClass()` (returns `Class`) + `getHandlerFor` | same | `hasConfigGui()` + `createConfigGui(parent)` |
| Screen construction | reflective `(GuiScreen parent)` ctor | reflective `(GuiScreen parent)` ctor | direct in `createConfigGui` (still uses the same ctor for uniformity) |
| Font accessor | `fontRendererObj` | `fontRendererObj` | `fontRenderer` |
| `GuiScreen` callbacks throw | no | `IOException` on `keyTyped`/`mouseClicked`/`actionPerformed` | same as 1.8.9 |
| `buttonList` | raw `List`, no `addButton()` | `List<GuiButton>`, no `addButton()` | `List<GuiButton>`, has `addButton(T)` |
| `GuiTextField` ctor | `(fontRenderer, x, y, w, h)` (no id) | `(id, fontRenderer, x, y, w, h)` | `(id, fontRenderer, x, y, w, h)` |
| Slider | `cpw.mods.fml.client.config.GuiSlider`, `getValueInt()` | `net.minecraftforge.fml.client.config.GuiSlider`, `getValue()` | `net.minecraftforge.fml.client.config.GuiSlider`, `getValue()` |

Because the factory is reflectively instantiated on 1.7.10/1.8.9 (no seam to pass the client), the screen
reaches the live client through the shared `ClassicClientHolder`, and 1.12.2 uses the same path for
consistency. The screen's edits go through `ClassicConfigModel`, which copies the current config and
replaces only the fields the screen shows, so a save never drops config it does not display (guarded by an
MC-free round-trip check during development).

## Multi-release jars

Legacy FML scans every class in a mod jar with an old ASM that cannot parse Java 11+ bytecode. Jackson
2.20 (pulled in for buttplug4j) ships such classes under `META-INF/versions/{11,17,21}/`, which made FML
reject the whole jar as corrupt on all three versions. The shaded jars therefore `exclude
'META-INF/versions/**'`; we target Java 8, where the base (root) copies are used, so nothing is lost.

## Toolchain

All three build on unimined 1.4.1, Gradle 8.8, a JDK 21 daemon (pinned via
`classic/gradle/gradle-daemon-jvm.properties`), targeting Java 8 bytecode. Forge only: Fabric and
NeoForge never existed for these versions. See the repository `README.md` for the overall layout.
