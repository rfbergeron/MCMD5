package com.flamingfrenchman.mcmd5;

import com.flamingfrenchman.mcmd5.proxy.CommonProxy;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Mod(modid = Mcmd5.MODID, name = Mcmd5.NAME, version = Mcmd5.VERSION)
public class Mcmd5
{
    public static final String MODID = "mcmd5";
    public static final String NAME = "MCMD5";
    public static final String VERSION = "0.1";
    public static boolean debug = true;

    @SidedProxy(serverSide = "com.flamingfrenchman.mcmd5.proxy.CommonProxy",
            clientSide = "com.flamingfrenchman.mcmd5.proxy.ClientProxy")
    public static CommonProxy proxy;

    public static Logger logger;
    public static Field quadData;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        proxy.preInit(event);
        try {
            quadData = UnpackedBakedQuad.class.getDeclaredField("unpackedData");
            quadData.setAccessible(true);
            logger.log(Level.INFO, "Field was set accessible");

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(quadData, quadData.getModifiers() & ~Modifier.FINAL);
        }
        catch (NoSuchFieldException e) {
            logger.log(Level.ERROR, "Could not find unpackedData field for class UnpackedBakedQuad");
        }
        catch (IllegalAccessException e) {
            logger.log(Level.ERROR, "Something went wrong with UnpackedBakedQuad");
        }
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
