package com.flamingfrenchman.mcmd5.proxy;

import com.flamingfrenchman.mcmd5.Mcmd5;
import com.flamingfrenchman.mcmd5.client.model.MD5Loader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Level;

public class ClientProxy extends CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModelLoaderRegistry.registerLoader(MD5Loader.INSTANCE);
        MD5Loader.INSTANCE.addDomain(Mcmd5.MODID);
    }

    public void init(FMLInitializationEvent event)
    {

    }

    public void addClientLog(Level level, String msg) {
        Mcmd5.logger.log(level, msg);
    }

    public void clientDebug(Level level, String msg) {
        if(Mcmd5.debug)
            Mcmd5.logger.log(level, msg);
    }
}
