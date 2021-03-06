package com.mumfrey.liteloader.util;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiIngame;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.GuiSlot;
import net.minecraft.src.GuiTexturePacks;
import net.minecraft.src.NetClientHandler;
import net.minecraft.src.NetworkManager;
import net.minecraft.src.Packet;
import net.minecraft.src.Profiler;
import net.minecraft.src.RenderEngine;
import net.minecraft.src.RenderGlobal;
import net.minecraft.src.RenderManager;
import net.minecraft.src.SoundManager;
import net.minecraft.src.SoundPool;
import net.minecraft.src.StringTranslate;
import net.minecraft.src.TexturePackCustom;
import net.minecraft.src.TexturePackImplementation;
import net.minecraft.src.TexturePackList;
import net.minecraft.src.TileEntity;
import net.minecraft.src.Timer;
import net.minecraft.src.WorldInfo;
import net.minecraft.src.WorldRenderer;
import net.minecraft.src.WorldType;

/**
 * Wrapper for obf/mcp reflection-accessed private fields, mainly added to centralise the locations I have to update the obfuscated field names
 * 
 * @author Adam Mummery-Smith
 *
 * @param <P> Parent class type, the type of the class that owns the field
 * @param <T> Field type, the type of the field value
 */
public class PrivateFields<P, T>
{
	/**
	 * Class to which this field belongs
	 */
	public final Class parentClass;

	/**
	 * MCP name for this field
	 */
	public final String mcpName;

	/**
	 * Real (obfuscated) name for this field
	 */
	public final String name;
	
	/**
	 * Name used to access the field, determined at init
	 */
	private final String fieldName;
	
	/**
	 * Creates a new private field entry
	 * 
	 * @param owner
	 * @param mcpName
	 * @param name
	 */
	private PrivateFields(Class owner, String mcpName, String name)
	{
		this.parentClass = owner;
		this.mcpName = mcpName;
		this.name = name;
		
		this.fieldName = ModUtilities.getObfuscatedFieldName(mcpName, name);
	}
	
	/**
	 * Get the current value of this field on the instance class supplied
	 * 
	 * @param instance Class to get the value of
	 * @return field value or null if errors occur
	 */
	public T Get(P instance)
	{
		try
		{
			Field field = parentClass.getDeclaredField(fieldName);
			field.setAccessible(true);
			return (T)field.get(instance);
		}
		catch (Exception ex)
		{
			return null;
		}
	}
	
	/**
	 * Set the value of this field on the instance class supplied
	 * 
	 * @param instance Object to set the value of the field on
	 * @param value value to set
	 * @return value
	 */
	public T Set(P instance, T value)
	{
		try
		{
			Field field = parentClass.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(instance, value);
		}
		catch (Exception ex) {}
		
		return value;
	}
	
	/**
	 * Set the value of this FINAL field on the instance class supplied
	 * 
	 * @param instance Object to set the value of the field on
	 * @param value value to set
	 * @return value
	 */
	public T SetFinal(P instance, T value)
	{
		try
		{
			Field modifiers = Field.class.getDeclaredField("modifiers");
			modifiers.setAccessible(true);
			
			Field field = parentClass.getDeclaredField(fieldName);
			modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
			field.setAccessible(true);
			field.set(instance, value);
		}
		catch (Exception ex) {}
		
		return value;
	}
	
	/**
	 * Static private fields
	 *
	 * @param <P> Parent class type, the type of the class that owns the field
	 * @param <T> Field type, the type of the field value
	 */
	public static final class StaticFields<P, T> extends PrivateFields<P, T>
	{
		public StaticFields(Class owner, String mcpName, String name) { super(owner, mcpName, name); }
		public T Get() { return Get(null); }
		public void Set(T value) { Set(null, value); }
		
		public static final StaticFields<Packet, Map>           packetClassToIdMap = new StaticFields<Packet, Map>     (Packet.class,     "packetClassToIdMap", "a");
		public static final StaticFields<TileEntity, Map> tileEntityNameToClassMap = new StaticFields<TileEntity, Map> (TileEntity.class, "nameToClassMap",     "a");
	}

	public static final PrivateFields<Minecraft, Timer>       minecraftTimer = new PrivateFields<Minecraft, Timer>    (Minecraft.class,     "timer",           "T");
	public static final PrivateFields<RenderManager, Map>    entityRenderMap = new PrivateFields<RenderManager, Map>  (RenderManager.class, "entityRenderMap", "o");
	public static final PrivateFields<Minecraft, Profiler> minecraftProfiler = new PrivateFields<Minecraft, Profiler> (Minecraft.class,     "mcProfiler",      "I");
}

