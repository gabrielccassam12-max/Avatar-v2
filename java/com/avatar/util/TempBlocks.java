package com.avatar.util;

import com.avatar.core.AvatarConfig;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

/** Temporary terrain changes: everything bending builds or digs is put back after a while. */
public final class TempBlocks {
    private TempBlocks() {}

    /** How many blocks one technique may still change. */
    public static final class Budget {
        public int left;

        public Budget(int left) {
            this.left = left;
        }

        public static Budget make() {
            return new Budget(AvatarConfig.INSTANCE.maxBlocksPerCast);
        }
    }

    public static boolean allowed(ServerWorld w, BlockPos pos) {
        AvatarConfig c = AvatarConfig.INSTANCE;
        if (!c.modifyBlocks) return false;
        if (!w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return false;
        if (w.isOutOfHeightLimit(pos) || !w.isChunkLoaded(pos)) return false;
        int r = c.spawnProtectionRadius;
        if (r > 0) {
            BlockPos sp = w.getSpawnPos();
            if (Math.abs(pos.getX() - sp.getX()) <= r && Math.abs(pos.getZ() - sp.getZ()) <= r) return false;
        }
        return true;
    }

    /** Puts a block into free space (air / plants / snow layer). It is removed again after "ticks". */
    public static boolean place(ServerWorld w, Budget b, BlockPos pos, BlockState st, int ticks) {
        if (b.left <= 0 || !allowed(w, pos)) return false;
        BlockState old = w.getBlockState(pos);
        if (!(old.isAir() || old.isReplaceable())) return false;
        if (w.getBlockEntity(pos) != null) return false;
        w.setBlockState(pos, st);
        b.left--;
        int delay = Math.max(20, ticks > 0 ? ticks : AvatarConfig.INSTANCE.restoreTicks);
        Scheduler.later(delay, () -> {
            if (w.isChunkLoaded(pos) && w.getBlockState(pos).isOf(st.getBlock())) w.setBlockState(pos, old);
        });
        return true;
    }

    /** Digs a solid block out (never block entities or unbreakable blocks); it comes back after "ticks". */
    public static boolean dig(ServerWorld w, Budget b, BlockPos pos, int ticks) {
        if (b.left <= 0 || !AvatarConfig.INSTANCE.craters || !allowed(w, pos)) return false;
        BlockState old = w.getBlockState(pos);
        if (old.isAir() || !old.getFluidState().isEmpty()) return false;
        if (w.getBlockEntity(pos) != null || old.getHardness(w, pos) < 0) return false;
        w.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
        b.left--;
        int delay = Math.max(40, ticks > 0 ? ticks : AvatarConfig.INSTANCE.restoreTicks);
        Scheduler.later(delay, () -> {
            if (w.isChunkLoaded(pos) && w.getBlockState(pos).isAir()) w.setBlockState(pos, old);
        });
        return true;
    }

    /** Turns a solid ground block into another block for a while (lava veins, mud). Never touches block entities. */
    public static boolean convert(ServerWorld w, Budget b, BlockPos pos, BlockState st, int ticks) {
        if (b.left <= 0 || !allowed(w, pos)) return false;
        BlockState old = w.getBlockState(pos);
        if (old.isAir() || !old.getFluidState().isEmpty() || old.isOf(st.getBlock())) return false;
        if (w.getBlockEntity(pos) != null || old.getHardness(w, pos) < 0) return false;
        w.setBlockState(pos, st);
        b.left--;
        int delay = Math.max(40, ticks > 0 ? ticks : AvatarConfig.INSTANCE.restoreTicks);
        Scheduler.later(delay, () -> {
            if (w.isChunkLoaded(pos) && w.getBlockState(pos).isOf(st.getBlock())) w.setBlockState(pos, old);
        });
        return true;
    }
}
