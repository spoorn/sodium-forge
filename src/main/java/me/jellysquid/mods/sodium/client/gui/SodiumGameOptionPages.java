package me.jellysquid.mods.sodium.client.gui;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.*;
import me.jellysquid.mods.sodium.client.gui.options.binding.compat.VanillaBooleanOptionBinding;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.MinecraftOptionsStorage;
import me.jellysquid.mods.sodium.client.gui.options.storage.SodiumOptionsStorage;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.MultidrawChunkRenderBackend;
import me.jellysquid.mods.sodium.client.util.UnsafeUtil;
import net.minecraft.client.AbstractOption;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.AttackIndicatorStatus;
import net.minecraft.client.settings.GraphicsFanciness;
import net.minecraft.client.settings.ParticleStatus;
import net.minecraft.client.shader.Framebuffer;

import java.util.ArrayList;
import java.util.List;

public class SodiumGameOptionPages {
    private static final SodiumOptionsStorage sodiumOpts = new SodiumOptionsStorage();
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();

    public static OptionPage general() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.renderDistance"))
                        .setTooltip(I18n.format("sodium.options.view_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, 32, 1, ControlValueFormatter.quantity("Chunks")))
                        .setBinding((options, value) -> options.renderDistanceChunks = value, options -> options.renderDistanceChunks)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.gamma"))
                        .setTooltip(I18n.format("sodium.options.brightness.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                        .setBinding((opts, value) -> opts.gamma = value * 0.01D, (opts) -> (int) (opts.gamma / 0.01D))
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("options.renderClouds"))
                        .setTooltip(I18n.format("sodium.options.clouds.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.quality.enableClouds = value;

                            if (Minecraft.isFabulousGraphicsEnabled()) {
                                Framebuffer framebuffer = Minecraft.getInstance().worldRenderer.getCloudFrameBuffer();
                                if (framebuffer != null) {
                                    framebuffer.framebufferClear(Minecraft.IS_RUNNING_ON_MAC);
                                }
                            }
                        }, (opts) -> opts.quality.enableClouds)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.fog.name"))
                        .setTooltip(I18n.format("sodium.options.fog.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.enableFog = value, opts -> opts.quality.enableFog)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.guiScale"))
                        .setTooltip(I18n.format("sodium.options.gui_scale.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.guiScale()))
                        .setBinding((opts, value) -> {
                            opts.guiScale = value;

                            Minecraft client = Minecraft.getInstance();
                            client.updateWindowSize();
                        }, opts -> opts.guiScale)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(I18n.format("options.fullscreen"))
                        .setTooltip(I18n.format("sodium.options.fullscreen.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.fullscreen = value;

                            Minecraft client = Minecraft.getInstance();
                            MainWindow window = client.getMainWindow();

                            if (window != null && window.isFullscreen() != opts.fullscreen) {
                                window.toggleFullscreen();

                                // The client might not be able to enter full-screen mode
                                opts.fullscreen = window.isFullscreen();
                            }
                        }, (opts) -> opts.fullscreen)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(I18n.format("options.vsync"))
                        .setTooltip(I18n.format("sodium.options.v_sync.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding(new VanillaBooleanOptionBinding(AbstractOption.VSYNC))
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.framerateLimit"))
                        .setTooltip(I18n.format("sodium.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 5, 260, 5, ControlValueFormatter.fpsLimit()))
                        .setBinding((opts, value) -> {
                            opts.framerateLimit = value;
                            Minecraft.getInstance().getMainWindow().setFramerateLimit(value);
                        }, opts -> opts.framerateLimit)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(I18n.format("options.viewBobbing"))
                        .setTooltip(I18n.format("sodium.options.view_bobbing.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding(new VanillaBooleanOptionBinding(AbstractOption.VIEW_BOBBING))
                        .build())
                .add(OptionImpl.createBuilder(AttackIndicatorStatus.class, vanillaOpts)
                        .setName(I18n.format("options.attackIndicator"))
                        .setTooltip(I18n.format("sodium.options.attack_indicator.tooltip"))
                        .setControl(opts -> new CyclingControl<>(opts, AttackIndicatorStatus.class, new String[] { "Off", "Crosshair", "Hotbar" }))
                        .setBinding((opts, value) -> opts.attackIndicator = value, (opts) -> opts.attackIndicator)
                        .build())
                .build());

        return new OptionPage(I18n.format("stat.generalButton"), ImmutableList.copyOf(groups));
    }

    public static OptionPage quality() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(GraphicsFanciness.class, vanillaOpts)
                        .setName(I18n.format("options.graphics"))
                        .setTooltip(I18n.format("sodium.options.graphics_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsFanciness.class, new String[] { I18n.format("options.graphics.fast"), I18n.format("options.graphics.fancy"), I18n.format("options.graphics.fabulous") }))
                        .setBinding(
                                (opts, value) -> opts.graphicFanciness = value,
                                opts -> opts.graphicFanciness)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .setImpact(OptionImpact.HIGH)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.clouds_quality.name"))
                        .setTooltip(I18n.format("sodium.options.clouds_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.cloudQuality = value, opts -> opts.quality.cloudQuality)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.weather_quality.name"))
                        .setTooltip(I18n.format("sodium.options.weather_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.weatherQuality = value, opts -> opts.quality.weatherQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(ParticleStatus.class, vanillaOpts)
                        .setName(I18n.format("sodium.options.particle_quality.name"))
                        .setTooltip(I18n.format("sodium.options.particle_quality.tooltip"))
                        .setControl(opt -> new CyclingControl<>(opt, ParticleStatus.class, new String[] { "High", "Medium", "Low" }))
                        .setBinding((opts, value) -> opts.particles = value, (opts) -> opts.particles)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.LightingQuality.class, sodiumOpts)
                        .setName(I18n.format("options.ao"))
                        .setTooltip(I18n.format("sodium.options.smooth_lighting.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.LightingQuality.class))
                        .setBinding((opts, value) -> opts.quality.smoothLighting = value, opts -> opts.quality.smoothLighting)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.biomeBlendRadius"))
                        .setTooltip(I18n.format("sodium.options.biome_blend.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 7, 1, ControlValueFormatter.quantityOrDisabled("block(s)", "None")))
                        .setBinding((opts, value) -> opts.biomeBlendRadius = value, opts -> opts.biomeBlendRadius)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.entityDistanceScaling"))
                        .setTooltip(I18n.format("sodium.options.entity_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 50, 500, 25, ControlValueFormatter.percentage()))
                        .setBinding((opts, value) -> opts.entityDistanceScaling = value / 100.0F, opts -> Math.round(opts.entityDistanceScaling * 100.0F))
                        .setImpact(OptionImpact.MEDIUM)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(I18n.format("options.entityShadows"))
                        .setTooltip(I18n.format("sodium.options.entity_shadows.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.entityShadows = value, opts -> opts.entityShadows)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.vignette.name"))
                        .setTooltip(I18n.format("sodium.options.vignette.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.enableVignette = value, opts -> opts.quality.enableVignette)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());


        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.mipmapLevels"))
                        .setTooltip(I18n.format("sodium.options.mipmap_levels.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                        .setBinding((opts, value) -> opts.mipmapLevels = value, opts -> opts.mipmapLevels)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());


        return new OptionPage(I18n.format("sodium.options.pages.quality"), ImmutableList.copyOf(groups));
    }

    public static OptionPage advanced() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Use Chunk Multi-Draw")
                        .setTooltip("Multi-draw allows multiple chunks to be rendered with fewer draw calls, greatly reducing CPU overhead when " +
                                "rendering the world while also potentially allowing for more efficient GPU utilization. This optimization may cause " +
                                "issues with some graphics drivers, so you should try disabling it if you are experiencing glitches.")
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useChunkMultidraw = value, opts -> opts.advanced.useChunkMultidraw)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .setImpact(OptionImpact.EXTREME)
                        .setEnabled(MultidrawChunkRenderBackend.isSupported(sodiumOpts.getData().advanced.ignoreDriverBlacklist))
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Use Vertex Array Objects")
                        .setTooltip("Helps to improve performance by moving information about how vertex data should be rendered into " +
                                "the driver, allowing it to better optimize for repeated rendering of the same objects. There is generally " +
                                "no reason to disable this unless you're using incompatible mods.")
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useVertexArrayObjects = value, opts -> opts.advanced.useVertexArrayObjects)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Use Block Face Culling")
                        .setTooltip("If enabled, only the sides of blocks which are facing the camera will be submitted for rendering. This can eliminate " +
                                "a large number of block faces very early in the rendering process, saving memory bandwidth and time on the GPU. Some resource " +
                                "packs may have issues with this option, so try disabling it if you're seeing holes in blocks.")
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.advanced.useBlockFaceCulling = value, opts -> opts.advanced.useBlockFaceCulling)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Use Compact Vertex Format")
                        .setTooltip("If enabled, a more compact vertex format will be used for rendering chunks. This can reduce graphics memory usage and bandwidth " +
                                "requirements significantly, especially for integrated graphics cards, but can cause z-fighting with some resource packs due " +
                                "to how it reduces the precision of position and texture coordinate attributes.")
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useCompactVertexFormat = value, opts -> opts.advanced.useCompactVertexFormat)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Use Fog Occlusion")
                        .setTooltip("If enabled, chunks which are determined to be fully hidden by fog effects will not be rendered, helping to improve performance. The " +
                                "improvement can be more dramatic when fog effects are heavier (such as while underwater), but it may cause undesirable visual artifacts " +
                                "between the sky and fog in some scenarios.")
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useFogOcclusion = value, opts -> opts.advanced.useFogOcclusion)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Use Entity Culling")
                        .setTooltip("If enabled, entities determined not to be in any visible chunks will be skipped during rendering. This can help improve performance " +
                                "by avoiding the rendering of entities located underground or behind walls.")
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.advanced.useEntityCulling = value, opts -> opts.advanced.useEntityCulling)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_particle_culling.name"))
                        .setTooltip(I18n.format("sodium.options.use_particle_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useParticleCulling = value, opts -> opts.advanced.useParticleCulling)
                        .setImpact(OptionImpact.MEDIUM)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Animate Only Visible Textures")
                        .setTooltip("If enabled, only animated textures determined to be visible will be updated. This can provide a significant boost to frame " +
                                "rates on some hardware, especially with heavier resource packs. If you experience issues with some textures not being animated, " +
                                "try disabling this option.")
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.advanced.animateOnlyVisibleTextures = value, opts -> opts.advanced.animateOnlyVisibleTextures)
                        .setImpact(OptionImpact.MEDIUM)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.translucency_sorting.name"))
                        .setTooltip(I18n.format("sodium.options.translucency_sorting.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.translucencySorting = value, opts -> opts.advanced.translucencySorting)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.leaves_culling.name"))
                        .setTooltip(I18n.format("sodium.options.leaves_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useLeavesCulling = value, opts -> opts.advanced.useLeavesCulling)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Allow Direct Memory Access")
                        .setTooltip("If enabled, some critical code paths will be allowed to use direct memory access for performance. This " +
                                "often greatly reduces CPU overhead for chunk and entity rendering, but can make it harder to diagnose some " +
                                "bugs and crashes. You should only disable this if you've been asked to or otherwise know what you're doing.")
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setEnabled(UnsafeUtil.isSupported())
                        .setBinding((opts, value) -> opts.advanced.allowDirectMemoryAccess = value, opts -> opts.advanced.allowDirectMemoryAccess)
                        .build()
                )
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName("Ignore Driver Blacklist")
                        .setTooltip("If enabled, known incompatibilities with your hardware/driver configuration will be ignored, allowing you to enable options that " +
                                "may cause issues with your game. You should generally not touch this option unless you know exactly what you are doing. After changing " +
                                "this option, you must save, close, and then re-open the settings screen.")
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.ignoreDriverBlacklist = value, opts -> opts.advanced.ignoreDriverBlacklist)
                        .build()
                )
                .build());

        return new OptionPage("Advanced", ImmutableList.copyOf(groups));
    }
}
