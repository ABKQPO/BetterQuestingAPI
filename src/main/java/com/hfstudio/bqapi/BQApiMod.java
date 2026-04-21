package com.hfstudio.bqapi;

import com.hfstudio.bqapi.command.BQApiCommand;
import com.hfstudio.bqapi.runtime.BQReinjector;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = BQApiMod.MODID,
    name = "HFStudio BetterQuesting Runtime API",
    version = BQApiMod.VERSION,
    dependencies = "after:betterquesting;",
    acceptedMinecraftVersions = "1.7.10")
public class BQApiMod {

    public static final String MODID = Tags.MODID;
    public static final String VERSION = Tags.VERSION;

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BQApiCommand());
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        BQReinjector.reinject(
            FMLCommonHandler.instance()
                .getMinecraftServerInstance());
    }
}
