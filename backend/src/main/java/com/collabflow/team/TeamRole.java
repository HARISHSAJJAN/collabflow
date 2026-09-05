package com.collabflow.team;

/**
 * Declared in enum-definition order from most to least privileged, which lets
 * {@link #satisfies} use ordinal comparison instead of a hand-written rank table.
 */
public enum TeamRole {
    OWNER,
    ADMIN,
    MEMBER;

    /** True if this role's privileges are at least as broad as {@code minimumRequired}'s. */
    public boolean satisfies(TeamRole minimumRequired) {
        return this.ordinal() <= minimumRequired.ordinal();
    }
}
