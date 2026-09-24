package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;

/** Temporary: many copies of the hunter's and hauler's tests at once. */
@GameTestHolder(BloodAndBones.MOD_ID)
public class TmpMergeRepro {
    @GameTestGenerator
    public static List<TestFunction> copies() {
        List<TestFunction> out = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            out.add(new TestFunction("huntrepro", "huntrepro_" + i, "bloodandbones:empty", 500, 0L, true, MinionJobTests::hunterWithMeatHookLeavesCarcass));
        }
        for (int i = 0; i < 40; i++) {
            out.add(new TestFunction("haulrepro", "haulrepro_" + i, "bloodandbones:empty", 1200, 0L, true, MinionJobTests::haulerLaysCarcassOnRack));
        }
        return out;
    }
}
