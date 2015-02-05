package com.elmakers.mine.bukkit.action.builtin;

import com.elmakers.mine.bukkit.api.action.GUIAction;
import com.elmakers.mine.bukkit.api.action.GeneralAction;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.api.wand.LostWand;
import com.elmakers.mine.bukkit.block.MaterialAndData;
import com.elmakers.mine.bukkit.spell.BaseSpellAction;
import com.elmakers.mine.bukkit.utility.CompatibilityUtils;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.Target;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RecallAction extends BaseSpellAction implements GeneralAction, GUIAction
{
    private class UndoMarkerMove implements Runnable {
        private final Location location;
        private final RecallAction spell;

        public UndoMarkerMove(RecallAction spell, Location currentLocation) {
            this.location = currentLocation;
            this.spell = spell;
        }

        @Override
        public void run() {
            spell.location = this.location;
        }
    }

    private enum RecallType
    {
        MARKER,
        DEATH,
        SPAWN,
        HOME,
        WAND,
        WARP
        //	FHOME,
    };

    private class Waypoint implements Comparable<Waypoint>
    {
        public final String name;
        public final Location location;
        public final String message;
        public final String failMessage;
        public final MaterialAndData icon;

        public Waypoint(Location location, String name, String message, String failMessage, MaterialAndData icon) {
            this.name = name;
            this.location = location;
            this.message = message;
            this.failMessage = failMessage;
            this.icon = icon;
        }

        public Waypoint(Location location, String name, String message, String failMessage)
        {
            this.name = name;
            this.location = location;
            this.message = message;
            this.failMessage = failMessage;
            this.icon = new MaterialAndData(Material.NETHER_STAR);
        }

        @Override
        public int compareTo(Waypoint o) {
            return name.compareTo(o.name);
        }

        public boolean isValid(boolean crossWorld, Location source)
        {
            if (location == null || location.getWorld() == null)
            {
                return false;
            }
            return crossWorld || source.getWorld().equals(location.getWorld());
        }
    };

    @Override
    public boolean isUndoable() {
        return true;
    }
	
	public Location location;

	private static int MAX_RETRY_COUNT = 8;
	private static int RETRY_INTERVAL = 10;
	
	private int retryCount = 0;
	private boolean allowCrossWorld = true;
	private List<String> warps = new ArrayList<String>();
	private List<RecallType> enabledTypes = new ArrayList<RecallType>();
    private Map<Integer, Waypoint> options = new HashMap<Integer, Waypoint>();
    private Inventory inventory = null;

    @Override
    public void deactivated() {

    }

    @Override
    public void clicked(InventoryClickEvent event)
    {
        int slot = event.getSlot();
        Waypoint waypoint = options.get(slot);
        if (waypoint != null)
        {
            Player player = getMage().getPlayer();
            getController().deactivateGUI();
            player.closeInventory();
            tryTeleport(player, waypoint);
        }
    }

    @Override
    public SpellResult perform(ConfigurationSection parameters) {
		boolean allowMarker = true;
		int cycleRetries = 5;
		enabledTypes.clear();
		warps = null;

        Mage mage = getMage();
        MageController controller = getController();
		Player player = mage.getPlayer();
		if (player == null) {
            return SpellResult.PLAYER_REQUIRED;
        }
		
		allowCrossWorld = parameters.getBoolean("cross_world", true);
		for (RecallType testType : RecallType.values()) {
			// Special-case for warps
			if (testType == RecallType.WARP) {
				if (parameters.contains("allow_warps")) {
					warps = ConfigurationUtils.getStringList(parameters, "allow_warps");
					enabledTypes.add(testType);
				}
			} else {
				if (parameters.getBoolean("allow_" + testType.name().toLowerCase(), true)) {
					enabledTypes.add(testType);
				} else {
					if (testType == RecallType.MARKER) allowMarker = false;
				}
			}
		}

        if (parameters.contains("warp"))
        {
            String warpName = parameters.getString("warp");
            String title = getMessage("title_warp").replace("$name", warpName);
            String castMessage = getMessage("cast_warp").replace("$name", warpName);
            String failMessage = getMessage("no_target_warp").replace("$name", warpName);
            Location location = controller.getWarp(warpName);
            Waypoint waypoint = new Waypoint(location, title, castMessage, failMessage);
            if (tryTeleport(player, waypoint)) {
                return SpellResult.CAST;
            }
            return SpellResult.FAIL;
        }
        else if (parameters.contains("type"))
        {
			String typeString = parameters.getString("type", "");
			if (typeString.equalsIgnoreCase("remove"))
            {
				if (removeMarker())
                {
                    return SpellResult.TARGET_SELECTED;
                }
                return SpellResult.FAIL;
			}

            if (typeString.equalsIgnoreCase("place"))
            {
                if (placeMarker(getLocation().getBlock()))
                {
                    return SpellResult.TARGET_SELECTED;
                }

                return SpellResult.FAIL;
            }

			RecallType recallType = RecallType.valueOf(typeString.toUpperCase());
			if (recallType == null) {
				controller.getLogger().warning("Unknown recall type " + typeString);
				return SpellResult.FAIL;
			}
			
			Waypoint location = getWaypoint(player, recallType, 0);
			if (tryTeleport(player, location)) {
				return SpellResult.CAST;
			}
			return SpellResult.FAIL;
		}

        List<Waypoint> allWaypoints = new LinkedList<Waypoint>();
        for (RecallType selectedType : enabledTypes) {
            if (selectedType == RecallType.WARP) {
                for (int i = 0; i < warps.size(); i++) {
                    Waypoint targetLocation = getWaypoint(player, selectedType, i);

                    org.bukkit.Bukkit.getLogger().info("Checking waypoint: " + targetLocation.name + " " + targetLocation.location + ": " + allowCrossWorld);

                    if (targetLocation != null && targetLocation.isValid(allowCrossWorld, location)) {
                        allWaypoints.add(targetLocation);
                    }
                }
            } else if (selectedType == RecallType.WAND) {
                List<LostWand> lostWands = mage.getLostWands();
                for (int i = 0; i < lostWands.size(); i++) {
                    Waypoint targetLocation = getWaypoint(player, selectedType, i);
                    if (targetLocation != null && targetLocation.isValid(allowCrossWorld, location)) {
                        allWaypoints.add(targetLocation);
                    }
                }
            } else {
                Waypoint targetLocation = getWaypoint(player, selectedType, 0);
                targetLocation = getWaypoint(player, selectedType, 0);
                if (targetLocation != null && targetLocation.isValid(allowCrossWorld, location)) {
                    allWaypoints.add(targetLocation);
                }
            }
        }
        if (allWaypoints.size() == 0) {
            return SpellResult.NO_TARGET;
        }

        options.clear();
        Collections.sort(allWaypoints);
        String inventoryTitle = getMessage("title", "Recall");
        int invSize = ((allWaypoints.size() + 9) / 9) * 9;
        Inventory displayInventory = CompatibilityUtils.createInventory(null, invSize, inventoryTitle);
        int index = 0;
        for (Waypoint waypoint : allWaypoints)
        {
            ItemStack waypointItem = waypoint.icon.getItemStack(1);
            ItemMeta meta = waypointItem.getItemMeta();
            meta.setDisplayName(waypoint.name);
            waypointItem.setItemMeta(meta);
            displayInventory.setItem(index, waypointItem);
            options.put(index, waypoint);
            index++;
        }
        controller.activateGUI(this);
        mage.getPlayer().openInventory(displayInventory);

        return SpellResult.CAST;
	}

	protected Waypoint getWaypoint(Player player, RecallType type, int index) {
		Mage mage = getMage();
        MageController controller = getController();
		switch (type) {
		case MARKER:
			return new Waypoint(location, getMessage("title_marker"), getMessage("cast_marker"), getMessage("no_target_marker"));
		case DEATH:
            return new Waypoint(mage.getLastDeathLocation(), "Last Death", getMessage("cast_death"), getMessage("no_target_death"));
		case SPAWN:
			return new Waypoint(getWorld().getSpawnLocation(), getMessage("title_spawn"), getMessage("cast_spawn"), getMessage("no_target_spawn"));
        case HOME:
            return new Waypoint(player == null ? null : player.getBedSpawnLocation(), getMessage("title_home"), getMessage("cast_home"), getMessage("no_target_home"));
		case WAND:
            List<LostWand> lostWands = mage.getLostWands();
			if (lostWands == null || index < 0 || index >= lostWands.size()) return null;
			return new Waypoint(lostWands.get(index).getLocation(), getMessage("title_wand"), getMessage("cast_wand"), getMessage("no_target_wand"));
		case WARP:
			if (warps == null || index < 0 || index >= warps.size()) return null;
			String warpName = warps.get(index);
			String castMessage = getMessage("cast_warp").replace("$name", warpName);
            String failMessage = getMessage("no_target_warp").replace("$name", warpName);
            String title = getMessage("title_warp").replace("$name", warpName);
			return new Waypoint(controller.getWarp(warpName), title, castMessage, failMessage);
		}
		
		return null;
	}

	protected boolean removeMarker()
	{
		if (location == null) return false;
		location = null;
		return true;
	}
	
	protected boolean tryTeleport(final Player player, final Waypoint waypoint) {
        Mage mage = getMage();
        Location targetLocation = waypoint.location;
		if (targetLocation == null) {
			sendMessage(waypoint.failMessage);
			return false;
		}
		if (!allowCrossWorld && !mage.getLocation().getWorld().equals(targetLocation.getWorld())) {
			sendMessage(getMessage("cross_world_disallowed"));
			return false;
		}

        MageController controller = getController();
		Chunk chunk = targetLocation.getBlock().getChunk();
		if (!chunk.isLoaded()) {
			chunk.load(true);
			if (retryCount < MAX_RETRY_COUNT) {
				Plugin plugin = controller.getPlugin();
				final RecallAction me = this;
				Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
					public void run() {
						me.tryTeleport(player, waypoint);
					}
				}, RETRY_INTERVAL);
				
				return true;
			}
		}

        registerMoved(player);
        Location playerLocation = player.getLocation();
        targetLocation.setYaw(playerLocation.getYaw());
        targetLocation.setPitch(playerLocation.getPitch());
        player.teleport(tryFindPlaceToStand(targetLocation));
        castMessage(waypoint.message);
		return true;
	}

	protected boolean placeMarker(Block target)
	{
		if (target == null)
		{
			return false;
		}

		registerForUndo(new UndoMarkerMove(this, location));
		if (location != null) 
		{
			removeMarker();
			castMessage(getMessage("cast_marker_move"));
		}
		else
		{
			castMessage(getMessage("cast_marker_place"));
		}

		location = getLocation();
		location.setX(target.getX());
		location.setY(target.getY());
		location.setZ(target.getZ());
		
		return true;
	}
	
	@Override
	public void load(ConfigurationSection node)
	{
		location = ConfigurationUtils.getLocation(node, "location");
	}

	@Override
	public void save(ConfigurationSection node)
	{
		node.set("location", ConfigurationUtils.fromLocation(location));
	}
}
