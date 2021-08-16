Curseforge link: https://www.curseforge.com/minecraft/mc-mods/sodium-reforged

![Project icon](https://git-assets.jellysquid.me/hotlink-ok/sodium/icon-rounded-128px.png)

# Sodium/Lithium/Starlight (Forge fork)
![GitHub license](https://img.shields.io/github/license/jellysquid3/sodium-fabric.svg)
![GitHub tag](https://img.shields.io/github/tag/jellysquid3/sodium-fabric.svg)

### This is Forge edition of Sodium/Lithium/Starlight

Sodium is a free and open-source optimization mod for the Minecraft client that improves frame rates, reduces
micro-stutter, and fixes graphical issues in Minecraft. 

:warning: This fork uses outdated dev branch of original repo, though this fork is functional, stuff will be broken. 

This fork includes Lithium, as [original repo](https://github.com/CaffeineMC/lithium-fabric) says:

* Lithium is a free and open-source Minecraft mod which works to optimize many areas of the game in order to provide
  better overall performance. It works on both the **client and server**, and **doesn't require the mod to be installed
  on both sides**. 
  
This fork includes Starlight which is licensed under the LGPL.
### Downloads

Currently, only on [release page](https://github.com/Pannoniae/sodium-forge/releases/latest/).

### Community
Please do not join Caffeine discord if you intend to get support about this fork. All forks or unofficial version 
of sodium/phosphor/lithium are not support by original authors, if you encounter any issues, check current issues on this repo or 
make a new issue. 

### Building from source

If you're hacking on the code or would like to compile a custom build of Sodium from the latest sources, you'll want
to start here.

#### Prerequisites

You will need to install JDK 8 (or newer, see below) in order to build Sodium. You can either install this through
a package manager such as [Chocolatey](https://chocolatey.org/) on Windows or [SDKMAN!](https://sdkman.io/) on other
platforms. If you'd prefer to not use a package manager, you can always grab the installers or packages directly from
[AdoptOpenJDK](https://adoptopenjdk.net/).

On Windows, the Oracle JDK/JRE builds should be avoided where possible due to their poor quality. Always prefer using
the open-source builds from AdoptOpenJDK when possible.

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

### Building from sources

#### Requirements

- JRE 8 or newer (for running Gradle)
- JDK 8 (optional)
  - If you neither have JDK 8 available on your shell's path or installed through a supported package manager (such as
[SDKMAN](https://sdkman.io)), Gradle will automatically download a suitable toolchain from the [AdoptOpenJDK project](https://adoptopenjdk.net/)
and use it to compile the project. For more information on what package managers are supported and how you can
customize this behavior on a system-wide level, please see [Gradle's Toolchain user guide](https://docs.gradle.org/current/userguide/toolchains.html).
- Gradle 6.7 or newer (optional)
  - The [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:using_wrapper) is provided in
    this repository can be used instead of installing a suitable version of Gradle yourself. However, if you are building
    many projects, you may prefer to install it yourself through a suitable package manager as to save disk space and to
    avoid many different Gradle daemons sitting around in memory.

#### Building with Gradle

Sodium uses a typical Gradle project structure and can be built by simply running the default `build` task.

**Tip:** If this is a one-off build, and you would prefer the Gradle daemon does not stick around in memory afterwards 
(often consuming upwards of 1 GiB), then you can use the [`--no-daemon` argument](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:disabling_the_daemon)
to ensure that the daemon is torn down after the build is complete. However, subsequent Gradle builds will
[start more slowly](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:why_the_daemon) if the Gradle
daemon is not sitting warm and loaded in memory.

After Gradle finishes building the project, the resulting build artifacts (your usual mod binaries, and
their sources) can be found in `build/libs`.

Build artifacts classified with `dev` are outputs containing the sources and compiled classes
before they are remapped into stable intermediary names. If you are working in a developer environment and would
like to add the mod to your game, you should prefer to use the `modRuntime` or `modCompile` configurations provided by
Loom instead of these outputs.

Please note that support is not provided for setting up build environments or compiling the mod. We ask that
users who are looking to get their hands dirty with the code have a basic understanding of compiling Java/Gradle
projects.

### License

Sodium is licensed under GNU LGPLv3, a free and open-source license. For more information, please see the
[license file](https://github.com/jellysquid3/sodium-fabric/blob/1.16.x/dev/LICENSE.txt).
