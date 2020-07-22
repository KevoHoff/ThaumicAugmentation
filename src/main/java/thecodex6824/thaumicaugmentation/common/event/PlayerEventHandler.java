/**
 *  Thaumic Augmentation
 *  Copyright (c) 2019 TheCodex6824.
 *
 *  This file is part of Thaumic Augmentation.
 *
 *  Thaumic Augmentation is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Thaumic Augmentation is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Thaumic Augmentation.  If not, see <https://www.gnu.org/licenses/>.
 */

package thecodex6824.thaumicaugmentation.common.event;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import baubles.api.BaubleType;
import baubles.api.cap.BaublesCapabilities;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thecodex6824.thaumicaugmentation.api.TAConfig;
import thecodex6824.thaumicaugmentation.api.TAItems;
import thecodex6824.thaumicaugmentation.api.ThaumicAugmentationAPI;
import thecodex6824.thaumicaugmentation.api.augment.CapabilityAugmentableItem;
import thecodex6824.thaumicaugmentation.api.augment.IAugmentableItem;
import thecodex6824.thaumicaugmentation.api.entity.DamageSourceImpetus;
import thecodex6824.thaumicaugmentation.api.entity.PlayerMovementAbilityManager;
import thecodex6824.thaumicaugmentation.api.impetus.CapabilityImpetusStorage;
import thecodex6824.thaumicaugmentation.api.impetus.IImpetusStorage;
import thecodex6824.thaumicaugmentation.api.impetus.ImpetusAPI;
import thecodex6824.thaumicaugmentation.api.item.IArmorReduceFallDamage;
import thecodex6824.thaumicaugmentation.api.ward.storage.CapabilityWardStorage;
import thecodex6824.thaumicaugmentation.api.ward.storage.IWardStorage;
import thecodex6824.thaumicaugmentation.api.ward.storage.IWardStorageServer;
import thecodex6824.thaumicaugmentation.api.world.TADimensions;
import thecodex6824.thaumicaugmentation.common.TAConfigHolder;
import thecodex6824.thaumicaugmentation.common.network.PacketBoostState;
import thecodex6824.thaumicaugmentation.common.network.PacketFlightState;
import thecodex6824.thaumicaugmentation.common.network.TANetwork;
import thecodex6824.thaumicaugmentation.common.world.ChunkGeneratorEmptiness;
import thecodex6824.thaumicaugmentation.common.world.structure.MapGenEldritchSpire;

@EventBusSubscriber(modid = ThaumicAugmentationAPI.MODID)
public final class PlayerEventHandler {

    private static final WeakHashMap<Entity, Float> FALL_DAMAGE = new WeakHashMap<>();
    private static final Set<EntityPlayer> CREATIVE_FLIGHT = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<EntityPlayer> ELYTRA_BOOSTS = Collections.newSetFromMap(new WeakHashMap<>());
    
    private PlayerEventHandler() {}
    
    @SubscribeEvent
    @SuppressWarnings("null")
    public static void onJoin(PlayerLoggedInEvent event) {
        TAConfigHolder.loadOrSyncConfig(event.player);
        if (!ThaumcraftCapabilities.knowsResearchStrict(event.player, "THAUMIC_AUGMENTATION_BASE@1") &&
                (ThaumcraftCapabilities.knowsResearch(event.player, "FIRSTSTEPS") || ThaumcraftCapabilities.knowsResearch(event.player, "~FIRSTSTEPS"))) {
    
            ThaumcraftCapabilities.getKnowledge(event.player).addResearch("THAUMIC_AUGMENTATION_BASE");
            ThaumcraftCapabilities.getKnowledge(event.player).setResearchFlag("THAUMIC_AUGMENTATION_BASE", EnumResearchFlag.RESEARCH);
            ThaumcraftCapabilities.getKnowledge(event.player).setResearchStage("THAUMIC_AUGMENTATION_BASE", 2);
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof EntityPlayer && PlayerMovementAbilityManager.isValidSideForMovement((EntityPlayer) event.getEntity()))
            PlayerMovementAbilityManager.onJump((EntityPlayer) event.getEntity());
    }

    protected static void checkFrequent(EntityPlayer player) {
        WorldServer w = (WorldServer) player.getEntityWorld();
        if (w.getChunkProvider().isInsideStructure(w, "EldritchSpire", player.getPosition())) {
            if (!ThaumcraftCapabilities.knowsResearchStrict(player, "m_ENTERSPIRE")) {
                ThaumcraftCapabilities.getKnowledge(player).addResearch("m_ENTERSPIRE");
                player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.entered_spire").setStyle(
                        new Style().setColor(TextFormatting.DARK_PURPLE)), true);
            }
            
            if (player.capabilities.isFlying && !player.isCreative() && !player.isSpectator()) {
                MapGenEldritchSpire.Start start = ((ChunkGeneratorEmptiness) w.getChunkProvider().chunkGenerator).getSpireStart(player.getPosition());
                IWardStorage storage = w.getChunk(player.getPosition()).getCapability(CapabilityWardStorage.WARD_STORAGE, null);
                if (storage instanceof IWardStorageServer && ((IWardStorageServer) storage).isWardOwner(start.getWard())) {
                    player.capabilities.isFlying = false;
                    player.sendPlayerAbilities();
                    player.sendStatusMessage(new TextComponentTranslation("tc.break.fly").setStyle(
                            new Style().setColor(TextFormatting.DARK_PURPLE)), true);
                }
            }
        }
    }
    
    protected static void checkResearch(EntityPlayer player) {
        if (!TAConfig.disableEmptiness.getValue() && player.getEntityWorld().provider.getDimension() == TADimensions.EMPTINESS.getId() &&
                !ThaumcraftCapabilities.knowsResearchStrict(player, "m_ENTERVOID")) {
            
            ThaumcraftCapabilities.getKnowledge(player).addResearch("m_ENTERVOID");
            player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.entered_void").setStyle(
                    new Style().setColor(TextFormatting.DARK_PURPLE)), true);
        }
        
        Biome biome = player.getEntityWorld().getBiome(player.getPosition());
        if (BiomeDictionary.hasType(biome, Type.OCEAN) && !ThaumcraftCapabilities.knowsResearchStrict(player, "m_OCEAN")) {
            ThaumcraftCapabilities.getKnowledge(player).addResearch("m_OCEAN");
            player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.ocean").setStyle(
                    new Style().setColor(TextFormatting.DARK_PURPLE)), true);
        }
        
        if (BiomeDictionary.hasType(biome, Type.MOUNTAIN) && !ThaumcraftCapabilities.knowsResearchStrict(player, "m_MOUNTAIN")) {
            ThaumcraftCapabilities.getKnowledge(player).addResearch("m_MOUNTAIN");
            player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.mountain").setStyle(
                    new Style().setColor(TextFormatting.DARK_PURPLE)), true);
        }
        
        if (BiomeDictionary.hasType(biome, Type.SANDY) && BiomeDictionary.hasType(biome, Type.HOT) && !ThaumcraftCapabilities.knowsResearchStrict(player, "m_DESERT")) {
            ThaumcraftCapabilities.getKnowledge(player).addResearch("m_DESERT");
            player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.desert").setStyle(
                    new Style().setColor(TextFormatting.DARK_PURPLE)), true);
        }
        
        if (BiomeDictionary.hasType(biome, Type.FOREST) && !ThaumcraftCapabilities.knowsResearchStrict(player, "m_FOREST")) {
            ThaumcraftCapabilities.getKnowledge(player).addResearch("m_FOREST");
            player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.forest").setStyle(
                    new Style().setColor(TextFormatting.DARK_PURPLE)), true);
        }
        
        StatisticsManager stats = player.getServer().getPlayerList().getPlayerStatsFile(player);
        if (stats.readStat(StatList.AVIATE_ONE_CM) > 199999 && !ThaumcraftCapabilities.knowsResearchStrict(player, "m_ELYTRAFLY")) {
            ThaumcraftCapabilities.getKnowledge(player).addResearch("m_ELYTRAFLY");
            player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.elytra_fly").setStyle(
                    new Style().setColor(TextFormatting.DARK_PURPLE)), true);
        }
        
        if (stats.readStat(StatList.DIVE_ONE_CM) > 7999 && !ThaumcraftCapabilities.knowsResearchStrict(player, "m_LONGTIMEINWATER")) {
            ThaumcraftCapabilities.getKnowledge(player).addResearch("m_LONGTIMEINWATER");
            player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.long_time_in_water").setStyle(
                    new Style().setColor(TextFormatting.DARK_PURPLE)), true);
        }
        
        if (player.getActivePotionEffect(MobEffects.LEVITATION) != null && !ThaumcraftCapabilities.knowsResearchStrict(player, "m_LEVITATE")) {
            ThaumcraftCapabilities.getKnowledge(player).addResearch("m_LEVITATE");
            player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.levitate").setStyle(
                    new Style().setColor(TextFormatting.DARK_PURPLE)), true);
        }
    }
    
    public static boolean getBoostState(EntityPlayer player) {
        return ELYTRA_BOOSTS.contains(player);
    }
    
    public static void updateBoostState(EntityPlayer player, boolean boost) {
        if (boost)
            ELYTRA_BOOSTS.add(player);
        else
            ELYTRA_BOOSTS.remove(player);
    }
    
    public static boolean playerCanBoost(EntityPlayer player) {
        boolean canKeepFlying = false;
        IBaublesItemHandler baubles = player.getCapability(BaublesCapabilities.CAPABILITY_BAUBLES, null);
        if (baubles != null) {
            for (int slot : BaubleType.BODY.getValidSlots()) {
                ItemStack body = baubles.getStackInSlot(slot);
                if (body.getItem() == TAItems.ELYTRA_HARNESS) {
                    IAugmentableItem augmentable = body.getCapability(CapabilityAugmentableItem.AUGMENTABLE_ITEM, null);
                    if (augmentable != null) {
                        for (ItemStack aug : augmentable.getAllAugments()) {
                            if (aug.getItem() == TAItems.ELYTRA_HARNESS_AUGMENT && aug.getMetadata() == 0) {
                                IImpetusStorage impetus = aug.getCapability(CapabilityImpetusStorage.IMPETUS_STORAGE, null);
                                if (impetus != null && ImpetusAPI.tryExtractFully(impetus, 1, player)) {
                                    canKeepFlying = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                
                if (canKeepFlying)
                    break;
            }
        }
        
        return canKeepFlying;
    }
    
    @SubscribeEvent
    public static void onTick(PlayerTickEvent event) {
        if (event.phase == Phase.END) {
            EntityPlayer player = event.player;
            if (PlayerMovementAbilityManager.isValidSideForMovement(player))
                PlayerMovementAbilityManager.tick(player);
            
            if (!player.getEntityWorld().isRemote) {
                if (player.ticksExisted % 2 == 0) {
                    checkFrequent(player);
                    if (player.ticksExisted % 40 == 0)
                        checkResearch(player);
                }
                
                Boolean fly = Boolean.valueOf(player.capabilities.isFlying);
                if (CREATIVE_FLIGHT.contains(player) != fly) {
                    PacketFlightState packet = new PacketFlightState(player.getEntityId(), fly);
                    if (player instanceof EntityPlayerMP)
                        TANetwork.INSTANCE.sendTo(packet, (EntityPlayerMP) player);
                    
                    TANetwork.INSTANCE.sendToAllTracking(packet, player);
                    if (fly)
                        CREATIVE_FLIGHT.add(player);
                    else
                        CREATIVE_FLIGHT.remove(player);
                }
                
                if (ELYTRA_BOOSTS.contains(player)) {
                    if (!playerCanBoost(player)) {
                        ELYTRA_BOOSTS.remove(player);
                        PacketBoostState packet = new PacketBoostState(player.getEntityId(), false);
                        if (player instanceof EntityPlayerMP)
                            TANetwork.INSTANCE.sendTo(packet, (EntityPlayerMP) player);
                        
                        TANetwork.INSTANCE.sendToAllTracking(packet, player);
                    }
                    else {
                        Vec3d vec3d = player.getLookVec();
                        player.motionX += vec3d.x * 0.1 + (vec3d.x * 1.5 - player.motionX) * 0.5;
                        player.motionY += vec3d.y * 0.1 + (vec3d.y * 1.5 - player.motionY) * 0.5;
                        player.motionZ += vec3d.z * 0.1 + (vec3d.z * 1.5 - player.motionZ) * 0.5;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onFallFirst(LivingAttackEvent event) {
        // damage can't be reduced to non-zero here, but cancelling it removes the screen shake and damage sound
        if (event.getSource() == DamageSource.FALL) {
            float damage = event.getAmount();
            for (ItemStack stack : event.getEntityLiving().getArmorInventoryList()) {
                if (stack.getItem() instanceof IArmorReduceFallDamage) {
                    damage = ((IArmorReduceFallDamage) stack.getItem()).getNewFallDamage(stack, damage, event.getEntityLiving().fallDistance);
                }
            }

            damage = Math.max(0.0F, damage);
            if (damage < 1.0F) 
                event.setCanceled(true);
            else
                FALL_DAMAGE.put(event.getEntity(), damage);
        }
    }

    @SubscribeEvent
    public static void onFallHurt(LivingHurtEvent event) {
        // this is needed to actually reduce damage if it's not 0
        if (event.getSource() == DamageSource.FALL && FALL_DAMAGE.containsKey(event.getEntity())) {
            float damage = FALL_DAMAGE.remove(event.getEntity());
            if (damage < 1.0F) {
                event.setAmount(0.0F);
                event.setCanceled(true);
            }
            else
                event.setAmount(damage);
        }
    }
    
    @SubscribeEvent
    @SuppressWarnings("null")
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntity();
            if (event.getSource() instanceof DamageSourceImpetus && !ThaumcraftCapabilities.knowsResearchStrict(player, "f_IMPETUSDAMAGE")) {
                ThaumcraftCapabilities.getKnowledge(player).addResearch("f_IMPETUSDAMAGE");
                player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.impetus_damage").setStyle(
                        new Style().setColor(TextFormatting.DARK_PURPLE)), true);
            }
        }
    }

    @SubscribeEvent
    public static void onFallSound(PlaySoundAtEntityEvent event) {
        if (event.getEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntity();
            if (event.getSound() == SoundEvents.ENTITY_PLAYER_BIG_FALL || event.getSound() == SoundEvents.ENTITY_PLAYER_SMALL_FALL) {
                boolean shouldSilenceFall = false;
                for (ItemStack stack : player.getArmorInventoryList()) {
                    if (stack.getItem() instanceof IArmorReduceFallDamage) {
                        shouldSilenceFall = true;
                        break;
                    }
                }

                if (shouldSilenceFall)
                    event.setCanceled(true);
            }
        }
    }

}
