package com.flamingfrenchman.mcmd5.item;

import com.flamingfrenchman.mcmd5.Mcmd5;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.jws.WebParam;
import java.util.List;
import java.util.Random;

public class ItemTest extends Item {

    public ItemTest(String regName, String unlocalName) {
        setRegistryName(regName);
        setUnlocalizedName(Mcmd5.MODID + "." + unlocalName);
    }

    @Override
    public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected)
    {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if(tagCompound != null) {
            int frame = tagCompound.getInteger("frame");
            if(frame > 119) frame = -1;
            tagCompound.setInteger("frame", frame + 1);
        }
        else {
            tagCompound = new NBTTagCompound();
            tagCompound.setInteger("frame", 0);
            stack.setTagCompound(tagCompound);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged)
    {
        return newStack.getItem().equals(oldStack.getItem());
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        Mcmd5.logger.log(Level.INFO, "initializing model");
        /*ModelLoader.setCustomModelResourceLocation(this, 0,
                new ModelResourceLocation(getRegistryName().toString() + ".md5mesh", null));*/
        /*ModelLoader.setCustomModelResourceLocation(this, 0,
                new ModelResourceLocation(getRegistryName().toString(), "inventory"));*/
        ModelLoader.setCustomModelResourceLocation(this, 0,
                new ModelResourceLocation("mcmd5:monster.md5mesh", null));
    }
}
