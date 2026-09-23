package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryManager;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryTable;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.network.ButcherySyncPayload;
import com.avicagan.bloodandbones.network.RigSyncPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.Map;

/**
 * A single-player world hands packets across without ever turning them into bytes, so a codec that loses
 * something only shows on a real server. These write each synced packet out and read it back.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class NetworkTests {
    private static <T> T roundTrip(GameTestHelper helper, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        codec.encode(buf, value);
        T back = codec.decode(buf);
        if (buf.readableBytes() != 0) {
            helper.fail(buf.readableBytes() + " bytes left over after reading a packet back");
        }
        return back;
    }

    /** Compared as JSON, which is how the files were written. */
    private static <T> void same(GameTestHelper helper, Codec<T> codec, ResourceLocation id, T sent, T got) {
        var a = codec.encodeStart(JsonOps.INSTANCE, sent).getOrThrow();
        var b = got == null ? null : codec.encodeStart(JsonOps.INSTANCE, got).getOrThrow();
        if (!a.equals(b)) {
            helper.fail(id + " came back different:\n sent " + a + "\n got  " + b);
        }
    }

    /** Every rig, one per packet as the server sends them, comes back whole. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rigsSurviveTheNetwork(GameTestHelper helper) {
        Map<ResourceLocation, Rig> rigs = RigManager.all();
        if (rigs.isEmpty()) {
            helper.fail("No rigs loaded");
        }
        rigs.forEach((id, rig) -> {
            RigSyncPayload back = roundTrip(helper, RigSyncPayload.STREAM_CODEC, new RigSyncPayload(Map.of(id, rig)));
            same(helper, Rig.CODEC, id, rig, back.rigs().get(id));
        });
        helper.succeed();
    }

    /** The butchery tables, all in one packet, come back whole and fit in a packet with room to spare. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void butcheryTablesSurviveTheNetwork(GameTestHelper helper) {
        Map<ResourceLocation, ButcheryTable> tables = ButcheryManager.all();
        if (tables.isEmpty()) {
            helper.fail("No butchery tables loaded");
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        ButcherySyncPayload.STREAM_CODEC.encode(buf, new ButcherySyncPayload(tables));
        int size = buf.readableBytes();
        BloodAndBones.LOGGER.info("[network] butchery tables: {} tables in {} bytes", tables.size(), size);
        // a server-to-client custom packet may be up to 1 MiB; stay well clear of it
        if (size > 256 * 1024) {
            helper.fail("The butchery tables take " + size + " bytes; split them over several packets");
        }
        ButcherySyncPayload back = roundTrip(helper, ButcherySyncPayload.STREAM_CODEC, new ButcherySyncPayload(tables));
        if (back.tables().size() != tables.size()) {
            helper.fail("Sent " + tables.size() + " tables, got " + back.tables().size());
        }
        tables.forEach((id, table) -> same(helper, ButcheryTable.CODEC, id, table, back.tables().get(id)));
        helper.succeed();
    }
}
