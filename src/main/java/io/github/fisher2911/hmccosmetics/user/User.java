package io.github.fisher2911.hmccosmetics.user;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import io.github.fisher2911.hmccosmetics.HMCCosmetics;
import io.github.fisher2911.hmccosmetics.gui.ArmorItem;
import io.github.fisher2911.hmccosmetics.inventory.PlayerArmor;
import io.github.fisher2911.hmccosmetics.message.MessageHandler;
import io.github.fisher2911.hmccosmetics.message.Messages;
import io.github.fisher2911.hmccosmetics.message.Placeholder;
import io.github.fisher2911.hmccosmetics.util.builder.ColorBuilder;
import io.github.fisher2911.hmccosmetics.util.builder.ItemBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class User {

    private final UUID uuid;
    private final PlayerArmor playerArmor;
    private ArmorItem lastSetItem;

    private boolean hasArmorStand;
    private final int armorStandId;

    public User(final UUID uuid, final PlayerArmor playerArmor, final int armorStandId) {
        this.uuid = uuid;
        this.playerArmor = playerArmor;
        this.armorStandId = armorStandId;
        this.lastSetItem = playerArmor.getHat();
    }

    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(this.uuid);
    }

    public UUID getUuid() {
        return uuid;
    }

    public PlayerArmor getPlayerArmor() {
        return playerArmor;
    }

    public void setPlayerArmor(final PlayerArmor playerArmor) {
        this.playerArmor.setBackpack(playerArmor.getBackpack());
        this.playerArmor.setHat(playerArmor.getHat());
    }

    public void removeAllCosmetics() {
        this.setPlayerArmor(PlayerArmor.empty());
    }

    public void removeHat(final HMCCosmetics plugin) {
        this.setHat(ArmorItem.empty(ArmorItem.Type.HAT), plugin);
    }

    public void removeBackpack(final HMCCosmetics plugin) {
        this.setBackpack(ArmorItem.empty(ArmorItem.Type.BACKPACK), plugin);
    }

    public int getArmorStandId() {
        return armorStandId;
    }

    public void setBackpack(final ArmorItem backpack, final HMCCosmetics plugin) {
        this.playerArmor.setBackpack(backpack);
        this.lastSetItem = backpack;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabase().saveUser(this));
    }

    // return true if backpack was set
    public boolean setOrUnsetBackpack(
            final ArmorItem backpack,
            final MessageHandler messageHandler,
            final HMCCosmetics plugin) {

        final Player player = this.getPlayer();

        if (player == null) {
            return false;
        }

        if (backpack.getId().equals(this.playerArmor.getBackpack().getId())) {
            this.setBackpack(ArmorItem.empty(ArmorItem.Type.BACKPACK), plugin);

            messageHandler.sendMessage(
                    player,
                    Messages.REMOVED_BACKPACK
            );

            return false;
        }

        this.setBackpack(backpack, plugin);
        messageHandler.sendMessage(
                player,
                Messages.SET_BACKPACK
        );

        return true;
    }


    public void setHat(ArmorItem hat, final HMCCosmetics plugin) {
        this.playerArmor.setHat(hat);
        this.lastSetItem = hat;
        plugin.getUserManager().updateHat(this);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabase().saveUser(this));
    }

    // return true if hat was set
    public boolean setOrUnsetHat(
            final ArmorItem hat,
            final MessageHandler messageHandler,
            final HMCCosmetics plugin) {

        final Player player = this.getPlayer();

        if (player == null) {
            return false;
        }

        if (hat.getId().equals(this.playerArmor.getHat().getId())) {
            this.setHat(ArmorItem.empty(ArmorItem.Type.HAT), plugin);

            messageHandler.sendMessage(
                    player,
                    Messages.REMOVED_HAT
            );

            return false;
        }

        this.setHat(hat, plugin);
        messageHandler.sendMessage(
                player,
                Messages.SET_HAT
        );

        return true;
    }

    public void spawnArmorStand(final Player other) {
        final Player player = this.getPlayer();

        if (player == null) return;

        final Location location = player.getLocation();

        final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        final PacketContainer packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);

        // Entity ID
        packet.getIntegers().write(0, this.armorStandId);
        // Entity Type
//        packet.getIntegers().write(6, 78);
        // Set yaw pitch
        packet.getIntegers().write(4, (int) location.getPitch());
        packet.getIntegers().write(5, (int) location.getYaw());
        // Set location
        packet.getDoubles().write(0, location.getX());
        packet.getDoubles().write(1, location.getY());
        packet.getDoubles().write(2, location.getZ());
        // Set UUID
        packet.getUUIDs().write(0, UUID.randomUUID());

        packet.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);

        final PacketContainer ridingPacket = new PacketContainer(PacketType.Play.Server.MOUNT);
        ridingPacket.
                getIntegers().
                write(0, player.getEntityId());
        ridingPacket.getIntegerArrays().write(0, new int[]{this.armorStandId});

        try {
            protocolManager.sendServerPacket(other, packet);
            protocolManager.sendServerPacket(other, ridingPacket);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void spawnArmorStand() {
        if (this.hasArmorStand) {
            this.updateArmorStand();
            return;
        }

        for (final Player p : Bukkit.getOnlinePlayers()) {
            this.spawnArmorStand(p);
        }

        this.hasArmorStand = true;
    }

    public void updateArmorStand() {
        if (!this.hasArmorStand) {
            this.spawnArmorStand();
            return;
        }

        final Player player = this.getPlayer();

        if (player == null) return;

        final List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipmentList = new ArrayList<>();

        final Map<String, String> placeholders = Map.of(Placeholder.ALLOWED, "true",
                Placeholder.ENABLED, "true");

        equipmentList.add(new Pair<>(EnumWrappers.ItemSlot.HEAD,
                ItemBuilder.from(this.playerArmor.getBackpack().getItemStack()).
                        namePlaceholders(placeholders).
                        lorePlaceholders(placeholders).
                        build()
        ));

        final PacketContainer armorPacket = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
        armorPacket.getIntegers().write(0, this.armorStandId);
        armorPacket.getSlotStackPairLists().write(0, equipmentList);

        final Location location = player.getLocation();

        final PacketContainer metaContainer = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);

        WrappedDataWatcher metaData = new WrappedDataWatcher();
        metaData.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(0, WrappedDataWatcher.Registry.get(Byte.class)), (byte) (0x20));
        metaData.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(15, WrappedDataWatcher.Registry.get(Byte.class)), (byte) (0x10));

        final PacketContainer rotationPacket = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);

        rotationPacket.getIntegers().write(0, this.armorStandId);
        rotationPacket.getBytes().write(0, (byte) (location.getYaw() * 256 / 360));

        metaContainer.getIntegers().write(0, this.armorStandId);
        metaContainer.getWatchableCollectionModifier().write(0, metaData.getWatchableObjects());

        final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        final PacketContainer teleportPacket = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);

        teleportPacket.getIntegers().write(0, this.armorStandId);
        teleportPacket.getDoubles().
                write(0, location.getX()).
                write(1, location.getY()).
                write(2, location.getZ());

        teleportPacket.getBytes().
                write(0, (byte) (location.getYaw() * 256.0F / 360.0F)).
                write(1, (byte) (location.getPitch() * 256.0F / 360.0F));

        for (final Player p : Bukkit.getOnlinePlayers()) {
            try {
                protocolManager.sendServerPacket(p, armorPacket);
                protocolManager.sendServerPacket(p, metaContainer);
                protocolManager.sendServerPacket(p, rotationPacket);
            } catch (final InvocationTargetException exception) {
                exception.printStackTrace();
            }
        }
    }

    public void despawnAttached() {
        final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        final PacketContainer destroyPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getModifier().write(0, new IntArrayList(new int[]{this.armorStandId}));

        for (final Player p : Bukkit.getOnlinePlayers()) {
            try {
                protocolManager.sendServerPacket(p, destroyPacket);
                this.hasArmorStand = false;
            } catch (final InvocationTargetException exception) {
                exception.printStackTrace();
            }
        }
    }

    public ArmorItem getLastSetItem() {
        return lastSetItem;
    }

    public int getDye() {
        return this.playerArmor.getDye();
    }

    public void setDye(final int dye) {
        this.playerArmor.setDye(dye);
    }

    public boolean hasArmorStand() {
        return hasArmorStand;
    }

}
