package com.mumfrey.liteloader.client.gui;

public interface ModListContainer
{
	public abstract GuiLiteLoaderPanel getParentScreen();

	public abstract void setEnableButtonVisible(boolean visible);

	public abstract void setConfigButtonVisible(boolean visible);

	public abstract void setEnableButtonText(String displayString);
	
	public abstract void showConfig();

	public abstract void scrollTo(int yPos, int modHeight);
}
