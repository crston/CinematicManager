# CinematicManager AI authoring

Generate YAML that follows `cinematic.schema.json`. Put the file in
`plugins/CinematicManager/imports/<id>.yml`; the filename (without `.yml`) and
the top-level `id` must match.

Workflow:

1. Generate a complete YAML document with `schemaVersion: 1`.
2. Run `/cinematic validate <id>.yml`.
3. Fix every reported `ERROR code at path`.
4. Run `/cinematic import <id>.yml`.
5. Use `--replace` only when replacing an existing cinematic intentionally.

Time uses Minecraft server ticks (20 ticks = 1 second). Actions at the same
tick execute in listed order. Declare every actor with `spawn_npc` before using
its `actorId`. Every `pathId` must exist under `paths`.

Set top-level `origin` to the world and location where viewers should enter the
cinematic. Playback teleports viewers there even when no camera action exists.

Text fields support legacy color codes such as `&e` and `&a`. `%player%` is a
built-in player-name alias; when PlaceholderAPI is installed, its placeholders
such as `%player_name%` are resolved as well.

Path points are offsets from an action's `origin`. They normally omit `world`.
Camera and actor movement use one point per server tick.

`wait` means “pause until player input”; it is not a timed delay. To delay an
action, increase its `tick`.

Console commands are rejected by default. They require both `executor: console`
and `ai-import.allow-console-commands: true`, because they execute with full
server permissions.

Dialogue defaults to Minecraft title (speaker + line). Optional `displayMode`
values: `title`, `actionbar`, `both`, `bossbar`.
