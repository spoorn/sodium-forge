package me.jellysquid.mods.sodium.mixin.gen.fast_noise_interpolation;

import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.ImprovedNoiseGenerator;
import net.minecraft.world.gen.NoiseChunkGenerator;
import net.minecraft.world.gen.OctavesNoiseGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseChunkGenerator.class)
public class NoiseChunkGeneratorMixin {
    @Shadow
    @Final
    private OctavesNoiseGenerator minLimitPerlinNoise;

    @Shadow
    @Final
    private OctavesNoiseGenerator maxLimitPerlinNoise;

    @Shadow
    @Final
    private OctavesNoiseGenerator mainPerlinNoise;

    /**
     * @reason Smarter use of perlin noise that avoids unneeded sampling.
     * @author SuperCoder79
     */
    @Overwrite
    public double sampleAndClampNoise(int x, int y, int z, double horizontalScale, double verticalScale, double horizontalStretch, double verticalStretch) {
        // To generate it's terrain, Minecraft uses two different perlin noises.
        // It interpolates these two noises to create the final sample at a position.
        // However, the interpolation noise is not all that good and spends most of it's time at > 1 or < 0, rendering
        // one of the noises completely unnecessary in the process.
        // By taking advantage of that, we can reduce the sampling needed per block through the interpolation noise.

        // This controls both the frequency and amplitude of the noise.
        double frequency = 1.0;
        double interpolationValue = 0.0;

        // Calculate interpolation data to decide what noise to sample.
        for (int octave = 0; octave < 8; octave++) {
            double scaledVerticalScale = verticalStretch * frequency;
            double scaledY = y * scaledVerticalScale;

            interpolationValue += sampleOctave(this.mainPerlinNoise.getOctaveNoise(octave),
                    OctavesNoiseGenerator.wrap(x * horizontalStretch * frequency),
                    OctavesNoiseGenerator.wrap(scaledY),
                    OctavesNoiseGenerator.wrap(z * horizontalStretch * frequency), scaledVerticalScale, scaledY, frequency);

            frequency /= 2.0;
        }

        double clampedInterpolation = (interpolationValue / 10.0 + 1.0) / 2.0;

        if (clampedInterpolation >= 1) {
            // Sample only upper noise, as the lower noise will be interpolated out.
            frequency = 1.0;
            double noise = 0.0;
            for (int octave = 0; octave < 16; octave++) {
                double scaledVerticalScale = verticalScale * frequency;
                double scaledY = y * scaledVerticalScale;

                noise += sampleOctave(this.maxLimitPerlinNoise.getOctaveNoise(octave),
                        OctavesNoiseGenerator.wrap(x * horizontalScale * frequency),
                        OctavesNoiseGenerator.wrap(scaledY),
                        OctavesNoiseGenerator.wrap(z * horizontalScale * frequency), scaledVerticalScale, scaledY, frequency);

                frequency /= 2.0;
            }

            return noise / 512.0;
        } else if (clampedInterpolation <= 0) {
            // Sample only lower noise, as the upper noise will be interpolated out.
            frequency = 1.0;
            double noise = 0.0;
            for (int octave = 0; octave < 16; octave++) {
                double scaledVerticalScale = verticalScale * frequency;
                double scaledY = y * scaledVerticalScale;
                noise += sampleOctave(this.minLimitPerlinNoise.getOctaveNoise(octave),
                        OctavesNoiseGenerator.wrap(x * horizontalScale * frequency),
                        OctavesNoiseGenerator.wrap(scaledY),
                        OctavesNoiseGenerator.wrap(z * horizontalScale * frequency), scaledVerticalScale, scaledY, frequency);

                frequency /= 2.0;
            }

            return noise / 512.0;
        } else {
            // [VanillaCopy] SurfaceChunkGenerator#sampleNoise
            // Sample both and interpolate, as in vanilla.

            frequency = 1.0;
            double lowerNoise = 0.0;
            double upperNoise = 0.0;

            for (int octave = 0; octave < 16; octave++) {
                // Pre calculate these values to share them
                double scaledVerticalScale = verticalScale * frequency;
                double scaledY = y * scaledVerticalScale;
                double xVal = OctavesNoiseGenerator.wrap(x * horizontalScale * frequency);
                double yVal = OctavesNoiseGenerator.wrap(scaledY);
                double zVal = OctavesNoiseGenerator.wrap(z * horizontalScale * frequency);

                upperNoise += sampleOctave(this.maxLimitPerlinNoise.getOctaveNoise(octave), xVal, yVal, zVal, scaledVerticalScale, scaledY, frequency);
                lowerNoise += sampleOctave(this.minLimitPerlinNoise.getOctaveNoise(octave), xVal, yVal, zVal, scaledVerticalScale, scaledY, frequency);

                frequency /= 2.0;
            }

            // Vanilla behavior, return interpolated noise
            return MathHelper.lerp(clampedInterpolation, lowerNoise / 512.0, upperNoise / 512.0);
        }
    }

    private static double sampleOctave(ImprovedNoiseGenerator sampler, double x, double y, double z, double scaledVerticalScale, double scaledY, double frequency) {
        return sampler.noise(x, y, z, scaledVerticalScale, scaledY) / frequency;
    }
}