<img src="src/main/resources/assets/sodium/icon.png" width="128">

# Sodium/Lithium/Phosphor (for Forge)
![GitHub license](https://img.shields.io/github/license/jellysquid3/sodium-fabric.svg)
![GitHub tag](https://img.shields.io/github/tag/jellysquid3/sodium-fabric.svg)

Sodium is a free and open-source optimization mod for the Minecraft client that improves frame rates, reduces
micro-stutter, and fixes graphical issues in Minecraft.

:warning: This fork uses the outdated dev branch of the original repo. Although this fork is functional, stuff will be broken.

This fork includes [Lithium](https://github.com/CaffeineMC/lithium-fabric), and [Phosphor](https://github.com/CaffeineMC/phosphor-fabric).

### Downloads

Downloads are available on the [actions page](https://github.com/GalaxiaTeam/sodium-forge/actions).

### Community

Please do not attempt to contact the developers of the original mods if you wish to get support. If you have any problems, check currently open issues, or create a new issue.

### Building from source

If you're hacking on the code or would like to compile a custom build of Sodium from the latest sources, you'll want
to start here.

#### Prerequisites

You will need to install JDK 8 (or newer, see below) in order to build Sodium. You can either install this through
a package manager such as [Chocolatey](https://chocolatey.org/) on Windows or [SDKMAN!](https://sdkman.io/) on other
platforms. If you'd prefer to not use a package manager, you can always grab the installers or packages directly from
[Adoptium](https://adoptium.net/).

On Windows, the Oracle JDK/JRE builds should be avoided where possible due to their poor quality. Always prefer using
the open-source builds from Adoptium when possible.

#### Compiling

Navigate to the directory you've cloned this repository and launch a build with Gradle using `gradlew build` (Windows)
or `./gradlew build` (macOS/Linux). If you are not using the Gradle wrapper, simply replace `gradlew` with `gradle`
or the path to it.

The initial setup may take a few minutes. After Gradle has finished building everything, you can find the resulting
artifacts in `build/libs`.

### Tuning for optimal performance

_This section is entirely optional and is only aimed at users who are interested in squeezing out every drop from their
game. Sodium will work without issue in the default configuration of almost all launchers._

Generally speaking, newer versions of Java will provide better performance not only when playing Minecraft, but when
using Sodium as well. The default configuration your game launcher provides will usually be some old version of Java 8
that has been selected to maximize hardware compatibility instead of performance.

For most users, these compatibility issues are not relevant, and it should be relatively easy to upgrade the game's Java
runtime and apply the required patches. For more information on upgrading and tuning the Java runtime, see the
guide [here](https://gist.github.com/jellysquid3/8a7b21e57f47f5711eb5697e282e502e).

### License

Sodium, Lithium, and Phosphor are licensed under GNU LGPLv3, a free and open-source license. For more information, please see the
[license file](https://github.com/GalaxiaTeam/sodium-forge/blob/1.16.x/dev/LICENSE.txt).
