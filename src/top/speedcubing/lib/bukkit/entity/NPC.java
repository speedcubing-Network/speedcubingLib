package top.speedcubing.lib.bukkit.entity;

import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import top.speedcubing.lib.api.MojangAPI;
import top.speedcubing.lib.bukkit.CubingLibPlayer;
import top.speedcubing.lib.bukkit.packetwrapper.OutScoreboardTeam;
import top.speedcubing.lib.utils.Reflections;

import java.util.*;

public class NPC {

    public static final Set<NPC> all = new HashSet<>();
    public final Set<PlayerConnection> listener = new HashSet<>();
    public final Set<String> world = new HashSet<>();

    boolean autoSpawn;
    boolean autoListen = true;
    public final EntityPlayer entityPlayer;
    boolean nameTagHidden;
    float spawnBodyYaw;
    ItemStack itemInHand;
    ClickEvent event;

    public interface ClickEvent {
        void run(Player player, PacketPlayInUseEntity.EnumEntityUseAction action);
    }

    public NPC setClickEvent(ClickEvent e) {
        this.event = e;
        return this;
    }

    public ClickEvent getClickEvent() {
        return event;
    }

    public NPC(String name, UUID uuid, boolean enableOuterLayerSkin) {
        WorldServer world = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        entityPlayer = new EntityPlayer(((CraftServer) Bukkit.getServer()).getServer(), world, new GameProfile(uuid == null ? UUID.randomUUID() : uuid, name), new PlayerInteractManager(world));
        if (enableOuterLayerSkin)
            entityPlayer.getDataWatcher().watch(10, (byte) 127);
        all.add(this);
    }

    public void delete() {
        for (CubingLibPlayer p : CubingLibPlayer.user.values()) {
            p.outRange.remove(this);
        }
        all.remove(this);
    }

    public NPC world(String... world) {
        this.world.addAll(Sets.newHashSet(world));
        return this;
    }

    public NPC setAutoSpawn(boolean autoSpawn) {
        this.autoSpawn = autoSpawn;
        return this;
    }

    public boolean getAutoSpawn() {
        return autoSpawn;
    }

    public NPC setAutoListen(boolean autoListen) {
        this.autoListen = autoListen;
        listener.clear();
        if (autoListen)
            Bukkit.getOnlinePlayers().forEach(a -> listener.add(((CraftPlayer) a).getHandle().playerConnection));
        return this;
    }

    public boolean getAutoListen() {
        return autoSpawn;
    }

    public NPC setSpawnBodyYaw(float spawnBodyYaw) {
        this.spawnBodyYaw = spawnBodyYaw;
        return this;
    }

    public NPC animation(int animation) {
        PacketPlayOutAnimation packet = new PacketPlayOutAnimation();
        Reflections.setField(packet, "a", entityPlayer.getId());
        Reflections.setField(packet, "b", (byte) animation);
        listener.forEach(a -> a.sendPacket(packet));
        return this;
    }

    public NPC status(int status) {
        PacketPlayOutEntityStatus packet = new PacketPlayOutEntityStatus();
        Reflections.setField(packet, "a", entityPlayer.getId());
        Reflections.setField(packet, "b", (byte) status);
        listener.forEach(a -> a.sendPacket(packet));
        return this;
    }

    public NPC setSneaking(boolean sneaking) {
        entityPlayer.setSneaking(sneaking);
        PacketPlayOutEntityMetadata metadata = new PacketPlayOutEntityMetadata(entityPlayer.getId(), entityPlayer.getDataWatcher(), true);
        listener.forEach(a -> a.sendPacket(metadata));
        return this;
    }

    public NPC setItemInHand(ItemStack itemInHand) {
        this.itemInHand = itemInHand == null || itemInHand.getType().equals(Material.AIR) ? null : itemInHand;
        PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(entityPlayer.getId(), 0, CraftItemStack.asNMSCopy(itemInHand));
        listener.forEach(a -> a.sendPacket(packet));
        return this;
    }

    public NPC spawn() {
        float yaw = entityPlayer.yaw;
        entityPlayer.yaw = spawnBodyYaw;
        PacketPlayOutPlayerInfo p1 = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, entityPlayer);
        PacketPlayOutNamedEntitySpawn p2 = new PacketPlayOutNamedEntitySpawn(entityPlayer);
        entityPlayer.yaw = yaw;
        PacketPlayOutEntityHeadRotation p3 = new PacketPlayOutEntityHeadRotation(entityPlayer, (byte) ((int) (entityPlayer.yaw * 256 / 360)));
        listener.forEach(a -> {
                    a.sendPacket(p1);
                    a.sendPacket(p2);
                    a.sendPacket(p3);
                }
        );
        if (nameTagHidden)
            hideNametag();
        if (itemInHand != null)
            setItemInHand(itemInHand);
        return this;
    }

    public NPC setSkin(UUID uuid) {
        try {
            String[] skin = MojangAPI.getSkin(uuid);
            return setSkin(skin[0], skin[1]);
        } catch (Exception e) {
            return this;
        }
    }

    public NPC setSkin(String name) {
        try {
            return setSkin(MojangAPI.getUUID(name));
        } catch (Exception e) {
            return this;
        }
    }

    public NPC setSkin(String value, String signature) {
        GameProfile gameProfile = entityPlayer.getProfile();
        gameProfile.getProperties().removeAll("textures");
        gameProfile.getProperties().put("textures", new Property("textures", value, signature));
        if (!listener.isEmpty()) {
            despawn();
            spawn();
            updateNpcLocation();
        }
        return this;
    }

    public NPC despawn() {
        PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(entityPlayer.getId());
        listener.forEach(a -> a.sendPacket(packet));
        return this;
    }

    public NPC setLocation(double x, double y, double z, float headRotation, float pitch) {
        entityPlayer.setLocation(x, y, z, headRotation, pitch);
        return this;
    }

    public NPC setLocation(Location location) {
        world.add(location.getWorld().getName());
        return setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public NPC hideNametag() {
        this.nameTagHidden = true;
        PacketPlayOutScoreboardTeam p1 = new OutScoreboardTeam().a("").e("never").h(1).packet;
        PacketPlayOutScoreboardTeam p2 = new OutScoreboardTeam().a("").e("never").packet;
        PacketPlayOutScoreboardTeam p3 = new OutScoreboardTeam().a("").g(Collections.singletonList(entityPlayer.getName())).h(3).packet;
        listener.forEach(a -> {
            a.sendPacket(p1);
            a.sendPacket(p2);
            a.sendPacket(p3);
        });
        return this;
    }

    public NPC hideFromTab() {
        return hideFromTab(4000);
    }

    public NPC hideFromTab(int delay) {
        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, entityPlayer);
        if (delay < 1) {
            listener.forEach(a -> a.sendPacket(packet));
            return this;
        }
        Set<PlayerConnection> copy = new HashSet<>(listener);
        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        copy.forEach(a -> a.sendPacket(packet));
                    }
                }, delay);
        return this;
    }


    public NPC updateNpcLocation() {
        PacketPlayOutEntityTeleport p1 = new PacketPlayOutEntityTeleport(entityPlayer.getId(), MathHelper.floor(entityPlayer.locX * 32), MathHelper.floor(entityPlayer.locY * 32), MathHelper.floor(entityPlayer.locZ * 32), (byte) ((int) (entityPlayer.yaw * 256 / 360)), (byte) ((int) (entityPlayer.pitch * 256 / 360)), true);
        PacketPlayOutEntity.PacketPlayOutEntityLook p2 = new PacketPlayOutEntity.PacketPlayOutEntityLook(entityPlayer.getId(), (byte) ((int) (entityPlayer.yaw * 256F / 360F)), (byte) ((int) (entityPlayer.pitch * 256F / 360F)), true);
        PacketPlayOutEntityHeadRotation p3 = new PacketPlayOutEntityHeadRotation(entityPlayer, (byte) ((int) (entityPlayer.yaw * 256F / 360F)));
        listener.forEach(a -> {
                    a.sendPacket(p1);
                    a.sendPacket(p2);
                    a.sendPacket(p3);
                }
        );
        return this;
    }

    private final Set<PlayerConnection> temp = new HashSet<>();

    public NPC setListenerValues(PlayerConnection... connections) {
        temp.addAll(listener);
        listener.clear();
        listener.addAll(Sets.newHashSet(connections));
        return this;
    }

    public NPC rollBackListenerValues() {
        listener.clear();
        listener.addAll(temp);
        temp.clear();
        return this;
    }
}
