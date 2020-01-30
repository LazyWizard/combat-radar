package org.lazywizard.radar.commands;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import org.lazywizard.radar.RadarSettings;

public class ReloadRadar implements BaseCommand
{
    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        try
        {
            RadarSettings.reloadSettings();
            Console.showMessage("Reloaded radar settings. Changes will take effect"
                    + " after loading a save, or the next battle for the combat radar.");
            return CommandResult.SUCCESS;
        }
        catch (Exception ex)
        {
            Console.showException("Failed to reload radar settings: ", ex);
            return CommandResult.ERROR;
        }
    }
}
