package com.avicagan.bloodandbones.datagen;

import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Developer aid, off unless {@code -Dbloodandbones.dump_layers=minecraft:goat#main,...} is given to the data
 * run: writes each named model layer's part tree (pivots, rotations, cubes, and each cube's box in model
 * space) to build/layer-dump.txt, for writing rig targets without guessing.
 */
public class LayerDumpProvider implements DataProvider {
    public static final String PROPERTY = "bloodandbones.dump_layers";

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        String wanted = System.getProperty(PROPERTY);
        if (wanted == null || wanted.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<ModelLayerLocation, LayerDefinition> roots = LayerDefinitions.createRoots();
        StringBuilder out = new StringBuilder();
        for (String entry : wanted.split(",")) {
            String[] parts = entry.trim().split("#");
            ModelLayerLocation layer = new ModelLayerLocation(ResourceLocation.parse(parts[0]), parts.length > 1 ? parts[1] : "main");
            LayerDefinition definition = roots.get(layer);
            out.append("=== ").append(layer).append('\n');
            if (definition == null) {
                out.append("  (no such layer)\n");
                continue;
            }
            walk(definition.bakeRoot(), "", new Vector3f(), new Quaternionf(), out);
        }
        try {
            Path file = Path.of("build", "layer-dump.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the layer dump", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static void walk(ModelPart part, String path, Vector3f translation, Quaternionf rotation, StringBuilder out) {
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            ModelPart child = entry.getValue();
            String childPath = path.isEmpty() ? entry.getKey() : path + "/" + entry.getKey();
            PartPose pose = child.getInitialPose();
            Quaternionf local = new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot);
            Vector3f childTranslation = rotation.transform(new Vector3f(pose.x, pose.y, pose.z)).add(translation);
            Quaternionf childRotation = new Quaternionf(rotation).mul(local);
            out.append(String.format(Locale.ROOT, "%s  pivot(%.2f,%.2f,%.2f) rot(%.0f,%.0f,%.0f)deg model-pivot(%.2f,%.2f,%.2f)%n", childPath,
                    pose.x, pose.y, pose.z, Math.toDegrees(pose.xRot), Math.toDegrees(pose.yRot), Math.toDegrees(pose.zRot),
                    childTranslation.x, childTranslation.y, childTranslation.z));
            for (ModelPart.Cube cube : child.cubes) {
                Vector3f lo = new Vector3f(Float.MAX_VALUE);
                Vector3f hi = new Vector3f(-Float.MAX_VALUE);
                for (int i = 0; i < 8; i++) {
                    Vector3f c = new Vector3f((i & 1) == 0 ? cube.minX : cube.maxX, (i & 2) == 0 ? cube.minY : cube.maxY, (i & 4) == 0 ? cube.minZ : cube.maxZ);
                    childRotation.transform(c).add(childTranslation);
                    lo.min(c);
                    hi.max(c);
                }
                out.append(String.format(Locale.ROOT, "    cube local(%.2f,%.2f,%.2f)..(%.2f,%.2f,%.2f) size %.1fx%.1fx%.1f  model(%.1f,%.1f,%.1f)..(%.1f,%.1f,%.1f)%n",
                        cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ,
                        cube.maxX - cube.minX, cube.maxY - cube.minY, cube.maxZ - cube.minZ, lo.x, lo.y, lo.z, hi.x, hi.y, hi.z));
            }
            walk(child, childPath, childTranslation, childRotation, out);
        }
    }

    @Override
    public String getName() {
        return "Blood & Bones model layer dump";
    }
}
