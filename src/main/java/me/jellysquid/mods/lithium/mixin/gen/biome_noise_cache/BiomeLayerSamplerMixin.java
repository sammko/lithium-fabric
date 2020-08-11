package me.jellysquid.mods.lithium.mixin.gen.biome_noise_cache;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.layer.util.CachingLayerSampler;
import net.minecraft.world.biome.layer.util.LayerFactory;
import net.minecraft.world.biome.source.BiomeLayerSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeLayerSampler.class)
public abstract class BiomeLayerSamplerMixin {
    private ThreadLocal<CachingLayerSampler> tlSampler;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(LayerFactory<CachingLayerSampler> factory) {
        this.tlSampler = ThreadLocal.withInitial(factory::make);
    }

    @Redirect(method = "sample", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/layer/util/CachingLayerSampler;sample(II)I"))
    public int sample(int x, int y) {
        return this.tlSampler.get().sample(x, y);
    }
}
