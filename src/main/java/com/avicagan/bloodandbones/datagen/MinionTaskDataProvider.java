package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.minion.MinionDisposition;
import com.avicagan.bloodandbones.minion.MinionTask;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The minion tasks' and the heads' dispositions' files (docs/NEXT.md 1.6), written from their code defaults: a file per task
 * ({@code minion_task/<task>.json}) and per disposition ({@code minion_disposition/<name>.json}), so a datapack sees what
 * there is to retune and a missing file changes nothing.
 */
public class MinionTaskDataProvider implements DataProvider {
    private final PackOutput output;

    public MinionTaskDataProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Path root = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(BloodAndBones.MOD_ID);
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (MinionTask task : MinionTask.values()) {
            futures.add(DataProvider.saveStable(cache, task.defaults().toJson(), root.resolve("minion_task").resolve(task.id.getPath() + ".json")));
        }
        for (Map.Entry<String, MinionDisposition> e : MinionDisposition.DEFAULTS.entrySet()) {
            futures.add(DataProvider.saveStable(cache, e.getValue().toJson(), root.resolve("minion_disposition").resolve(e.getKey() + ".json")));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Blood & Bones minion tasks and dispositions";
    }
}
