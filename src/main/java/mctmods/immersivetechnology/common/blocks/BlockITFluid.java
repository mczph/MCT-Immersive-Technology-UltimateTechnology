package mctmods.immersivetechnology.common.blocks;

import mctmods.immersivetechnology.ImmersiveTechnology;
import mctmods.immersivetechnology.common.ITContent;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

	/*
	@author BluSunrize
	*/
public class BlockITFluid extends BlockFluidClassic {
	private int flammability = 0;
	private int fireSpread = 0;
	private PotionEffect[] potionEffects;

	public BlockITFluid(String name, Fluid fluid, Material material) {
		super(fluid, material);
		this.setTranslationKey(ImmersiveTechnology.MODID + "." + name);
		this.setCreativeTab(ImmersiveTechnology.creativeTab);
		ITContent.registeredITBlocks.add(this);
	}

	public BlockITFluid setFlammability(int flammability, int fireSpread) {
		this.flammability = flammability;
		this.fireSpread = fireSpread;
		return this;
	}

	public BlockITFluid setPotionEffects(PotionEffect... potionEffects) {
		this.potionEffects = potionEffects;
		return this;
	}

	@Override
	public int getFlammability(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return this.flammability;
	}

	@Override
	public int getFireSpreadSpeed(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return fireSpread;
	}

	@Override
	public boolean isFlammable(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return this.flammability > 0;
	}

	@Override
	public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
		if(potionEffects != null && entity instanceof EntityLivingBase) for(PotionEffect effect : potionEffects) if(effect != null) ((EntityLivingBase) entity).addPotionEffect(new PotionEffect(effect));
	}

}