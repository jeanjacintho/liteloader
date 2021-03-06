package com.mumfrey.liteloader.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import com.mumfrey.liteloader.util.PrivateFields;

import net.minecraft.src.*;

/**
 * Proxy packet which we will register in place of the original chat packet. The class will proxy the function calls
 * through to the replaced class via reflection if the original (replaced) class is NOT the basic Packet3Chat (this
 * is to maintain compatibility with things like WorldEditCUI. 
 * 
 * @author Adam Mummery-Smith
 *
 */
public class HookChat extends Packet3Chat
{
	/**
	 * True if this class was registered with the base class
	 */
	private static boolean registered = false;
	
	/**
	 * Handler module which is registered to handle inbound chat packets
	 */
	private static LiteLoader packetHandler; 
	
	/**
	 * Class which was overridden and will be instanced for new packets
	 */
	private static Class<? extends Packet> proxyClass;
	
	/**
	 * Instance of the proxy packet for this packet instance
	 */
	private Packet proxyPacket;
	
	/**
	 * Create a new chat packet proxy
	 */
	public HookChat()
	{
		super();
		
		try
		{
			if (proxyClass != null)
			{
				proxyPacket = proxyClass.newInstance();
			}
		}
		catch (Exception ex) {}
	}
	
	/**
	 * Create a new chat proxy with the specified message
	 * @param message
	 */
	public HookChat(String message)
	{
		super(message);

		try
		{
			if (proxyClass != null)
			{
				proxyPacket = proxyClass.newInstance();
				
				if (proxyPacket instanceof Packet3Chat)
				{
					((Packet3Chat)proxyPacket).message = this.message;
				}
			}
		}
		catch (Exception ex) {}
	}

	@Override
	public void readPacketData(DataInputStream datainputstream) throws IOException
	{
		if (proxyPacket != null)
		{
			proxyPacket.readPacketData(datainputstream);
			this.message = ((Packet3Chat)proxyPacket).message;
		}
		else
			super.readPacketData(datainputstream);
	}

	@Override
	public void writePacketData(DataOutputStream dataoutputstream) throws IOException
	{
		if (proxyPacket != null)
			proxyPacket.writePacketData(dataoutputstream);
		else
			super.writePacketData(dataoutputstream);
	}

	@Override
	public void processPacket(NetHandler nethandler)
	{
		if (packetHandler == null || packetHandler.onChat(this))
		{
			if (proxyPacket != null)
				proxyPacket.processPacket(nethandler);
			else
				super.processPacket(nethandler);
		}
	}

	@Override
	public int getPacketSize()
	{
		if (proxyPacket != null)
			return proxyPacket.getPacketSize();
		else
			return super.getPacketSize();
	}
	
	/**
	 * Register the specified handler as the packet handler for this packet
	 * @param handler
	 */
	public static void RegisterPacketHandler(LiteLoader handler)
	{
		packetHandler = handler;
	}
	
	/**
	 * Register this packet as the new packet for packet ID 3
	 */
    public static void Register()
    {
    	Register(false);
    }

    /**
     * Register this packet as the new packet for packet ID 3 and optionally force re-registration even
     * if registration was performed already.
     * 
     * @param force Force registration even if registration was already performed previously.
     */
    @SuppressWarnings("unchecked")
	public static void Register(boolean force)
	{
		if (!registered || force)
		{
			try
			{
			    IntHashMap packetIdToClassMap = Packet.packetIdToClassMap;
			    proxyClass = (Class<? extends Packet>)packetIdToClassMap.lookup(3);
			    
			    if (proxyClass.equals(Packet3Chat.class))
			    {
			    	proxyClass = null;
			    }
			    
			    packetIdToClassMap.removeObject(3);
			    packetIdToClassMap.addKey(3, HookChat.class);

			    Map packetClassToIdMap = PrivateFields.StaticFields.packetClassToIdMap.Get();
			    packetClassToIdMap.put(HookChat.class, Integer.valueOf(3));
			    
			    registered = true;
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}
}
