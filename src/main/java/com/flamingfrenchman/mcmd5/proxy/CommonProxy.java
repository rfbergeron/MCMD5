package com.flamingfrenchman.mcmd5.proxy;

import com.flamingfrenchman.mcmd5.Mcmd5;
import com.flamingfrenchman.mcmd5.item.ItemTest;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.Level;

@Mod.EventBusSubscriber
public class CommonProxy {

    @GameRegistry.ObjectHolder(Mcmd5.MODID + ":monster")
    public static ItemTest TEST = new ItemTest("monster", "monster");

    public void preInit(FMLPreInitializationEvent event) {

    }

    public void init(FMLInitializationEvent event) {

    }

    public void postInit(FMLPostInitializationEvent event) {

    }

    //message only prints on client if bothSides is true
    public void addLog(Level level, String msg, boolean bothSides) {
        if(bothSides || !(this instanceof ClientProxy) )
            Mcmd5.logger.log(level, msg);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(TEST);
    }

    @SubscribeEvent
    public static void regsiterModels(ModelRegistryEvent event) {
        TEST.initModel();
    }
}
