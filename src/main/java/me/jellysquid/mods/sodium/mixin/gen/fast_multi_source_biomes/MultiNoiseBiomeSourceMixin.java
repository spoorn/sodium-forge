package me.jellysquid.mods.sodium.mixin.gen.fast_multi_source_biomes;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.biome.provider.NetherBiomeProvider;
import net.minecraft.world.gen.MaxMinNoiseMixer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Supplier;

@Mixin(NetherBiomeProvider.class)
public class MultiNoiseBiomeSourceMixin {
    @Shadow
    @Final
    private boolean useY;

    @Shadow
    @Final
    private MaxMinNoiseMixer temperatureNoise;

    @Shadow
    @Final
    private MaxMinNoiseMixer humidityNoise;

    @Shadow
    @Final
    private MaxMinNoiseMixer weirdnessNoise;

    @Shadow
    @Final
    private MaxMinNoiseMixer altitudeNoise;

    @Shadow
    @Final
    private List<Pair<Biome.Attributes, Supplier<Biome>>> parameters;

    /**
     * @reason Remove stream based code in favor of regular collections.
     * @author SuperCoder79
     */
    @Overwrite
    public Biome getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        // [VanillaCopy] MultiNoiseBiomeSource#getBiomeForNoiseGen

        // Get the y value for perlin noise sampling. This field is always set to false in vanilla code.
        int y = this.useY ? biomeY : 0;

        // Calculate the noise point based using 4 perlin noise samplers.
        Biome.Attributes mixedNoisePoint = new Biome.Attributes(
                (float) this.temperatureNoise.getValue(biomeX, y, biomeZ),
                (float) this.humidityNoise.getValue(biomeX, y, biomeZ),
                (float) this.altitudeNoise.getValue(biomeX, y, biomeZ),
                (float) this.weirdnessNoise.getValue(biomeX, y, biomeZ),
                0.0F);

        int idx = -1;
        float min = Float.POSITIVE_INFINITY;

        // Iterate through the biome points and calculate the distance to the current noise point.
        for (int i = 0; i < this.parameters.size(); i++) {
            float distance = this.parameters.get(i).getFirst().fitness(mixedNoisePoint);

            // If the distance is less than the recorded minimum, update the minimum and set the current index.
            if (min > distance) {
                idx = i;
                min = distance;
            }
        }

        // Return the biome with the noise point closest to the evaluated one.
        return this.parameters.get(idx).getSecond().get() == null ? BiomeRegistry.THE_VOID : this.parameters.get(idx).getSecond().get();
    }
}
