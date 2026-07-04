# ZyroxMC Plugin Workspace

This workspace is organized as a collection of separate ZyroxMC plugin projects.
Each project lives in its own top-level folder so plugins can be developed,
built, and versioned independently.

## Folder Structure

```text
.
+-- README.md
+-- zyrox-api/
    +-- build.gradle
    +-- settings.gradle
    +-- src/
        +-- main/
            +-- java/
            |   +-- de/tecca/zyrox/api/
            +-- resources/
                +-- plugin.yml
                +-- config.yml
+-- zyrox-dc/
    +-- build.gradle
    +-- settings.gradle
    +-- src/
        +-- main/
            +-- java/
            |   +-- de/tecca/zyrox/dc/
            +-- resources/
                +-- plugin.yml
                +-- config.yml
```

## Projects

### `zyrox-api/`

`zyrox-api` is the central Paper API plugin for ZyroxMC. It is designed to run
on the server and expose shared platform services to other ZyroxMC plugins
through Bukkit/Paper's `ServicesManager`.

The API owns the shared module registry, action registry, permission checks, and
browser dashboard. Other plugins should register their admin actions once so the
same behavior can be reached through both commands and the web dashboard.

Future plugins should be created as sibling folders, for example:

```text
zyrox-api/
zyrox-dc/
zyrox-core/
zyrox-economy/
zyrox-quests/
```

### `zyrox-dc/`

`zyrox-dc` is the Discord verification plugin for ZyroxMC. It depends on
`ZyroxAPI`, connects a Discord bot through JDA, stores linked Discord and
Minecraft accounts in SQLite, automatically whitelists submitted Minecraft
accounts, and exposes verification data inside the ZyroxAPI dashboard.

The first verification flow is intentionally immediate:

1. A Discord user clicks the verification button.
2. The bot opens a modal asking for the Minecraft username.
3. The Discord account is linked to that Minecraft username.
4. The Minecraft account is added to the server whitelist.
5. Admins can review, unlink, relink, whitelist, and unwhitelist through
   commands or the ZyroxAPI dashboard.

## Package Naming

The Java package root for ZyroxMC projects is:

```text
de.tecca.zyrox
```

The API project starts with:

```text
de.tecca.zyrox.api
```

The Discord verification project starts with:

```text
de.tecca.zyrox.dc
```

## Building

This template uses Gradle with the Groovy DSL and targets Java 21.

From inside the API project folder:

```sh
cd zyrox-api
gradle build
```

The expected output is:

```text
zyrox-api/build/libs/zyrox-api-0.1.0-SNAPSHOT.jar
```

Build `zyrox-dc` after `zyrox-api`, because it compiles against the API JAR:

```sh
cd zyrox-dc
gradle build
```

The expected output is:

```text
zyrox-dc/build/libs/zyrox-dc-0.1.0-SNAPSHOT.jar
```

If Gradle is not installed locally, install Gradle or add a Gradle Wrapper to
the project later with a compatible Gradle version.

## Using `ZyroxAPI` From Another Plugin

Other ZyroxMC plugins should depend on the API in their `plugin.yml`:

```yaml
depend:
  - ZyroxAPI
```

They can then load the API through Bukkit's services manager:

```java
ZyroxApi api = Bukkit.getServicesManager().load(ZyroxApi.class);
```

Plugins can register themselves as modules and expose dashboard/command actions
through the shared action registry:

```java
api.modules().register(new ZyroxModule(
        "example",
        "Example Plugin",
        "1.0.0",
        "Example ZyroxMC module.",
        "example.admin"
));

api.actions().register("example", new ZyroxAction(
        "reload",
        "Reload Example",
        "Reloads the example plugin.",
        "example.reload",
        List.of(),
        context -> ZyroxActionResult.success("Example reloaded.")
));
```

## Dashboard

Players with the configured permission can create a personal dashboard link:

```text
/zyrox web
```

The link contains a short-lived login token. After the first browser visit,
ZyroxAPI stores the authenticated dashboard session in an HTTP cookie and checks
permissions for the linked Minecraft player before showing modules or running
actions.

Defaults are configured in `zyrox-api/src/main/resources/config.yml`:

```yaml
web:
  bind-address: "127.0.0.1"
  port: 8080
  public-url: "http://localhost:8080"
  token-expiry-minutes: 10
  command-permission: "zyrox.web.open"
```

## Discord Verification

`zyrox-dc` depends on `ZyroxAPI` and must be configured before the Discord bot
can connect:

```yaml
discord:
  token: "CHANGE_ME"
  guild-id: "0"
  verification-channel-id: "0"
  verified-role-id: ""
```

The Discord bot needs Guild Members Intent enabled in the Discord Developer
Portal so `zyrox-dc` can show non-bot Discord members that are not linked yet.

Admin commands:

```text
/zyroxdc verify-message
/zyroxdc link <discordId> <minecraftName>
/zyroxdc unlink <discordId>
/zyroxdc reload
```
