package com.mumfrey.liteloader.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.mumfrey.liteloader.LiteMod;
import com.mumfrey.liteloader.core.EnabledModsList;
import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.core.Loadable;
import com.mumfrey.liteloader.core.LoadableMod;

/**
 * Represents a mod in the mod info screen, keeps track of mod information and provides methods
 * for displaying the mod in the mod list and drawing the selected mod info
 *
 * @author Adam Mummery-Smith
 */
public class GuiModListEntry extends Gui
{
	private static final int PANEL_HEIGHT = 32;
	private static final int PANEL_SPACING = 4;
	
	/**
	 * For text display
	 */
	private FontRenderer fontRenderer;
	
	private LiteMod modInstance;
	
	private Class<? extends LiteMod> modClass;

	/**
	 * The metadata name (id) of the mod, used as the enablement/disablement key
	 */
	private String metaName;
	
	/**
	 * Display name of the mod, disabled mods use the file/folder name
	 */
	private String name;
	
	/**
	 * Mod version string
	 */
	private String version;
	
	/**
	 * Mod author, from the metadata
	 */
	private String author = "Unknown";
	
	/**
	 * Mod URL, from the metadata
	 */
	private String url = null;
	
	/**
	 * Mod description, from metadata
	 */
	private String description = "";
	
	/**
	 * Whether the mod is currently active
	 */
	private boolean enabled;
	
	/**
	 * Whether the mod can be toggled, not all mods support this, eg. internal mods
	 */
	private boolean canBeToggled;
	
	/**
	 * Whether the mod WILL be enabled on the next startup, if the mod is active and has been disabled this
	 * will be false, and if it's currently disabled by has been toggled then it will be true 
	 */
	private boolean willBeEnabled;
	
	/**
	 * True if the mouse was over this mod on the last render
	 */
	private boolean mouseOver;
	
	private boolean external;
	
	/**
	 * Mod list entry for an ACTIVE mod
	 * 
	 * @param loader
	 * @param enabledMods
	 * @param fontRenderer
	 * @param modInstance
	 */
	GuiModListEntry(LiteLoader loader, EnabledModsList enabledMods, FontRenderer fontRenderer, LiteMod modInstance)
	{
		this.fontRenderer    = fontRenderer;
		this.modInstance     = modInstance;
		this.modClass        = modInstance.getClass();
		this.metaName        = loader.getModMetaName(this.modClass);
		this.name            = modInstance.getName();
		this.version         = modInstance.getVersion();
		this.author          = loader.getModMetaData(this.modClass, "author", "Unknown");
		this.url             = loader.getModMetaData(this.modClass, "url", null);
		this.description     = loader.getModMetaData(this.modClass, "description", "");
		this.enabled         = true;
		this.canBeToggled    = this.metaName != null && enabledMods.saveAllowed();
		this.willBeEnabled   = true;
	}
	
	/**
	 * Mod list entry for a currently disabled mod
	 * 
	 * @param loader
	 * @param enabledMods
	 * @param fontRenderer
	 * @param mod
	 */
	GuiModListEntry(LiteLoader loader, EnabledModsList enabledMods, FontRenderer fontRenderer, Loadable<?> mod)
	{
		this.fontRenderer    = fontRenderer;
		this.metaName        = mod.getIdentifier().toLowerCase();
		this.name            = mod.getDisplayName();
		this.version         = mod.getVersion();
		this.author          = mod.getAuthor();
		this.enabled         = mod.isEnabled(enabledMods, LiteLoader.getProfile());
		this.canBeToggled    = mod.isToggleable() && enabledMods.saveAllowed();
		this.willBeEnabled   = enabledMods.isEnabled(loader.getProfile(), this.metaName);
		this.external        = mod.isExternalJar();
		
		if (mod instanceof LoadableMod<?>)
		{
			this.url             = ((LoadableMod<?>)mod).getMetaValue("url", null);
			this.description     = ((LoadableMod<?>)mod).getMetaValue("description", "");
		}
	}
	
	/**
	 * Draw this list entry as a list item
	 * 
	 * @param mouseX
	 * @param mouseY
	 * @param partialTicks
	 * @param xPosition
	 * @param yPosition
	 * @param width
	 * @param selected
	 * @return
	 */
	public int drawListEntry(int mouseX, int mouseY, float partialTicks, int xPosition, int yPosition, int width, boolean selected)
	{
		int colour1 = selected ? (this.external ? 0xB047d1aa : 0xB04785D1) : 0xB0000000;
		drawGradientRect(xPosition, yPosition, xPosition + width, yPosition + PANEL_HEIGHT, colour1, 0xB0333333);
		
		this.fontRenderer.drawString(this.name, xPosition + 5, yPosition + 2, this.enabled ? (this.external ? 0xFF47d1aa : 0xFFFFFFFF) : 0xFF999999);
		this.fontRenderer.drawString("Version " + this.version, xPosition + 5, yPosition + 12, 0xFF999999);
		
		String status = this.external ? "Loaded" : "Active";
		
		if (this.canBeToggled)
		{
			if (!this.enabled && !this.willBeEnabled) status = "\2477Disabled";
			if (!this.enabled &&  this.willBeEnabled) status = "\247aEnabled on next startup"; 
			if ( this.enabled && !this.willBeEnabled) status = "\247cDisabled on next startup";
		}
		
		this.fontRenderer.drawString(status, xPosition + 5, yPosition + 22, this.external ? 0xB047d1aa : 0xFF4785D1);
		
		this.mouseOver = mouseX > xPosition && mouseX < xPosition + width && mouseY > yPosition && mouseY < yPosition + PANEL_HEIGHT; 
		drawRect(xPosition, yPosition, xPosition + 1, yPosition + PANEL_HEIGHT, this.mouseOver ? 0xFFFFFFFF : 0xFF999999);
		
		return PANEL_HEIGHT + PANEL_SPACING;
	}
	
	public int getHeight()
	{
		return PANEL_HEIGHT + PANEL_SPACING;
	}
	
	/**
	 * Draw this entry as the info page
	 * 
	 * @param mouseX
	 * @param mouseY
	 * @param partialTicks
	 * @param xPosition
	 * @param yPosition
	 * @param width
	 */
	public void drawInfo(int mouseX, int mouseY, float partialTicks, int xPosition, int yPosition, int width)
	{
		yPosition += 2;

		this.fontRenderer.drawString(this.name, xPosition + 5, yPosition, 0xFFFFFFFF); yPosition += 10;
		this.fontRenderer.drawString("Version " + this.version, xPosition + 5, yPosition, 0xFF999999); yPosition += 10;

		drawRect(xPosition + 5, yPosition, xPosition + width, yPosition + 1, 0xFF999999); yPosition += 4;

		this.fontRenderer.drawString("Authors: \2477" + this.author, xPosition + 5, yPosition, 0xFFFFFFFF); yPosition += 10;
		if (this.url != null)
		{
			this.fontRenderer.drawString(this.url, xPosition + 5, yPosition, 0xB04785D1); yPosition += 10;
		}

		drawRect(xPosition + 5, yPosition, xPosition + width, yPosition + 1, 0xFF999999); yPosition += 4;
		
		this.fontRenderer.drawSplitString(this.description, xPosition + 5, yPosition, width - 5, 0xFFFFFFFF);
	}

	/**
	 * Toggle the enablement status of this mod, if supported
	 */
	public void toggleEnabled()
	{
		if (this.canBeToggled)
		{
			this.willBeEnabled = !this.willBeEnabled;
			LiteLoader.getInstance().setModEnabled(this.metaName, this.willBeEnabled);
		}
	}
	
	public String getKey()
	{
		return this.metaName + Integer.toHexString(this.hashCode());
	}
	
	public LiteMod getModInstance()
	{
		return this.modInstance;
	}
	
	public Class<? extends LiteMod> getModClass()
	{
		return this.modClass;
	}
	
	public String getName()
	{
		return this.name;
	}

	public String getVersion()
	{
		return this.version;
	}

	public String getAuthor()
	{
		return this.author;
	}

	public String getDescription()
	{
		return this.description;
	}

	public boolean isEnabled()
	{
		return this.enabled;
	}

	public boolean canBeToggled()
	{
		return this.canBeToggled;
	}

	public boolean willBeEnabled()
	{
		return this.willBeEnabled;
	}
	
	public boolean mouseWasOver()
	{
		return this.mouseOver;
	}
}