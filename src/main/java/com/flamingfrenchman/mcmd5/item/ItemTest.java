package com.flamingfrenchman.mcmd5.item;

import com.flamingfrenchman.mcmd5.Mcmd5;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import java.util.List;
import java.util.Random;

public class ItemTest extends Item {

    public ItemTest(String regName, String unlocalName) {
        setRegistryName(regName);
        setUnlocalizedName(Mcmd5.MODID + "." + unlocalName);
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        Mcmd5.logger.log(Level.INFO, "initializing model");
        ModelLoader.setCustomModelResourceLocation(this, 0,
                new ModelResourceLocation(getRegistryName() + ".md5mesh", "inventory"));
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn)
    {
        if(worldIn.isRemote) {
            IBakedModel model = Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getItemModel(playerIn.getHeldItem(handIn));
            List<BakedQuad> quads = model.getQuads(null, null, new Random().nextLong());
            for(BakedQuad quad : quads) {
                UnpackedBakedQuad unpackedBakedQuad = (UnpackedBakedQuad) quad;
                try {
                    float[][][] data = (float[][][]) Mcmd5.quadData.get(unpackedBakedQuad);
                    for(int v = 0 ; v < data.length ; ++v) {
                        Mcmd5.logger.log(Level.INFO, "Vertex number " + v);
                        for(int e = 0 ; e < data[v].length ; ++e) {
                            Mcmd5.logger.log(Level.INFO, "\tEntry number " + e);
                            for (int d = 0 ; d < data[v][e].length ; ++d) {
                                Mcmd5.logger.log(Level.INFO, "\t\t" + data[v][e][d]);
                            }
                        }
                    }
                }
                catch (IllegalAccessException e) {
                    Mcmd5.logger.log(Level.ERROR, "oof");
                }
            }
        }
        return new ActionResult<ItemStack>(EnumActionResult.PASS, playerIn.getHeldItem(handIn));
    }
}
