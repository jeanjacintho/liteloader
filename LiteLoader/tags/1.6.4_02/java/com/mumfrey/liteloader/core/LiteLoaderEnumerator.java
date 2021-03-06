package com.mumfrey.liteloader.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import net.minecraft.launchwrapper.LaunchClassLoader;

import com.mumfrey.liteloader.LiteMod;
import com.mumfrey.liteloader.core.exceptions.OutdatedLoaderException;
import com.mumfrey.liteloader.launch.LiteLoaderTweaker;

/**
 * The enumerator performs all mod discovery functions for LiteLoader, this includes locating mod files to load
 * as well as searching for mod classes within the class path and discovered mod files.
 *
 * @author Adam Mummery-Smith
 */
class LiteLoaderEnumerator implements FilenameFilter
{
	private static final String OPTION_SEARCH_ZIPFILES  = "search.zipfiles";
	private static final String OPTION_SEARCH_JARFILES  = "search.jarfiles";
	private static final String OPTION_SEARCH_MODS      = "search.mods";
	private static final String OPTION_SEARCH_JAR       = "search.jar";
	private static final String OPTION_SEARCH_CLASSPATH = "search.classpath";

	/**
	 * Maximum recursion depth for mod discovery
	 */
	private static final int MAX_DISCOVERY_DEPTH = 16;

	/**
	 * Local logger reference
	 */
	private static Logger logger = Logger.getLogger("liteloader");
	
	/**
	 * Reference to the bootstrap agent 
	 */
	private final LiteLoaderBootstrap bootstrap;

	/**
	 * Reference to the launch classloader
	 */
	private final LaunchClassLoader classLoader;

	/**
	 * Array of class path entries specified to the JVM instance 
	 */
	private final String[] classPathEntries;

	/**
	 * Classes to load, mapped by class name 
	 */
	private final Map<String, Class<? extends LiteMod>> modsToLoad = new HashMap<String, Class<? extends LiteMod>>();
	
	/**
	 * URLs to add once init is completed
	 */
	private final List<ModFile> allModFiles = new ArrayList<ModFile>();
	
	/**
	 * Mod metadata from version file 
	 */
	private final Map<String, ModFile> modFiles = new HashMap<String, ModFile>();
	
	/**
	 * True if the loader is allowed to load tweak classes from mod files 
	 */
	private final boolean loadTweaks;
	
	/**
	 * True if liteloader should also search files ending with .zip
	 */
	private boolean readZipFiles = false;
	
	/**
	 * True if liteloader should also search files ending with .jar
	 */
	private boolean readJarFiles = true;

	private boolean searchModsFolder = true;
	private boolean searchProtectionDomain = true;
	private boolean searchClassPath = true;
	
	/**
	 * @param properties
	 * @param gameFolder
	 * @param classLoader
	 * @param loadTweaks
	 */
	public LiteLoaderEnumerator(LiteLoaderBootstrap bootstrap, LaunchClassLoader classLoader, boolean loadTweaks)
	{
		this.bootstrap   = bootstrap;
		this.classLoader = classLoader;
		this.loadTweaks  = loadTweaks;

		// Read the JVM class path into the local array
		this.classPathEntries = this.readClassPath();
		
		// Read the discovery settings from the properties 
		this.readSettings();
		
		// Write settings back to the properties file, in case they changed 
		this.writeSettings();
	}

	/**
	 * Get the list of all enumerated mod classes to load
	 */
	public Collection<Class<? extends LiteMod>> getModsToLoad()
	{
		return this.modsToLoad.values();
	}

	/**
	 * Get the number of mods to load
	 */
	public int modsToLoadCount()
	{
		return this.modsToLoad.size();
	}
	
	/**
	 * @return
	 */
	public boolean hasModsToLoad()
	{
		return this.modsToLoad.size() > 0;
	}

	/**
	 * Get a metadata value for the specified mod
	 * 
	 * @param modClassName
	 * @param metaDataKey
	 * @param defaultValue
	 * @return
	 */
	public String getModMetaData(Class<? extends LiteMod> modClass, String metaDataKey, String defaultValue)
	{
		ModFile modFile = this.getModFile(modClass);
		return modFile != null ? modFile.getMetaValue(metaDataKey, defaultValue) : defaultValue;
	}
	
	/**
	 * @param mod
	 * @return
	 */
	public ModFile getModFile(Class<? extends LiteMod> modClass)
	{
		return this.modFiles.get(modClass.getSimpleName());
	}

	/**
	 * Get the mod "name" metadata key, this is used for versioning, exclusivity, and enablement checks
	 * 
	 * @param modClass
	 * @return
	 */
	public String getModMetaName(Class<? extends LiteMod> modClass)
	{
		String modClassName = modClass.getSimpleName();
		if (!this.modFiles.containsKey(modClassName)) return null;
		return this.modFiles.get(modClassName).getModName().toLowerCase();
	}
	
	/**
	 * Get the discovery settings from the properties file
	 */
	private void readSettings()
	{
		this.readZipFiles           = this.bootstrap.getAndStoreBooleanProperty(OPTION_SEARCH_ZIPFILES,  false);
		this.readJarFiles           = this.bootstrap.getAndStoreBooleanProperty(OPTION_SEARCH_JARFILES,  true);
		this.searchModsFolder       = this.bootstrap.getAndStoreBooleanProperty(OPTION_SEARCH_MODS,      true);
		this.searchProtectionDomain = this.bootstrap.getAndStoreBooleanProperty(OPTION_SEARCH_JAR,       true);
		this.searchClassPath        = this.bootstrap.getAndStoreBooleanProperty(OPTION_SEARCH_CLASSPATH, true);
		
		if (!this.searchModsFolder && !this.searchProtectionDomain && !this.searchClassPath)
		{
			LiteLoaderEnumerator.logWarning("Invalid configuration, no search locations defined. Enabling all search locations.");
			
			this.searchModsFolder       = true;
			this.searchProtectionDomain = true;
			this.searchClassPath        = true;
		}
	}

	/**
	 * Write settings
	 */
	private void writeSettings()
	{
		this.bootstrap.setBooleanProperty(OPTION_SEARCH_ZIPFILES,  this.readZipFiles);
		this.bootstrap.setBooleanProperty(OPTION_SEARCH_JARFILES,  this.readJarFiles);
		this.bootstrap.setBooleanProperty(OPTION_SEARCH_MODS,      this.searchModsFolder);
		this.bootstrap.setBooleanProperty(OPTION_SEARCH_JAR,       this.searchProtectionDomain);
		this.bootstrap.setBooleanProperty(OPTION_SEARCH_CLASSPATH, this.searchClassPath);
	}

	/**
	 * Enumerate the "mods" folder to find mod files
	 */
	protected void discoverMods()
	{
		if (this.searchClassPath)
		{
			this.findTweaksInClassPath();
		}
		
		if (this.searchModsFolder)
		{
			// Find and enumerate the "mods" folder
			File modsFolder = this.bootstrap.getModsFolder();
			if (modsFolder.exists() && modsFolder.isDirectory())
			{
				LiteLoaderEnumerator.logInfo("Mods folder found, searching %s", modsFolder.getPath());
				this.findModFiles(modsFolder, false);
				
				File versionedModsFolder = new File(modsFolder, LiteLoaderBootstrap.VERSION.getMinecraftVersion());
				if (versionedModsFolder.exists() && versionedModsFolder.isDirectory())
				{
					LiteLoaderEnumerator.logInfo("Versioned mods folder found, searching %s", versionedModsFolder.getPath());
					this.findModFiles(versionedModsFolder, true);
				}
				
				LiteLoaderEnumerator.logInfo("Found %d mod file(s)", this.allModFiles.size());
			}
		}
	}
	
	/**
	 * Enumerate class path and discovered mod files to find mod classes
	 */
	protected void discoverModClasses()
	{
		try
		{
			// Inject mod files discovered earlier into the class loader
			this.injectDiscoveredModFiles();
			
			// then search through all sources to find mod classes
			this.findModClasses();
		}
		catch (Throwable th)
		{
			LiteLoaderEnumerator.logger.log(Level.WARNING, "Mod class discovery failed", th);
			return;
		}
	}

	/**
	 * Injects all external mod files into the launch classloader's class path
	 */
	private void injectDiscoveredModFiles()
	{
		LiteLoaderEnumerator.logInfo("Injecting external mods into class path...");
		
		for (ModFile file : this.allModFiles)
		{
			try
			{
				if (file.injectIntoClassPath(this.classLoader, false))
				{
					LiteLoaderEnumerator.logInfo("Successfully injected mod file '%s' into classpath", file.getAbsolutePath());
				}
			}
			catch (MalformedURLException ex)
			{
				LiteLoaderEnumerator.logWarning("Error injecting '%s' into classPath. The mod will not be loaded", file.getAbsolutePath());
			}
		}
	}

	/**
	 * Reads the class path entries that were supplied to the JVM and returns them as an array
	 */
	private String[] readClassPath()
	{
		LiteLoaderEnumerator.logInfo("Enumerating class path...");
		
		String classPath = System.getProperty("java.class.path");
		String classPathSeparator = System.getProperty("path.separator");
		String[] classPathEntries = classPath.split(classPathSeparator);
		
		LiteLoaderEnumerator.logInfo("Class path separator=\"%s\"", classPathSeparator);
		LiteLoaderEnumerator.logInfo("Class path entries=(\n   classpathEntry=%s\n)", classPath.replace(classPathSeparator, "\n   classpathEntry="));
		return classPathEntries;
	}

	/**
	 * Search class path entries for mod class path entries which contain tweaks, mainly for dev environment purposes
	 */
	private void findTweaksInClassPath()
	{
		LiteLoaderEnumerator.logInfo("Discovering tweaks on class path...");

		for (String classPathPart : this.classPathEntries)
		{
			File packagePath = new File(classPathPart);
			ClassPathMod classPathMod = new ClassPathMod(packagePath, null);
			
			if (classPathMod.hasTweakClass() || classPathMod.hasClassTransformer())
			{
				this.addTweaksFromMod(classPathMod);
			}
		}
	}
	
	/**
	 * For FilenameFilter interface
	 * 
	 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
	 */
	@Override
	public boolean accept(File dir, String fileName)
	{
		fileName = fileName.toLowerCase();
		return                       fileName.endsWith(".litemod")
			|| (this.readZipFiles && fileName.endsWith(".zip"))
			|| (this.readJarFiles && fileName.endsWith(".jar"));
	}

	/**
	 * Find mod files in the "mods" folder
	 * 
	 * @param modFolder Folder to search
	 * @param isVersionedModFolder This is true if we will also allow non-metadata-containing jars to be examined for tweaks
	 */
	protected void findModFiles(File modFolder, boolean isVersionedModFolder)
	{
		Map<String, TreeSet<ModFile>> versionOrderingSets = new HashMap<String, TreeSet<ModFile>>();
		
		for (File modFile : modFolder.listFiles(this))
		{
			try
			{
				ZipFile modZip = new ZipFile(modFile);
				ZipEntry versionEntry = modZip.getEntry("litemod.json");
				ZipEntry legacyVersionEntry = modZip.getEntry("version.txt");

				// Check for a version file
				if (versionEntry != null)
				{
					String strVersion = null;
					try
					{
						strVersion = ModFile.zipEntryToString(modZip, versionEntry);
					}
					catch (IOException ex)
					{
						LiteLoaderEnumerator.logWarning("Error reading version data from %s", modZip.getName());
					}
					
					if (strVersion != null)
					{
						ModFile modFileInfo = new ModFile(modFile, strVersion);
						
						if (modFileInfo.isValid())
						{
							// Only add the mod if the version matches, we add candidates to the versionOrderingSets in
							// order to determine the most recent version available.
							if (LiteLoaderBootstrap.VERSION.isVersionSupported(modFileInfo.getVersion()))
							{
								if (!versionOrderingSets.containsKey(modFileInfo.getName()))
								{
									versionOrderingSets.put(modFileInfo.getModName(), new TreeSet<ModFile>());
								}
								
								LiteLoaderEnumerator.logInfo("Considering valid mod file: %s", modFileInfo.getAbsolutePath());
								versionOrderingSets.get(modFileInfo.getModName()).add(modFileInfo);
							}
							else
							{
								LiteLoaderEnumerator.logInfo("Not adding invalid or outdated mod file: %s", modFile.getAbsolutePath());
							}
						}
					}
				}
				else if (legacyVersionEntry != null)
				{
					LiteLoaderEnumerator.logWarning("version.txt is no longer supported, ignoring outdated mod file: %s", modFile.getAbsolutePath());
				}
				else if (isVersionedModFolder && this.loadTweaks && this.readJarFiles && modFile.getName().toLowerCase().endsWith(".jar"))
				{
					this.addTweaksFromJar(modFile);
				}
				
				modZip.close();
			}
			catch (Exception ex)
			{
				LiteLoaderEnumerator.logInfo("Error enumerating '%s': Invalid zip file or error reading file", modFile.getAbsolutePath());
			}
		}

		// Copy the first entry in every version set into the modfiles list
		for (Entry<String, TreeSet<ModFile>> modFileEntry : versionOrderingSets.entrySet())
		{
			ModFile newestVersion = modFileEntry.getValue().iterator().next();

			LiteLoaderEnumerator.logInfo("Adding newest valid mod file '%s' at revision %.4f: ", newestVersion.getAbsolutePath(), newestVersion.getRevision());
			this.allModFiles.add(newestVersion);
			
			if (this.loadTweaks)
			{
				try
				{
					this.addTweaksFromMod(newestVersion);
				}
				catch (Throwable th)
				{
					LiteLoaderEnumerator.logWarning("Error adding tweaks from '%s'", newestVersion.getAbsolutePath());
				}
			}
		}
	}
	
	private void addTweaksFromMod(ModFile modFile)
	{
		if (modFile.hasTweakClass())
		{
			this.addTweakFrom(modFile, modFile.getTweakClassName(), null);
		}
		else if (modFile.isFile())
		{
			this.addTweaksFromJar(modFile);
		}
		
		if (modFile.hasClassTransformer())
		{
			this.addClassTransformerFrom(modFile, modFile.getClassTransformerClassName());
		}
	}

	/**
	 * @param jarFile
	 * @throws IOException
	 */
	private void addTweaksFromJar(File jarFile)
	{
		JarFile jar = null;
		
		try
		{
			jar = new JarFile(jarFile);
			if (jar.getManifest() != null)
			{
				LiteLoaderEnumerator.logInfo("Searching for tweaks in '%s'", jarFile.getName());
				Attributes manifestAttributes = jar.getManifest().getMainAttributes();
				
				String tweakClass = manifestAttributes.getValue("TweakClass");
				if (tweakClass != null)
				{
					String[] classPathEntries = null;
					String classPath = manifestAttributes.getValue("Class-Path");
					if (classPath != null)
					{
						classPathEntries = classPath.split(" ");
					}
					
					this.addTweakFrom(jarFile, tweakClass, classPathEntries);
				}
			}
		}
		catch (Exception ex)
		{
			LiteLoaderEnumerator.logWarning("Error parsing tweak class manifest entry in '%s'", jarFile.getAbsolutePath());
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
	
	private void addTweakFrom(File jarFile, String tweakClass, String[] classPathEntries)
	{
		try
		{
			LiteLoaderEnumerator.logInfo("Mod file '%s' provides tweakClass '%s', adding to Launch queue", jarFile.getName(), tweakClass);
			if (LiteLoaderTweaker.addTweaker(tweakClass))
			{
				LiteLoaderEnumerator.logInfo("tweakClass '%s' was successfully added", tweakClass);
				this.injectIntoClassLoader(jarFile);
				
				if (classPathEntries != null)
				{
					for (String classPathEntry : classPathEntries)
					{
						try
						{
							File classPathJar = new File(this.bootstrap.getGameDirectory(), classPathEntry);
							URL classPathJarUrl = classPathJar.toURI().toURL();
							
							LiteLoaderEnumerator.logInfo("Adding Class-Path entry: %s", classPathEntry); 
							LiteLoaderTweaker.addURLToParentClassLoader(classPathJarUrl);
							this.classLoader.addURL(classPathJarUrl);
						}
						catch (MalformedURLException ex) {}
					}
				}
			}
		}
		catch (MalformedURLException ex)
		{
		}
	}

	private void addClassTransformerFrom(File jarFile, String classTransformerClass)
	{
		try
		{
			LiteLoaderEnumerator.logInfo("Mod file '%s' provides classTransformer '%s', adding to class loader", jarFile.getName(), classTransformerClass);
			if (LiteLoaderTweaker.addClassTransformer(classTransformerClass))
			{
				LiteLoaderEnumerator.logInfo("classTransformer '%s' was successfully added", classTransformerClass);
				this.injectIntoClassLoader(jarFile);
			}
		}
		catch (MalformedURLException ex)
		{
		}
	}
	
	/**
	 * @param jarFile
	 * @throws MalformedURLException
	 */
	private void injectIntoClassLoader(File jarFile) throws MalformedURLException
	{
		if (jarFile instanceof ModFile)
		{
			((ModFile)jarFile).injectIntoClassPath(this.classLoader, true);
		}
		else
		{
			LiteLoaderTweaker.addURLToParentClassLoader(jarFile.toURI().toURL());
			this.classLoader.addURL(jarFile.toURI().toURL());
		}
	}
	
	/**
	 * Find mod classes in the class path and enumerated mod files list
	 * @param classPathEntries Java class path split into string entries
	 */
	private void findModClasses()
	{
		if (this.searchProtectionDomain || this.searchClassPath)
			LiteLoaderEnumerator.logInfo("Discovering mods on class path...");
		
		if (this.searchProtectionDomain)
		{
			try
			{
				this.findModsInProtectionDomain();
			}
			catch (Throwable th)
			{
				LiteLoaderEnumerator.logWarning("Error loading from local class path: %s", th.getMessage());
			}
		}
		
		if (this.searchClassPath)
		{
			// Search through the class path and find mod classes
			this.findModsInClassPath();
		}
		
		// Search through mod files and find mod classes
		this.findModsInFiles();
		
		LiteLoaderEnumerator.logInfo("Mod class discovery completed");
	}

	/**
	 * @param modsToLoad
	 * @throws MalformedURLException
	 * @throws URISyntaxException
	 * @throws UnsupportedEncodingException
	 */
	@SuppressWarnings("unchecked")
	private void findModsInProtectionDomain() throws MalformedURLException, URISyntaxException, UnsupportedEncodingException
	{
		LiteLoaderEnumerator.logInfo("Searching protection domain code source...");
		
		File packagePath = null;
		
		URL protectionDomainLocation = LiteLoaderEnumerator.class.getProtectionDomain().getCodeSource().getLocation();
		if (protectionDomainLocation != null)
		{
			if (protectionDomainLocation.toString().indexOf('!') > -1 && protectionDomainLocation.toString().startsWith("jar:"))
			{
				protectionDomainLocation = new URL(protectionDomainLocation.toString().substring(4, protectionDomainLocation.toString().indexOf('!')));
			}
			
			packagePath = new File(protectionDomainLocation.toURI());
		}
		else
		{
			// Fix (?) for forge and other mods which mangle the protection domain
			String reflectionClassPath = LiteLoaderEnumerator.class.getResource("/com/mumfrey/liteloader/core/LiteLoader.class").getPath();
			
			if (reflectionClassPath.indexOf('!') > -1)
			{
				reflectionClassPath = URLDecoder.decode(reflectionClassPath, "UTF-8");
				packagePath = new File(reflectionClassPath.substring(5, reflectionClassPath.indexOf('!')));
			}
		}
		
		if (packagePath != null)
		{
			LinkedList<Class<?>> modClasses = LiteLoaderEnumerator.getSubclassesFor(packagePath, this.classLoader, LiteMod.class, "LiteMod");
			
			for (Class<?> mod : modClasses)
			{
				if (this.modsToLoad.containsKey(mod.getSimpleName()))
				{
					LiteLoaderEnumerator.logWarning("Mod name collision for mod with class '%s', maybe you have more than one copy?", mod.getSimpleName());
				}
				
				this.modsToLoad.put(mod.getSimpleName(), (Class<? extends LiteMod>)mod);
			}
			
			if (modClasses.size() > 0)
				LiteLoaderEnumerator.logInfo("Found %s potential matches", modClasses.size());
		}
	}

	@SuppressWarnings("unchecked")
	private void findModsInClassPath()
	{
		for (String classPathPart : this.classPathEntries)
		{
			LiteLoaderEnumerator.logInfo("Searching %s...", classPathPart);
			
			File packagePath = new File(classPathPart);
			LinkedList<Class<?>> modClasses = LiteLoaderEnumerator.getSubclassesFor(packagePath, this.classLoader, LiteMod.class, "LiteMod");
			
			for (Class<?> mod : modClasses)
			{
				if (this.modsToLoad.containsKey(mod.getSimpleName()))
				{
					LiteLoaderEnumerator.logWarning("Mod name collision for mod with class '%s', maybe you have more than one copy?", mod.getSimpleName());
				}
				
				this.modsToLoad.put(mod.getSimpleName(), (Class<? extends LiteMod>)mod);
				this.modFiles.put(mod.getSimpleName(), new ClassPathMod(packagePath, mod.getSimpleName().substring(7).toLowerCase()));
			}
			
			if (modClasses.size() > 0)
				LiteLoaderEnumerator.logInfo("Found %s potential matches", modClasses.size());
		}
	}

	/**
	 */
	@SuppressWarnings("unchecked")
	private void findModsInFiles()
	{
		for (ModFile modFile : this.allModFiles)
		{
			LiteLoaderEnumerator.logInfo("Searching %s...", modFile.getAbsolutePath());
			
			LinkedList<Class<?>> modClasses = LiteLoaderEnumerator.getSubclassesFor(modFile, this.classLoader, LiteMod.class, "LiteMod");
			
			for (Class<?> mod : modClasses)
			{
				if (this.modsToLoad.containsKey(mod.getSimpleName()))
				{
					LiteLoaderEnumerator.logWarning("Mod name collision for mod with class '%s', maybe you have more than one copy?", mod.getSimpleName());
				}
				
				this.modsToLoad.put(mod.getSimpleName(), (Class<? extends LiteMod>)mod);
				this.modFiles.put(mod.getSimpleName(), modFile);
			}
			
			if (modClasses.size() > 0)
				LiteLoaderEnumerator.logInfo("Found %s potential matches", modClasses.size());
		}
	}

	/**
	 * Enumerate classes on the classpath which are subclasses of the specified
	 * class
	 * 
	 * @param superClass
	 * @return
	 */
	private static LinkedList<Class<?>> getSubclassesFor(File packagePath, ClassLoader classloader, Class<?> superClass, String prefix)
	{
		LinkedList<Class<?>> classes = new LinkedList<Class<?>>();
		
		try
		{
			if (packagePath.isDirectory())
			{
				LiteLoaderEnumerator.enumerateDirectory(prefix, superClass, classloader, classes, packagePath);
			}
			else if (packagePath.isFile() && (packagePath.getName().endsWith(".jar") || packagePath.getName().endsWith(".zip") || packagePath.getName().endsWith(".litemod")))
			{
				LiteLoaderEnumerator.enumerateCompressedPackage(prefix, superClass, classloader, classes, packagePath);
			}
		}
		catch (OutdatedLoaderException ex)
		{
			classes.clear();
			LiteLoaderEnumerator.logWarning("Error searching in '%s', missing API component '%s', your loader is probably out of date", packagePath, ex.getMessage());
		}
		catch (Throwable th)
		{
			LiteLoaderEnumerator.logger.log(Level.WARNING, "Enumeration error", th);
		}
		
		return classes;
	}

	/**
	 * @param superClass
	 * @param classloader
	 * @param classes
	 * @param packagePath
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void enumerateCompressedPackage(String prefix, Class<?> superClass, ClassLoader classloader, LinkedList<Class<?>> classes, File packagePath) throws FileNotFoundException, IOException
	{
		FileInputStream fileinputstream = new FileInputStream(packagePath);
		ZipInputStream zipinputstream = new ZipInputStream(fileinputstream);
		
		ZipEntry zipentry = null;
		
		do
		{
			zipentry = zipinputstream.getNextEntry();
			
			if (zipentry != null && zipentry.getName().endsWith(".class"))
			{
				String classFileName = zipentry.getName();
				String className = classFileName.lastIndexOf('/') > -1 ? classFileName.substring(classFileName.lastIndexOf('/') + 1) : classFileName;
				
				if (prefix == null || className.startsWith(prefix))
				{
					try
					{
						String fullClassName = classFileName.substring(0, classFileName.length() - 6).replaceAll("/", ".");
						LiteLoaderEnumerator.checkAndAddClass(classloader, superClass, classes, fullClassName);
					}
					catch (Exception ex) {}
				}
			}
		} while (zipentry != null);
		
		fileinputstream.close();
	}

	/**
	 * Recursive function to enumerate classes inside a classpath folder
	 * 
	 * @param superClass
	 * @param classloader
	 * @param classes
	 * @param packagePath
	 * @param packageName
	 * @throws OutdatedLoaderException 
	 */
	private static void enumerateDirectory(String prefix, Class<?> superClass, ClassLoader classloader, LinkedList<Class<?>> classes, File packagePath) throws OutdatedLoaderException
	{
		LiteLoaderEnumerator.enumerateDirectory(prefix, superClass, classloader, classes, packagePath, "", 0);
	}

	/**
	 * Recursive function to enumerate classes inside a classpath folder
	 * 
	 * @param superClass
	 * @param classloader
	 * @param classes
	 * @param packagePath
	 * @param packageName
	 * @throws OutdatedLoaderException 
	 */
	private static void enumerateDirectory(String prefix, Class<?> superClass, ClassLoader classloader, LinkedList<Class<?>> classes, File packagePath, String packageName, int depth) throws OutdatedLoaderException
	{
		// Prevent crash due to broken recursion
		if (depth > MAX_DISCOVERY_DEPTH)
			return;
		
		File[] classFiles = packagePath.listFiles();
		
		for (File classFile : classFiles)
		{
			if (classFile.isDirectory())
			{
				LiteLoaderEnumerator.enumerateDirectory(prefix, superClass, classloader, classes, classFile, packageName + classFile.getName() + ".", depth + 1);
			}
			else
			{
				if (classFile.getName().endsWith(".class") && (prefix == null || classFile.getName().startsWith(prefix)))
				{
					String classFileName = classFile.getName();
					String className = packageName + classFileName.substring(0, classFileName.length() - 6);
					LiteLoaderEnumerator.checkAndAddClass(classloader, superClass, classes, className);
				}
			}
		}
	}

	/**
	 * @param classloader
	 * @param superClass
	 * @param classes
	 * @param className
	 * @throws OutdatedLoaderException 
	 */
	private static void checkAndAddClass(ClassLoader classloader, Class<?> superClass, LinkedList<Class<?>> classes, String className) throws OutdatedLoaderException
	{
		if (className.indexOf('$') > -1)
			return;
		
		try
		{
			Class<?> subClass = classloader.loadClass(className);
			
			if (subClass != null && !superClass.equals(subClass) && superClass.isAssignableFrom(subClass) && !subClass.isInterface() && !classes.contains(subClass))
			{
				classes.add(subClass);
			}
		}
		catch (Throwable th)
		{
			String missingClassName = th.getCause().getMessage();
			if (th.getCause() instanceof NoClassDefFoundError && missingClassName != null)
			{
				if (missingClassName.startsWith("com/mumfrey/liteloader/"))
				{
					throw new OutdatedLoaderException(missingClassName.substring(missingClassName.lastIndexOf('/') + 1));
				}
			}
			
			LiteLoaderEnumerator.logger.log(Level.WARNING, "checkAndAddClass error", th);
		}
	}

	private static void logInfo(String string, Object... args)
	{
		LiteLoaderEnumerator.logger.info(String.format(string, args));
	}

	private static void logWarning(String string, Object... args)
	{
		LiteLoaderEnumerator.logger.warning(String.format(string, args));
	}
}
