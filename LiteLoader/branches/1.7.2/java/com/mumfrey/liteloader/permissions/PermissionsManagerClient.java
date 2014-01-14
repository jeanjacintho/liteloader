package com.mumfrey.liteloader.permissions;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.eq2online.permissions.ReplicatedPermissionsContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.play.server.S01PacketJoinGame;

import com.mumfrey.liteloader.LiteMod;
import com.mumfrey.liteloader.Permissible;
import com.mumfrey.liteloader.PluginChannelListener;
import com.mumfrey.liteloader.core.ClientPluginChannels;
import com.mumfrey.liteloader.core.PluginChannels.ChannelPolicy;

/**
 * This class manages permissions on the client, it is a singleton class which can manage permissions for multiple 
 * client mods. It manages the client/server communication used to replicate permissions and serves as a hub for
 * permissions objects which keep track of the permissions available on the client
 * 
 * @author Adam Mummery-Smith
 */
public class PermissionsManagerClient implements PermissionsManager, PluginChannelListener
{
	/**
	 * Singleton instance of the client permissions manager 
	 */
	private static PermissionsManagerClient instance;
	
	/**
	 * Permissions permissible which is a proxy for permissions that are common to all mods
	 */
	private static Permissible allMods = new PermissibleAllMods();
	
	/**
	 * Minecraft instance 
	 */
	private Minecraft minecraft;
	
	/**
	 * List of registered client mods supporting permissions
	 */
	private Map<String, Permissible> registeredClientMods = new HashMap<String, Permissible>();
	
	/**
	 * List of registered client permissions, grouped by mod
	 */
	private Map<Permissible, TreeSet<String>> registeredClientPermissions = new HashMap<Permissible, TreeSet<String>>();
	
	/**
	 * Objects which listen to events generated by this object
	 */
	private Set<Permissible> permissibles = new HashSet<Permissible>();
	
	/**
	 * Local permissions, used when server permissions are not available
	 */
	private LocalPermissions localPermissions = new LocalPermissions();
	
	/**
	 * Server permissions, indexed by mod
	 */
	private Map<String, ServerPermissions> serverPermissions = new HashMap<String, ServerPermissions>();
	
	/**
	 * Last time onTick was called, used to detect tamper condition if no ticks are being received
	 */
	private long lastTickTime = System.currentTimeMillis();
	
	/**
	 * Delay counter for when joining a server
	 */
	private int pendingRefreshTicks = 0;
	
	private int menuTicks = 0;

	/**
	 * Get a reference to the singleton instance of the client permissions manager
	 * 
	 * @return
	 */
	public static PermissionsManagerClient getInstance()
	{
		if (instance == null)
		{
			instance = new PermissionsManagerClient();
		}
		
		return instance;
	}
	
	/**
	 * Private .ctor, for singleton pattern
	 */
	private PermissionsManagerClient()
	{
		this.registerClientMod("all", allMods);
	}
	
	/* (non-Javadoc)
	 * @see net.eq2online.permissions.PermissionsManager#getPermissions(java.lang.String)
	 */
	@Override
	public Permissions getPermissions(Permissible mod)
	{
		if (mod == null) mod = allMods;
		String modName = mod.getPermissibleModName();

		ServerPermissions modPermissions = this.serverPermissions.get(modName);
		return modPermissions != null ? modPermissions : this.localPermissions;
	}
	
	/* (non-Javadoc)
	 * @see net.eq2online.permissions.PermissionsManager#getPermissionUpdateTime(java.lang.String)
	 */
	@Override
	public Long getPermissionUpdateTime(Permissible mod)
	{
		if (mod == null) mod = allMods;
		String modName = mod.getPermissibleModName();

		ServerPermissions modPermissions = this.serverPermissions.get(modName);
		return modPermissions != null ? modPermissions.getReplicationTime() : 0;
	}
	
	/**
	 * Register a new mod, if permissible
	 * 
	 * @param mod
	 */
	public void registerMod(LiteMod mod)
	{
		if (mod instanceof Permissible)
		{
			this.registerPermissible((Permissible)mod);
		}
	}

	/* (non-Javadoc)
	 * @see net.eq2online.permissions.PermissionsManager#registerListener(net.eq2online.permissions.PermissionsListener)
	 */
	@Override
	public void registerPermissible(Permissible permissible)
	{
		if (!this.permissibles.contains(permissible) && permissible.getPermissibleModName() != null)
		{
			this.registerClientMod(permissible.getPermissibleModName(), permissible);
			permissible.registerPermissions(this);
		}
		
		this.permissibles.add(permissible);
	}
	
	/**
	 * Register a new client mod with this manager
	 * 
	 * @param modName Mod name
	 * @param modVersion Mod version
	 */
	private void registerClientMod(String modName, Permissible mod)
	{
		if (this.registeredClientMods.containsKey(modName))
		{
			throw new IllegalArgumentException("Cannot register mod \"" + modName + "\"! The mod was already registered with the permissions manager.");
		}
		
		this.registeredClientMods.put(modName, mod);
		this.registeredClientPermissions.put(mod, new TreeSet<String>());
	}

	/* (non-Javadoc)
	 * @see com.mumfrey.liteloader.permissions.PermissionsManager#onLogin(net.minecraft.network.INetHandler, net.minecraft.network.play.server.S01PacketJoinGame)
	 */
	@Override
	public void onJoinGame(INetHandler netHandler, S01PacketJoinGame joinGamePacket)
	{
		this.clearServerPermissions();
		this.scheduleRefresh();
	}

	/**
	 * Schedule a permissions refresh
	 */
	public void scheduleRefresh()
	{
		this.pendingRefreshTicks = 2;
	}
	
	/**
	 * Clears the current replicated server permissions 
	 */
	protected void clearServerPermissions()
	{
		this.serverPermissions.clear();
		
		for (Permissible permissible : this.permissibles)
			permissible.onPermissionsCleared(this);
	}

	/**
	 * Send permission query packets to the server for all registered mods
	 * 
	 * @param minecraft Minecraft instance
	 */
	protected void sendPermissionQueries()
	{
		for (Permissible mod : this.registeredClientMods.values())
			this.sendPermissionQuery(mod);
	}

	/**
	 * Send a permission query packet to the server for the specified mod. You do not need to call this method because it is
	 * issued automatically by the client permissions manager when connecting to a new server. However you can call use this
	 * method to "force" a refresh of permissions when needed.
	 * 
	 * @param modName name of the mod to send a query packet for
	 */
	public void sendPermissionQuery(Permissible mod)
	{
		String modName = mod.getPermissibleModName();
		
		if (this.minecraft != null && this.minecraft.thePlayer != null && this.minecraft.theWorld != null && this.minecraft.theWorld.isRemote)
		{
			if (!this.registeredClientMods.containsValue(mod))
			{
				throw new IllegalArgumentException("The specified mod \"" + modName + "\" was not registered with the permissions system");
			}
			
			Float modVersion = mod.getPermissibleModVersion();
			Set<String> modPermissions = this.registeredClientPermissions.get(mod);
			
			if (modPermissions != null)
			{
				ReplicatedPermissionsContainer query = new ReplicatedPermissionsContainer(modName, modVersion, modPermissions);
	
				if (!query.modName.equals("all") || query.permissions.size() > 0)
				{
					byte[] data = query.getBytes();
					ClientPluginChannels.sendMessage(ReplicatedPermissionsContainer.CHANNEL, data, ChannelPolicy.DISPATCH_ALWAYS);
				}
			}
		}
		else
		{
			this.serverPermissions.remove(modName);
		}
	}

	/* (non-Javadoc)
	 * @see com.mumfrey.liteloader.permissions.PermissionsManager#onTick(net.minecraft.client.Minecraft, float, boolean)
	 */
	@Override
	public void onTick(Minecraft minecraft, float partialTicks, boolean inGame)
	{
		this.minecraft = minecraft;
		this.lastTickTime = System.currentTimeMillis();
		
		if (this.pendingRefreshTicks > 0)
		{
			this.pendingRefreshTicks--;
			
			if (this.pendingRefreshTicks == 0 && inGame)
			{
				this.sendPermissionQueries();
				return;
			}
		}
		
		for (Map.Entry<String, ServerPermissions> modPermissions : this.serverPermissions.entrySet())
		{
			if (!modPermissions.getValue().isValid())
			{
				modPermissions.getValue().notifyRefreshPending();
				this.sendPermissionQuery(this.registeredClientMods.get(modPermissions.getKey()));
			}
		}
		
		if (inGame) this.menuTicks = 0; else this.menuTicks++;
		
		if (this.menuTicks == 200)
		{
			this.clearServerPermissions();
		}
	}
	
	/* (non-Javadoc)
	 * @see net.eq2online.permissions.PermissionsManager#tamperCheck()
	 */
	@Override
	public void tamperCheck()
	{
		if (System.currentTimeMillis() - this.lastTickTime > 60000L)
		{
			throw new IllegalStateException("Client permissions manager was not ticked for 60 seconds, tamper.");
		}
	}

	/* (non-Javadoc)
	 * @see net.eq2online.permissions.PermissionsManager#onCustomPayload(java.lang.String, int, byte[])
	 */
	@Override
	public void onCustomPayload(String channel, int length, byte[] data)
	{
		if (channel.equals(ReplicatedPermissionsContainer.CHANNEL) && !Minecraft.getMinecraft().isSingleplayer())
		{
			ServerPermissions modPermissions = null;
			try
			{
				modPermissions = new ServerPermissions(data);
			}
			catch (Exception ex) {}
			
			if (modPermissions != null && modPermissions.getModName() != null)
			{
				this.serverPermissions.put(modPermissions.getModName(), modPermissions);
	
				Permissible permissible = this.registeredClientMods.get(modPermissions.getModName());
				if (permissible != null) permissible.onPermissionsChanged(this);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see net.eq2online.permissions.PermissionsManager#getChannels()
	 */
	@Override
	public List<String> getChannels()
	{
		return Arrays.asList(new String[] { ReplicatedPermissionsContainer.CHANNEL });
	}
	
	/**
	 * Register a permission for all mods, the permission will be prefixed with "mod.all." to provide
	 * a common namespace for client mods when permissions are replicated to the server
	 * 
	 * @param permission
	 */
	public void registerPermission(String permission)
	{
		this.registerModPermission(allMods, permission);
	}
	
	/**
	 * Register a permission for the specified mod, the permission will be prefixed with "mod.<modname>." to provide
	 * a common namespace for client mods when permissions are replicated to the server
	 * 
	 * @param mod
	 * @param permission
	 */
	public void registerModPermission(Permissible mod, String permission)
	{
		if (mod == null) mod = allMods;
		String modName = mod.getPermissibleModName();
		
		if (!this.registeredClientMods.containsValue(mod))
		{
			throw new IllegalArgumentException("Cannot register a mod permission for mod \"" + modName + "\"! The mod was not registered with the permissions manager.");
		}
		
		permission = formatModPermission(modName, permission);
		
		Set<String> modPermissions = this.registeredClientPermissions.get(mod);
		if (modPermissions != null && !modPermissions.contains(permission))
		{
			modPermissions.add(permission);
		}
	}
	
	/**
	 * Get the value of the specified permission for all mods.
	 * 
	 * @param permission Permission to check for
	 * @return
	 */
	public boolean getPermission(String permission)
	{
		return this.getModPermission(allMods, permission);
	}
	
	/**
	 * Get the value of the specified permission for all mods and return the default value if the permission is not set
	 * 
	 * @param permission Permission to check for
	 * @param defaultValue Value to return if the permission is not set
	 * @return
	 */
	public boolean getPermission(String permission, boolean defaultValue)
	{
		return this.getModPermission(allMods, permission, defaultValue);
	}
	
	/**
	 * Get the value of the specified permission for the specified mod. The permission will be prefixed with "mod.<modname>."
	 * in keeping with registerModPermission as a convenience.
	 * 
	 * @param mod
	 * @param permission
	 * @return
	 */
	public boolean getModPermission(Permissible mod, String permission)
	{
		if (mod == null) mod = PermissionsManagerClient.allMods;
		permission = formatModPermission(mod.getPermissibleModName(), permission);
		Permissions permissions = this.getPermissions(mod);
		
		if (permissions != null)
		{
			return permissions.getHasPermission(permission);
		}
		
		return true;
	}

	/**
	 * Get the value of the specified permission for the specified mod. The permission will be prefixed with "mod.<modname>."
	 * in keeping with registerModPermission as a convenience.
	 * 
	 * @param modName
	 * @param permission
	 * @return
	 */
	public boolean getModPermission(String modName, String permission)
	{
		Permissible mod = this.registeredClientMods.get(modName);
		return mod != null ? this.getModPermission(mod, permission) : false;
	}
	
	/**
	 * Get the value of the specified permission for the specified mod. The permission will be prefixed with "mod.<modname>."
	 * in keeping with registerModPermission as a convenience. If the permission does not exist, the specified default value
	 * will be returned.
	 * 
	 * @param mod
	 * @param permission
	 * @param defaultValue
	 * @return
	 */
	public boolean getModPermission(Permissible mod, String permission, boolean defaultValue)
	{
		if (mod == null) mod = allMods;
		permission = formatModPermission(mod.getPermissibleModName(), permission);
		Permissions permissions = this.getPermissions(mod);
		
		if (permissions != null && permissions.getPermissionSet(permission))
		{
			return permissions.getHasPermission(permission);
		}
		
		return defaultValue;
	}

	/**
	 * Get the value of the specified permission for the specified mod. The permission will be prefixed with "mod.<modname>."
	 * in keeping with registerModPermission as a convenience.
	 * 
	 * @param modName
	 * @param permission
	 * @return
	 */
	public boolean getModPermission(String modName, String permission, boolean defaultValue)
	{
		Permissible mod = this.registeredClientMods.get(modName);
		return mod != null ? this.getModPermission(mod, permission, defaultValue) : defaultValue;
	}
	
	/**
	 * @param modName
	 * @param permission
	 * @return
	 */
	protected static String formatModPermission(String modName, String permission)
	{
		return String.format("mod.%s.%s", modName, permission);
	}

	/* (non-Javadoc)
	 * @see com.mumfrey.liteloader.LiteMod#getName()
	 */
	@Override
	public String getName()
	{
		// Stub for PluginChannelListener interface
		return null;
	}

	/* (non-Javadoc)
	 * @see com.mumfrey.liteloader.LiteMod#getVersion()
	 */
	@Override
	public String getVersion()
	{
		// Stub for PluginChannelListener interface
		return null;
	}

	/* (non-Javadoc)
	 * @see com.mumfrey.liteloader.LiteMod#init()
	 */
	@Override
	public void init(File configPath)
	{
		// Stub for PluginChannelListener interface
	}
	
	@Override
	public void upgradeSettings(String version, File configPath, File oldConfigPath)
	{
	}
}
