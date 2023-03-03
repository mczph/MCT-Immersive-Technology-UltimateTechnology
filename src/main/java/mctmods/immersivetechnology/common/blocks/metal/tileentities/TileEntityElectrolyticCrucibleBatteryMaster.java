package mctmods.immersivetechnology.common.blocks.metal.tileentities;

import blusunrize.immersiveengineering.common.util.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mctmods.immersivetechnology.ImmersiveTechnology;
import mctmods.immersivetechnology.api.ITUtils;
import mctmods.immersivetechnology.api.crafting.ElectrolyticCrucibleBatteryRecipe;
import mctmods.immersivetechnology.common.Config;
import mctmods.immersivetechnology.common.blocks.metal.multiblocks.MultiblockElectrolyticCrucibleBattery;
import mctmods.immersivetechnology.common.util.ITFluidTank;
import mctmods.immersivetechnology.common.util.ITSounds;
import mctmods.immersivetechnology.common.util.multiblock.PoICache;
import mctmods.immersivetechnology.common.util.multiblock.PoIJSONSchema;
import mctmods.immersivetechnology.common.util.network.BinaryMessageTileSync;
import mctmods.immersivetechnology.common.util.network.IBinaryMessageReceiver;
import mctmods.immersivetechnology.common.util.network.MessageStopSound;
import mctmods.immersivetechnology.common.util.sound.ITSoundHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntityElectrolyticCrucibleBatteryMaster extends TileEntityElectrolyticCrucibleBatterySlave implements ITFluidTank.TankListener, IBinaryMessageReceiver {

    private static final int inputTankSize = Config.ITConfig.Machines.ElectrolyticCrucibleBattery.electrolyticCrucibleBattery_input_tankSize;
    private static final int outputTankSize = Config.ITConfig.Machines.ElectrolyticCrucibleBattery.electrolyticCrucibleBattery_output_tankSize;

    public FluidTank[] tanks = new FluidTank[] {
            new ITFluidTank(inputTankSize, this),
            new ITFluidTank(outputTankSize, this),
            new ITFluidTank(outputTankSize, this),
            new ITFluidTank(outputTankSize, this)
    };

    ElectrolyticCrucibleBatteryRecipe recipe;

    @Override
    public void readCustomNBT(@Nonnull NBTTagCompound nbt, boolean descPacket) {
        super.readCustomNBT(nbt, descPacket);
        tanks[0].readFromNBT(nbt.getCompoundTag("tank0"));
        tanks[1].readFromNBT(nbt.getCompoundTag("tank1"));
        tanks[2].readFromNBT(nbt.getCompoundTag("tank2"));
        tanks[3].readFromNBT(nbt.getCompoundTag("tank3"));
    }

    @Override
    public void writeCustomNBT(@Nonnull NBTTagCompound nbt, boolean descPacket) {
        super.writeCustomNBT(nbt, descPacket);
        nbt.setTag("tank0", tanks[0].writeToNBT(new NBTTagCompound()));
        nbt.setTag("tank1", tanks[1].writeToNBT(new NBTTagCompound()));
        nbt.setTag("tank2", tanks[2].writeToNBT(new NBTTagCompound()));
        nbt.setTag("tank3", tanks[3].writeToNBT(new NBTTagCompound()));
    }

    public void requestUpdate() {
        ByteBuf buffer = Unpooled.copyBoolean(true);
        BlockPos center = getPos();
        ImmersiveTechnology.packetHandler.sendToServer(new BinaryMessageTileSync(center, buffer));
    }

    public void notifyNearbyClients() {
        if (clientUpdateCooldown > 0) {
            notify = true;
            return;
        }
        clientUpdateCooldown = 20;
        ByteBuf buffer = Unpooled.copyBoolean(isRunning);
        BlockPos center = getPos();
        ImmersiveTechnology.packetHandler.sendToAllAround(new BinaryMessageTileSync(center, buffer), new NetworkRegistry.TargetPoint(world.provider.getDimension(), center.getX(), center.getY(), center.getZ(), 40));
    }

    @Override
    public void receiveMessageFromClient(ByteBuf message, EntityPlayerMP player) {
        ByteBuf buffer = Unpooled.copyBoolean(isRunning);
        BlockPos center = getPos();
        ImmersiveTechnology.packetHandler.sendTo(new BinaryMessageTileSync(center, buffer), player);
    }

    @Override
    public void receiveMessageFromServer(ByteBuf message) {
        isRunning = message.readBoolean();
    }

    private float soundVolume;
    private int clientUpdateCooldown = 20;
    private double distanceToTE = 0;
    private int playerDimension;
    private boolean isRunning;
    private boolean notify;

    public void handleSounds() {
        if(isRunning) {
            if(soundVolume < 1) soundVolume += 0.02f;
        } else {
            if(soundVolume > 0) soundVolume -= 0.02f;
        }
        if(soundVolume == 0) ITSoundHandler.StopSound(soundOrigin);
        else {
            float attenuation = Math.max((float) distanceToTE / 16f, 1);
            ITSounds.gasTurbineArc.PlayRepeating(soundOrigin, soundVolume / attenuation, 1);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onChunkUnload() {
        ITSoundHandler.StopSound(soundOrigin);
        super.onChunkUnload();
    }

    @Override
    public void disassemble() {
        ImmersiveTechnology.packetHandler.sendToAllTracking(new MessageStopSound(soundOrigin), new NetworkRegistry.TargetPoint(world.provider.getDimension(), soundOrigin.getX(), soundOrigin.getY(), soundOrigin.getZ(), 0));
        super.disassemble();
    }

    private void clientUpdate() {
        if(input0 == null) {
            InitializePoIs();
            requestUpdate();
        }
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        double currentDistance = player.getDistanceSq(soundOrigin.getX(), soundOrigin.getY(), soundOrigin.getZ());
        if (getWorld().provider.getDimension() == player.dimension && currentDistance < 400 &&
                (distanceToTE > 400 || playerDimension != player.dimension)) requestUpdate();
        distanceToTE = currentDistance;
        playerDimension = player.dimension;
        handleSounds();
    }

    private void serverUpdate() {
        pumpOutputOut();
        boolean update = false;
        if(processQueue.size() < this.getProcessQueueMaxLength()) {
            if(tanks[0].getFluidAmount() > 0) {
                recipe = ElectrolyticCrucibleBatteryRecipe.findRecipe(tanks[0].getFluid());
                if(recipe != null && tanks[1].fill(recipe.fluidOutput0, false) == recipe.fluidOutput0.amount &&
                        (recipe.fluidOutput1 == null || tanks[2].fill(recipe.fluidOutput1, false) == recipe.fluidOutput1.amount) &&
                            (recipe.fluidOutput2 == null || tanks[3].fill(recipe.fluidOutput2, false) == recipe.fluidOutput2.amount)) {
                    @SuppressWarnings("unchecked")
                    MultiblockProcessInMachine<ElectrolyticCrucibleBatteryRecipe> process =
                            new MultiblockProcessInMachine<>(recipe).setInputTanks(0);
                    if(this.addProcessToQueue(process, true)) {
                        this.addProcessToQueue(process, false);
                        update = true;
                    }
                }
            }
        }

        if (tickedProcesses > 0) {
            if (!isRunning) {
                isRunning = true;
                notifyNearbyClients();
            }
        } else if (isRunning) {
            isRunning = false;
            notifyNearbyClients();
        }

        if (clientUpdateCooldown > 0) clientUpdateCooldown--;
        if (notify) notifyNearbyClients();
        if (update) {
            efficientMarkDirty();
            this.markContainingBlockForUpdate(null);
        }
    }

    @Override
    public void update() {
        super.update();
        if(world.isRemote) {
            clientUpdate();
            return;
        }
        serverUpdate();
    }

    public void efficientMarkDirty() { // !!!!!!! only use it within update() function !!!!!!!
        world.getChunk(this.getPos()).markDirty();
    }

    @Override
    public void TankContentsChanged() {
        this.markContainingBlockForUpdate(null);
    }

    @Override
    public void onProcessFinish(@Nonnull MultiblockProcess<ElectrolyticCrucibleBatteryRecipe> process) {
        tanks[1].fill(process.recipe.fluidOutput0, true);
        if (process.recipe.fluidOutput1 != null) tanks[2].fill(process.recipe.fluidOutput1, true);
        if (process.recipe.fluidOutput2 != null) tanks[3].fill(process.recipe.fluidOutput2, true);

        //Item output
        if (process.recipe.itemOutput != null && !process.recipe.itemOutput.isEmpty()) {
            ItemStack output = null;
            TileEntity inventoryTile = this.world.getTileEntity(output3Front);
            if (inventoryTile != null)
                output = Utils.insertStackIntoInventory(inventoryTile, process.recipe.itemOutput.copy(), facing.getOpposite());
            if (output == null) Utils.dropStackAtPos(world, output3Front, process.recipe.itemOutput, facing);
        }
    }

    @Override
    public boolean isDummy() {
        return false;
    }

    @Override
    public TileEntityElectrolyticCrucibleBatteryMaster master() {
        master = this;
        return this;
    }

    private PoICache input0, output0, output1, output2, output3;
    private BlockPos soundOrigin, input0Front, output0Front, output1Front, output2Front, output3Front;
    private int redstonePos, energyPos0, energyPos1, energyPos2;

    private void InitializePoIs() {
        for(PoIJSONSchema poi : MultiblockElectrolyticCrucibleBattery.instance.pointsOfInterest) {
            switch (poi.name) {
                case "redstone":
                    redstonePos = poi.position;
                    break;
                case "energy0":
                    energyPos0 = poi.position;
                    break;
                case "energy1":
                    energyPos1 = poi.position;
                    break;
                case "energy2":
                    energyPos2 = poi.position;
                    break;
                case "input0":
                    input0 = new PoICache(facing, poi, mirrored);
                    input0Front = getBlockPosForPos(input0.position).offset(input0.facing);
                    break;
                case "output0":
                    output0 = new PoICache(facing, poi, mirrored);
                    output0Front = getBlockPosForPos(output0.position).offset(output0.facing);
                    break;
                case "output1":
                    output1 = new PoICache(facing, poi, mirrored);
                    output1Front = getBlockPosForPos(output1.position).offset(output1.facing);
                    break;
                case "output2":
                    output2 = new PoICache(facing, poi, mirrored);
                    output2Front = getBlockPosForPos(output2.position).offset(output2.facing);
                    break;
                case "output3":
                    output3 = new PoICache(facing, poi, mirrored);
                    output3Front = getBlockPosForPos(output3.position).offset(output3.facing);
                    break;
                case "sound":
                    soundOrigin = getBlockPosForPos(poi.position);
                    break;
            }
        }
    }

    public IFluidTank[] getAccessibleFluidTanks(EnumFacing side, int position) {
        if(input0 == null) InitializePoIs();
        if(side == null) return tanks;
        if(input0.isPoI(side, position)) return new FluidTank[] {tanks[0]};
        else if(output0.isPoI(side, position)) return new FluidTank[] {tanks[1]};
        else if(output1.isPoI(side, position)) return new FluidTank[] {tanks[2]};
        else if(output2.isPoI(side, position)) return new FluidTank[] {tanks[3]};
        return ITUtils.emptyIFluidTankList;
    }

    public boolean canFillTankFrom(int iTank, EnumFacing side, FluidStack resource, int position) {
        if(input0 == null) InitializePoIs();
        if(input0.isPoI(side, position)) {
            if(tanks[0].getFluidAmount() >= tanks[0].getCapacity()) return false;
            if(tanks[0].getFluid() == null) return ElectrolyticCrucibleBatteryRecipe.findRecipeFluid(resource.getFluid()) != null;
            else return resource.getFluid() == tanks[0].getFluid().getFluid();
        }
        return false;
    }

    public boolean canDrainTankFrom(int iTank, EnumFacing side, int position) {
        if(input0 == null) InitializePoIs();
        if(output0.isPoI(side, position)) {
            return tanks[1].getFluidAmount() > 0;
        } else if(output1.isPoI(side, position)) {
            return tanks[2].getFluidAmount() > 0;
        } else if(output2.isPoI(side, position)) {
            return tanks[3].getFluidAmount() > 0;
        }
        return false;
    }

    private void pumpOutputOut() {
        if(input0 == null) InitializePoIs();
        IFluidHandler output;
        if(tanks[1].getFluidAmount() > 0 && (output = FluidUtil.getFluidHandler(world, output0Front, output0.facing.getOpposite())) != null) {
            FluidStack out = tanks[1].getFluid();
            int accepted = output.fill(out, false);
            if(accepted == 0) return;
            int drained = output.fill(Utils.copyFluidStackWithAmount(out, Math.min(out.amount, accepted), false), true);
            this.tanks[1].drain(drained, true);
        }
        if(tanks[2].getFluidAmount() > 0 && (output = FluidUtil.getFluidHandler(world, output1Front, output1.facing.getOpposite())) != null) {
            FluidStack out = tanks[2].getFluid();
            int accepted = output.fill(out, false);
            if(accepted == 0) return;
            int drained = output.fill(Utils.copyFluidStackWithAmount(out, Math.min(out.amount, accepted), false), true);
            this.tanks[2].drain(drained, true);
        }
        if(tanks[3].getFluidAmount() > 0 && (output = FluidUtil.getFluidHandler(world, output2Front, output2.facing.getOpposite())) != null) {
            FluidStack out = tanks[3].getFluid();
            int accepted = output.fill(out, false);
            if(accepted == 0) return;
            int drained = output.fill(Utils.copyFluidStackWithAmount(out, Math.min(out.amount, accepted), false), true);
            this.tanks[3].drain(drained, true);
        }
    }

    @Nonnull
    @Override
    public int[] getRedstonePos() {
        if(input0 == null) InitializePoIs();
        return new int[] { redstonePos };
    }

    @Nonnull
    @Override
    public int[] getEnergyPos() {
        if(input0 == null) InitializePoIs();
        return new int[] { energyPos0, energyPos1, energyPos2 };
    }
}
