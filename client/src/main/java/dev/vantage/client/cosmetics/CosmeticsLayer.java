package dev.vantage.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vantage.client.cosmetics.PlayerCosmeticsModule.Hat;
import dev.vantage.client.cosmetics.PlayerCosmeticsModule.Pet;
import dev.vantage.client.cosmetics.PlayerCosmeticsModule.Side;
import net.minecraft.client.model.animal.bee.BeeModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the hat and the bee pet on your own player. Capes and parrots need no layer of their own: they go into
 * the render state and vanilla's cape and parrot layers draw them (see {@link PlayerCosmeticsModule#apply}).
 */
public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier BEE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/bee/bee.png");

	private final BeeModel bee;

	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
		super(parent);
		this.bee = new BeeModel(models.bakeLayer(ModelLayers.BEE));
	}

	@Override
	public void submit(PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		PlayerCosmeticsModule cosmetics = PlayerCosmeticsModule.activeFor(state);
		if (cosmetics == null || state.isInvisible) {
			return;
		}
		submitHat(cosmetics, poses, collector, light, state);
		if (cosmetics.pet.get() == Pet.BEE) {
			Side side = cosmetics.petSide.get();
			if (side.left()) {
				submitBee(poses, collector, light, state, true);
			}
			if (side.right()) {
				submitBee(poses, collector, light, state, false);
			}
		}
	}

	private void submitHat(PlayerCosmeticsModule cosmetics, PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState state) {
		Hat hat = cosmetics.hat.get();
		if (hat == Hat.NONE || (cosmetics.hideHatUnderHelmet.enabled() && !state.headEquipment.isEmpty())) {
			return;
		}
		poses.pushPose();
		PlayerModel model = getParentModel();
		model.root().translateAndRotate(poses);
		model.translateToHead(poses);
		if (hat == Hat.HALO) {
			poses.translate(0, Mth.sin(state.ageInTicks * 0.08f) * 0.03f, 0);
		}
		RenderType type = RenderTypes.entityCutoutNoCull(CosmeticTextures.white());
		int accent = cosmetics.hatColor.color();
		for (HatModels.Piece piece : HatModels.pieces(hat)) {
			collector.submitModelPart(piece.part(), poses, type, piece.glow() ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY,
				null, false, false, piece.color(accent), null, state.outlineColor);
		}
		poses.popPose();
	}

	/** A small bee hovering over the shoulder, placed like vanilla's shoulder parrot. */
	private void submitBee(PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState state, boolean left) {
		poses.pushPose();
		poses.translate(left ? 0.4f : -0.4f, state.isCrouching ? 0.2f : 0f, 0f);
		poses.scale(0.5f, 0.5f, 0.5f);
		poses.translate(0f, -1.45f, 0f);
		// A new state per call: submitted models are drawn later in the frame, so a shared state would be overwritten.
		BeeRenderState beeState = new BeeRenderState();
		beeState.ageInTicks = state.ageInTicks + (left ? 0 : 7);
		beeState.isOnGround = false;
		beeState.isAngry = false;
		beeState.hasNectar = false;
		beeState.hasStinger = true;
		beeState.rollAmount = 0;
		collector.submitModel(bee, beeState, poses, bee.renderType(BEE_TEXTURE), light, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poses.popPose();
	}
}
