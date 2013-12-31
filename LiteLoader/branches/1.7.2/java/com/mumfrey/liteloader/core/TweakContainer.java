package com.mumfrey.liteloader.core;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mumfrey.liteloader.launch.LiteLoaderTweaker;

import net.minecraft.launchwrapper.LaunchClassLoader;

public class TweakContainer extends File implements Loadable<File>, Injectable
{
	private static final Pattern versionPattern = Pattern.compile("([0-9]+\\.)+[0-9]+([_A-Z0-9]+)?");

	private static final long serialVersionUID = 1L;

	/**
	 * Local logger reference
	 */
	private static Logger logger = Logger.getLogger("liteloader");

	/**
	 * True once this file has been injected into the class path 
	 */
	protected boolean injected;
	
	/**
	 * Position to inject the mod file at in the class path, if blank injects at the bottom as usual, alternatively
	 * the developer can specify "top" to inject at the top, "base" to inject above the game jar, or "above: name" to
	 * inject above a specified other library matching "name".
	 */
	protected String injectAt;

	/**
	 * Name of the tweak class
	 */
	protected String tweakClassName;
	
	/**
	 * Class path entries read from jar metadata
	 */
	protected String[] classPathEntries = null;

	protected String displayName;

	protected String version = "Unknown";

	protected String author;

	/**
	 * Create a new tweak container wrapping the specified file
	 */
	public TweakContainer(File parent)
	{
		super(parent.getAbsolutePath());
		this.displayName = this.getName();
		this.guessVersionFromName();
		this.readMetaData();
	}

	/**
	 * ctor for subclasses
	 */
	protected TweakContainer(String pathname)
	{
		super(pathname);
		this.displayName = this.getName();
	}
	
	private void guessVersionFromName()
	{
		Matcher versionPatternMatcher = TweakContainer.versionPattern.matcher(this.getName());
		while (versionPatternMatcher.find())
			this.version = versionPatternMatcher.group();
	}
	
	/**
	 * Search for tweaks in this file
	 */
	private void readMetaData()
	{
		JarFile jar = null;
		
		try
		{
			jar = new JarFile(this);
			if (jar.getManifest() != null)
			{
				TweakContainer.logInfo("Searching for tweaks in '%s'", this.getName());
				Attributes manifestAttributes = jar.getManifest().getMainAttributes();
				
				this.tweakClassName = manifestAttributes.getValue("TweakClass");
				if (this.tweakClassName != null)
				{
					String classPath = manifestAttributes.getValue("Class-Path");
					if (classPath != null)
					{
						this.classPathEntries = classPath.split(" ");
					}
				}
				
				if (manifestAttributes.getValue("TweakName") != null)
					this.displayName = manifestAttributes.getValue("TweakName");
				
				if (manifestAttributes.getValue("TweakVersion") != null)
					this.version = manifestAttributes.getValue("TweakVersion");
				
				if (manifestAttributes.getValue("TweakAuthor") != null)
					this.version = manifestAttributes.getValue("TweakAuthor");
			}
		}
		catch (Exception ex)
		{
			TweakContainer.logWarning("Error parsing manifest entries in '%s'", this.getAbsolutePath());
		}
		finally
		{
			try
			{
				if (jar != null) jar.close();
			}
			catch (IOException ex) {}
		}
	}
	
	@Override
	public File getTarget()
	{
		return this;
	}
	
	@Override
	public String getLocation()
	{
		return this.getAbsolutePath();
	}
	
	@Override
	public URL getURL() throws MalformedURLException
	{
		return this.toURI().toURL();
	}
	
	@Override
	public String getIdentifier()
	{
		return this.getName().toLowerCase();
	}
	
	public boolean hasTweakClass()
	{
		return this.tweakClassName != null;
	}
	
	public String getTweakClassName()
	{
		return this.tweakClassName;
	}
	
	public String[] getClassPathEntries()
	{
		return this.classPathEntries;
	}
	
	public boolean hasClassTransformers()
	{
		return false;
	}
	
	public List<String> getClassTransformerClassNames()
	{
		return new ArrayList<String>();
	}

	
	@Override
	public boolean isInjected()
	{
		return this.injected;
	}
	
	@Override
	public boolean injectIntoClassPath(LaunchClassLoader classLoader, boolean injectIntoParent) throws MalformedURLException
	{
		if (!this.injected)
		{
			if (injectIntoParent)
			{
				LiteLoaderTweaker.addURLToParentClassLoader(this.getURL());
			}
			
			classLoader.addURL(this.getURL());
			this.injected = true;
			return true;
		}
		
		return false;
	}

	@Override
	public String getDisplayName()
	{
		return this.displayName;
	}

	@Override
	public String getVersion()
	{
		return this.version;
	}
	
	@Override
	public String getAuthor()
	{
		return this.author;
	}
	
	@Override
	public boolean isExternalJar()
	{
		return true;
	}

	@Override
	public boolean isToggleable()
	{
		return false;
	}
	
	@Override
	public boolean isEnabled(EnabledModsList enabledModsList, String profile)
	{
		return enabledModsList.isEnabled(profile, this.getIdentifier());
	}

	private static void logInfo(String string, Object... args)
	{
		TweakContainer.logger.info(String.format(string, args));
	}

	private static void logWarning(String string, Object... args)
	{
		TweakContainer.logger.warning(String.format(string, args));
	}
}