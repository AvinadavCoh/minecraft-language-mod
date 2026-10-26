package dev.itonk.languagemod.test.mixin;

import dev.itonk.languagemod.test.ChallengeClientGameTest;
import net.minecraft.client.particle.CrackParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only: counts item-crack particles the client actually spawns. */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	@Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"))
	private void languagemodtest$count(Particle particle, CallbackInfo ci) {
		if (particle instanceof CrackParticle) ChallengeClientGameTest.crackParticles.incrementAndGet();
	}
}
