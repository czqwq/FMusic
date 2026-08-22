package com.Lilith.FMusic.server.core.command;

import java.util.List;

public interface ICommand {

    void execute(Object sender, String name, String[] args);

    List<String> tab(Object sender, String name, String[] args, int index);
}
