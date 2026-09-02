package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BBGameTests {
    public static void register(RegisterGameTestsEvent event) {
        event.register(BBGameTests.class);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cowCarcassAssembles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        Vec3 cowPos = cow.position();

        if (!CarcassAssembler.assemble(cow, null)) {
            helper.fail("Carcass assembly returned false");
        }
        cow.discard();

        helper.runAfterDelay(40, () -> {
            CarcassSavedData data = CarcassSavedData.get(level);
            if (data.all().size() != 1) {
                helper.fail("Expected 1 saved carcass, found " + data.all().size());
            }
            CarcassSavedData.Carcass carcass = data.all().iterator().next();
            if (carcass.bones.size() != 6) {
                helper.fail("Expected 6 cow bones, found " + carcass.bones.size());
            }
            if (carcass.joints.size() != 5) {
                helper.fail("Expected 5 cow joints, found " + carcass.joints.size());
            }
            for (PhysicsConstraintHandle handle : carcass.liveJoints) {
                if (!handle.isValid()) {
                    helper.fail("A joint handle went invalid");
                }
            }
            if (carcass.liveJoints.size() != 5) {
                helper.fail("Expected 5 live joints, found " + carcass.liveJoints.size());
            }

            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                helper.fail("No Sable container");
            }
            for (Map.Entry<String, UUID> bone : carcass.bones.entrySet()) {
                SubLevel subLevel = container.getSubLevel(bone.getValue());
                if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                    helper.fail("Bone " + bone.getKey() + " has no live sub-level");
                    return;
                }
                Vector3d pos = serverSubLevel.logicalPose().position();
                double distance = pos.distance(cowPos.x, cowPos.y, cowPos.z);
                if (distance > 4.0) {
                    helper.fail("Bone " + bone.getKey() + " ended up " + distance + " blocks from the cow at " + pos);
                }
                if (pos.y < cowPos.y - 2.0) {
                    helper.fail("Bone " + bone.getKey() + " fell through the floor to " + pos);
                }
            }
            helper.succeed();
        });
    }
}
