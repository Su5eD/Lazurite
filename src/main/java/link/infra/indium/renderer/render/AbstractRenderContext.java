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

package link.infra.indium.renderer.render;

import java.util.function.Consumer;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.infra.indium.other.SpriteFinderCache;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;

abstract class AbstractRenderContext implements RenderContext {
	private static final QuadTransform NO_TRANSFORM = q -> true;

	private QuadTransform activeTransform = NO_TRANSFORM;
	private final ObjectArrayList<QuadTransform> transformStack = new ObjectArrayList<>();
	private final QuadTransform stackTransform = q -> {
		int i = transformStack.size() - 1;

		while (i >= 0) {
			if (!transformStack.get(i--).transform(q)) {
				return false;
			}
		}

		return true;
	};

	@Deprecated
	private final Consumer<Mesh> meshConsumer = mesh -> mesh.outputTo(getEmitter());

	protected Matrix4f matrix;
	protected Matrix3f normalMatrix;
	protected int overlay;
	private final Vector4f posVec = new Vector4f();
	private final Vector3f normalVec = new Vector3f();

	protected final boolean transform(MutableQuadView q) {
		return activeTransform.transform(q);
	}

	@Override
	public boolean hasTransform() {
		return activeTransform != NO_TRANSFORM;
	}

	@Override
	public void pushTransform(QuadTransform transform) {
		if (transform == null) {
			throw new NullPointerException("Renderer received null QuadTransform.");
		}

		transformStack.push(transform);

		if (transformStack.size() == 1) {
			activeTransform = transform;
		} else if (transformStack.size() == 2) {
			activeTransform = stackTransform;
		}
	}

	@Override
	public void popTransform() {
		transformStack.pop();

		if (transformStack.size() == 0) {
			activeTransform = NO_TRANSFORM;
		} else if (transformStack.size() == 1) {
			activeTransform = transformStack.get(0);
		}
	}

	// Overridden to prevent allocating a lambda every time this method is called.
	@Deprecated
	@Override
	public Consumer<Mesh> meshConsumer() {
		return meshConsumer;
	}

	/** final output step. */
	protected void bufferQuad(MutableQuadViewImpl quad, VertexConsumer vertexConsumer) {
		final Vector4f posVec = this.posVec;
		final Vector3f normalVec = this.normalVec;
		final boolean useNormals = quad.hasVertexNormals();

		if (useNormals) {
			quad.populateMissingNormals();
		} else {
			normalVec.set(quad.faceNormal());
			normalVec.mul(normalMatrix);
		}

		// Use fast track for the default vanilla implementation
		if (vertexConsumer instanceof BufferBuilder) {
			for (int i = 0; i < 4; i++) {
				posVec.set(quad.x(i), quad.y(i), quad.z(i), 1.0f);
				posVec.mul(matrix);
				vertexConsumer.vertex(posVec.x(), posVec.y(), posVec.z());

				final int color = quad.color(i);
				vertexConsumer.color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF);
				vertexConsumer.texture(quad.u(i), quad.v(i));
				vertexConsumer.overlay(overlay);
				vertexConsumer.light(quad.lightmap(i));

				if (useNormals) {
					quad.copyNormal(i, normalVec);
					normalVec.mul(normalMatrix);
				}

				vertexConsumer.normal(normalVec.x(), normalVec.y(), normalVec.z());
				vertexConsumer.next();
			}
		}
		// Other implementations (namely Flywheel's ShadeSeparatingVertexConsumer) only support calling putBulkData
		// However, this results in a few additional operations made, so we only use it when necessary
		else {
			MatrixStack.Entry entry = new MatrixStack().peek();
			entry.getPositionMatrix().set(matrix);
			entry.getNormalMatrix().set(normalMatrix);
			BakedQuad bakedQuad = quad.toBakedQuad(null);
			vertexConsumer.putBulkData(
				entry,
				bakedQuad,
				1.0F, 1.0F, 1.0F, 1.0F,
				0,
				overlay,
				true
			);
		}

		Sprite sprite = quad.cachedSprite();

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad);
		}

		SpriteUtil.markSpriteActive(sprite);
	}
}
