package com.flamingfrenchman.mcmd5;

import com.flamingfrenchman.mcmd5.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = Mcmd5.MODID, name = Mcmd5.NAME, version = Mcmd5.VERSION)
public class Mcmd5
{
    public static final String MODID = "mcmd5";
    public static final String NAME = "MCMD5";
    public static final String VERSION = "0.1";
    public static boolean debug = true;

    @SidedProxy(serverSide = "com.flamingfrenchman.mcollada.proxy.CommonProxy",
            clientSide = "com.flamingfrenchman.mcollada.proxy.ClientProxy")
    public static CommonProxy proxy;

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
