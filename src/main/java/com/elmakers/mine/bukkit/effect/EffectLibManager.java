package com.elmakers.mine.bukkit.effect;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.util.ParticleEffect;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages EffectLib integration
 */
public class EffectLibManager {
    private static EffectManager effectManager;
    private static final Map<String, String> nameMap = new HashMap<String, String>();

    public EffectLibManager() {
    }

    public static EffectLibManager initialize(Plugin plugin) {
        if (effectManager == null) {
            effectManager = new EffectManager(plugin);
        }

        return new EffectLibManager();
    }

    public void enableDebug(boolean debug) {
        if (effectManager != null) {
            effectManager.enableDebug(debug);
        }
    }

    public Effect play(ConfigurationSection configuration, EffectPlayer player, Location origin, Entity originEntity, Location target, Entity targetEntity) {
        if (originEntity != null && originEntity instanceof Player) {
            nameMap.put("$name", ((Player)originEntity).getName());
        } else if (originEntity != null && originEntity instanceof LivingEntity) {
            nameMap.put("$name", ((LivingEntity)originEntity).getCustomName());
        } else {
            nameMap.put("$name", "Unknown");
        }
        if (targetEntity != null && targetEntity instanceof Player) {
            nameMap.put("$target", ((Player)targetEntity).getName());
        } else if (originEntity != null && targetEntity instanceof LivingEntity) {
            nameMap.put("$target", ((LivingEntity)targetEntity).getCustomName());
        } else {
            nameMap.put("$target", "Unknown");
        }

        Effect effect = null;
        String effectClass = configuration.getString("class");
        ParticleEffect particleEffect = player.overrideParticle(null);
        String effectOverride = player.getParticleOverrideName();
        if (effectOverride != null && effectOverride.isEmpty()) effectOverride = null;
        String colorOverrideName = player.getColorOverrideName();
        if (colorOverrideName != null && colorOverrideName.isEmpty()) colorOverrideName = null;
        ConfigurationSection parameters = configuration;
        Color colorOverride = player.getColor1();
        if ((colorOverrideName != null && colorOverride != null) || (effectOverride != null && particleEffect != null))
        {
            parameters = new MemoryConfiguration();
            Collection<String> keys = configuration.getKeys(false);
            for (String key : keys) {
                parameters.set(key, configuration.get(key));
            }
            if (effectOverride != null && particleEffect != null)
            {
                parameters.set(effectOverride, particleEffect.name());
            }
            if (colorOverride != null && colorOverrideName != null)
            {
                String hexColor = Integer.toHexString(colorOverride.asRGB());
                parameters.set(colorOverrideName, hexColor);
            }
        }
        try {
            effect = effectManager.start(effectClass, parameters, origin, target, originEntity, targetEntity, nameMap);
        } catch (Throwable ex) {
            Bukkit.getLogger().warning("Error playing effects of class: " + effectClass);
            ex.printStackTrace();
        }
        return effect;
    }

    public void cancel(Collection<Effect> effects) {
        for (Effect effect : effects) {
            try {
                effect.cancel();
            } catch (Throwable ex) {
                Bukkit.getLogger().warning("Error cancelling effects");
                ex.printStackTrace();
            }
        }
    }
}
