# Nightfall Skin

Nightfall Skin lets you change your Minecraft skin in-game (press **I**) and share it
with everyone on the server. Skins from **64x64 up to 1024x1024** are supported, and they
transfer losslessly (no quality reduction for large files).

> ม็อดสกินจากทีมงาน Nightfall โดยการทำงานนั้นเรียบง่ายครับ กดปุ่ม **I** เพื่อเปิดหน้าสกินและสามารถเปลี่ยนใส่ได้เลย
> เปลี่ยนได้ตั้งแต่ 64x64 - 1024x1024
>
> ⚠️ ม็อดนี้ทำงานโดยการยิงข้อมูลสกินเข้าเซิร์ฟเวอร์ อาจทำให้เซิร์ฟหน่วงตอนมีคนเข้าใหม่
> ⚠️ สกินไฟล์ใหญ่เกินไปอาจถูกลดความคมชัด แนะนำให้ลดขนาดไฟล์ถ้ามีปัญหา

## Loaders

This is now a **multi-loader** project built with [Architectury](https://docs.architectury.dev/).
A single shared codebase produces both a **Fabric** and a **Forge** build for **Minecraft 1.20.1**.

```
common/   <- all shared game logic, GUI, mixins, networking (loader-agnostic)
fabric/   <- Fabric entry points + fabric.mod.json
forge/    <- Forge entry point + mods.toml
```

### Requirement: Architectury API

Both builds depend on the free **[Architectury API](https://modrinth.com/mod/architectury-api)** mod.
Players must install it alongside Nightfall Skin (Fabric users also need Fabric API).

## Building

You need **JDK 17**. From the project root:

```bash
./gradlew build           # builds both loaders
```

Output jars (use the one WITHOUT a "-dev"/"-sources" suffix):

```
fabric/build/libs/nightfall-skin-fabric-1.20.1-5.0.4.jar
forge/build/libs/nightfall-skin-forge-1.20.1-5.0.4.jar
```

Useful tasks:

```bash
./gradlew :fabric:build          # only Fabric
./gradlew :forge:build           # only Forge
```

### Two players (testing skin sync)

Each loader has two client run configs so you can launch two game windows at once:

```bash
./gradlew :fabric:runClient      # Player1  (run dir: fabric/run)
./gradlew :fabric:runClient2     # Player2  (run dir: fabric/run2)

./gradlew :forge:runClient       # Player1  (run dir: forge/run)
./gradlew :forge:runClient2      # Player2  (run dir: forge/run2)
```

Run each in its **own terminal**. To test that custom skins sync between players:

1. In the **Player1** window: create/open a single-player world, press Esc → **Open to LAN** → Start LAN World (note the port it prints in chat).
2. In the **Player2** window: Multiplayer → the LAN world should appear, or use **Direct Connect** → `localhost:<port>`.
3. With both in the same world, press **I** and change a skin on either client — it should appear on the other.

The two clients use different usernames/UUIDs and separate run directories, so their saves and skin storage stay independent.

## Notes for modders

- **Mappings:** Yarn (`1.20.1+build.10`) on *both* loaders, so the shared code uses
  the familiar Fabric/Yarn names everywhere.
- **Mod id / namespace:** `skinchanger` (must be hyphen-free for Forge; it is also the
  asset and registry namespace under `assets/skinchanger/`).
- Loader-specific APIs are abstracted through Architectury: networking
  (`NetworkManager`), keybindings (`KeyMappingRegistry`), registries
  (`DeferredRegister`) and lifecycle events.
