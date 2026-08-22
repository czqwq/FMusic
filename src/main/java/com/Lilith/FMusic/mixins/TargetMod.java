package com.Lilith.FMusic.mixins;

@SuppressWarnings({ "unused", "SpellCheckingInspection" })
public enum TargetMod {
    ;

    private final String modId;
    public final String modName;

    TargetMod(String modName, String modId) {
        this.modName = modName;
        this.modId = modId;
    }

    public String getModId() {
        return modId;
    }

    public String getModName() {
        return modName;
    }
}
