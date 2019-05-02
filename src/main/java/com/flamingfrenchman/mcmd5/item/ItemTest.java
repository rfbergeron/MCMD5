package com.flamingfrenchman.mcmd5.item;

import com.flamingfrenchman.mcmd5.Mcmd5;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

public class ItemTest extends Item {

    public ItemTest(String regName, String unlocalName) {
        setRegistryName(regName);
        setUnlocalizedName(Mcmd5.MODID + "." + unlocalName);
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        Mcmd5.logger.log(Level.INFO, "initializing model");
        ModelLoader.setCustomModelResourceLocation(this, 0,
                new ModelResourceLocation(getRegistryName() + ".dae", "inventory"));
    }
}
