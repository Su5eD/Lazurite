/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package link.infra.indium.mixin.renderer;

import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import link.infra.indium.renderer.accessor.AccessItemRenderer;
import link.infra.indium.renderer.render.ItemRenderContext;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer implements AccessItemRenderer {
	@Shadow @Final
	private ItemColors colors;

	@Unique
	private final ThreadLocal<ItemRenderContext> indium_contexts = ThreadLocal.withInitial(() -> new ItemRenderContext(colors));

	@Shadow
	protected abstract void renderBakedItemModel(BakedModel model, ItemStack stack, int light, int overlay, MatrixStack matrixStack, VertexConsumer buffer);

	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/BakedModel;isBuiltin()Z"), method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V", cancellable = true)
	public void hook_renderItem(ItemStack stack, ModelTransformationMode transformMode, boolean invert, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, int overlay, BakedModel model, CallbackInfo ci) {
		if (!((FabricBakedModel) model).isVanillaAdapter()) {
			indium_contexts.get().renderModel(stack, transformMode, invert, matrixStack, vertexConsumerProvider, light, overlay, model);
			matrixStack.pop();
			ci.cancel();
		}
	}

	@Override
	public void indium$renderBakedItemModel(BakedModel model, ItemStack stack, int light, int overlay, MatrixStack matrixStack, VertexConsumer buffer) {
		renderBakedItemModel(model, stack, light, overlay, matrixStack, buffer);
	}
}
