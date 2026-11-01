package com.avatar.server;

import com.avatar.core.Element;
import com.avatar.core.Skill;
import com.avatar.util.Effects;

/** Per-player runtime state (not saved). */
public final class PState {
    public final long[] cd = new long[Skill.values().length];
    public final long[] pressAt = new long[Skill.values().length];
    public final boolean[] holding = new boolean[Skill.values().length];
    public Element active;
    public boolean avatarOn;
    public int sel;
    // timed defences
    public long waterShieldUntil, airShieldUntil, rockArmorUntil, iceArmorUntil, shadowUntil, etherealUntil;
    public Effects.Handle sustained;       // scooter / flight / levitation
    public boolean flying, grantedFlight;
    // spirit projection
    public long spiritUntil;
    public net.minecraft.util.math.Vec3d spiritOrigin;
    // combo chain
    public int combo;
    public long lastCast;
    // collapse
    public boolean collapsing;
    public long collapseStart;
    public int lastSentHash = Integer.MIN_VALUE;
}
