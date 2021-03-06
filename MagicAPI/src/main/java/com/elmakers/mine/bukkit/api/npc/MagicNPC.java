package com.elmakers.mine.bukkit.api.npc;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;

import com.elmakers.mine.bukkit.api.entity.EntityData;
import com.elmakers.mine.bukkit.api.magic.Locatable;

public interface MagicNPC extends Locatable {
    @Nonnull
    EntityData getEntityData();
    @Override
    @Nullable
    Location getLocation();
    @Override
    boolean isActive();
    @Override
    @Nonnull
    String getName();
    void setName(@Nonnull String name);
    void configure(String key, Object value);
    void update();
    @Nonnull
    ConfigurationSection getParameters();
    @Nonnull
    UUID getUUID();
    void teleport(@Nonnull Location location);
    boolean setType(@Nonnull String mobKey);
    void remove();
    void describe(CommandSender sender);
    @Nullable
    Integer getImportedId();
    @Nullable
    Entity getEntity();
}
