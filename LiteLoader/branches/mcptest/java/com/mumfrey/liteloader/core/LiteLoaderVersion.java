package com.mumfrey.liteloader.core;

import java.util.HashSet;
import java.util.Set;

import com.mumfrey.liteloader.update.UpdateSite;

/**
 * LiteLoader version table
 *
 * @author Adam Mummery-Smith
 * @version 1.7.2_05
 */
public enum LiteLoaderVersion
{
	LEGACY(0, 0, "-", "-", "-"),
	
	MC_1_5_2_R1(9,  0,          "1.5.2", "1.5.2",    "1.5.2"          ),
	MC_1_6_1_R0(11, 0,          "1.6.1", "1.6.1",    "1.6.1", "1.6.r1"),
	MC_1_6_1_R1(11, 0,          "1.6.1", "1.6.1",    "1.6.1", "1.6.r1"),
	MC_1_6_2_R0(12, 0,          "1.6.2", "1.6.2",    "1.6.2", "1.6.r2"),
	MC_1_6_2_R1(12, 1374025480, "1.6.2", "1.6.2_01", "1.6.2", "1.6.r2"),
	MC_1_6_2_R2(13, 1374709543, "1.6.2", "1.6.2_02", "1.6.2", "1.6.r2"),
	MC_1_6_2_R3(14, 1375228794, "1.6.2", "1.6.2_03", "1.6.2", "1.6.r2"),
	MC_1_6_2_R4(15, 1375662298, "1.6.2", "1.6.2_04", "1.6.2", "1.6.r2"),
	MC_1_6_3_R0(16, 1375662298, "1.6.3", "1.6.3",    "1.6.3", "1.6.r3"),
	MC_1_6_4_R0(17, 1380279938, "1.6.4", "1.6.4",    "1.6.4", "1.6.r4"),
	MC_1_6_4_R1(18, 1380796916, "1.6.4", "1.6.4_01", "1.6.4", "1.6.r4"),
	MC_1_6_4_R2(19, 1380796916, "1.6.4", "1.6.4_02", "1.6.4", "1.6.r4"),
	MC_1_7_2_R0(20, 1386027226, "1.7.2", "1.7.2",    "1.7.2", "1.7.r1"),
	MC_1_7_2_R1(21, 1388455995, "1.7.2", "1.7.2_01", "1.7.2_01"),
	MC_1_7_2_R2(22, 1391815963, "1.7.2", "1.7.2_02", "1.7.2_02"),
	MC_1_7_2_R3(23, 1391890695, "1.7.2", "1.7.2_03", "1.7.2_02", "1.7.2_03"),
	MC_1_7_2_R4(24, 1392487926, "1.7.2", "1.7.2_04", "1.7.2_02", "1.7.2_03", "1.7.2_04"),
	MC_1_7_2_R5(25, 0,          "1.7.2", "1.7.2_05", "1.7.2_02", "1.7.2_03", "1.7.2_04", "1.7.2_05");
	
	/**
	 * Current loader version
	 */
	public static final LiteLoaderVersion CURRENT = LiteLoaderVersion.MC_1_7_2_R5;

	private static final String UPDATE_SITE_URL = "http://dl.liteloader.com/versions/";
	private static final String UPDATE_SITE_VERSIONS_JSON = "versions.json";
	private static final String UPDATE_SITE_ARTEFACT_NAME = "com.mumfrey:liteloader";
	
	private static final UpdateSite updateSite = new UpdateSite(UPDATE_SITE_URL, UPDATE_SITE_VERSIONS_JSON, LiteLoaderVersion.CURRENT.getMinecraftVersion(), UPDATE_SITE_ARTEFACT_NAME, LiteLoaderVersion.CURRENT.getReleaseTimestamp());
	
	private final int revision;
	
	private final long timestamp;
	
	private final String minecraftVersion;
	
	private final String loaderVersion;
	
	private final Set<String> supportedVersions = new HashSet<String>();

	private LiteLoaderVersion(int revision, long timestamp, String minecraftVersion, String loaderVersion, String... supportedVersions)
	{
		this.revision = revision;
		this.timestamp = timestamp;
		this.minecraftVersion = minecraftVersion;
		this.loaderVersion = loaderVersion;
		
		for (String supportedVersion : supportedVersions)
			this.supportedVersions.add(supportedVersion);
	}

	public int getLoaderRevision()
	{
		return this.revision;
	}
	
	public long getReleaseTimestamp()
	{
		return this.timestamp;
	}

	public String getMinecraftVersion()
	{
		return this.minecraftVersion;
	}

	public String getLoaderVersion()
	{
		return this.loaderVersion;
	}
	
	public static LiteLoaderVersion getVersionFromRevision(int revision)
	{
		for (LiteLoaderVersion version : LiteLoaderVersion.values())
		{
			if (version.getLoaderRevision() == revision)
				return version;
		}
		
		return LiteLoaderVersion.LEGACY;
	}
	
	public static int getRevisionFromVersion(String versionString)
	{
		for (LiteLoaderVersion version : LiteLoaderVersion.values())
		{
			if (version.getLoaderVersion().equals(versionString))
				return version.getLoaderRevision();
		}
		
		return LiteLoaderVersion.LEGACY.getLoaderRevision();
	}

	public boolean isVersionSupported(String version)
	{
		return this.supportedVersions.contains(version);
	}
	
	@Override
	public String toString()
	{
		return this == LiteLoaderVersion.LEGACY ? "Unknown" : this.loaderVersion;
	}
	
	public static UpdateSite getUpdateSite()
	{
		return LiteLoaderVersion.updateSite;
	}
}
