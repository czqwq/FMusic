package com.Lilith.FMusic.server.core.command;

import com.Lilith.FMusic.codec.HudType;

public abstract class AHudCommand extends ACommand {

    protected final HudType type;

    public AHudCommand(HudType type) {
        this.type = type;
    }
}
