package org.majorsopa.shikari;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

/**
 * Rusherhack basehunting plugin
 *
 * @author majorsopa
 */
public class ShikariPlugin extends Plugin {
	
	@Override
	public void onLoad() {
		
		//logger
		this.getLogger().info(this.getName() + " loaded!");
		this.getLogger().info("Hello World!");
		
		//creating and registering a new module
		final ShikariNewChunks shikariNewChunks = new ShikariNewChunks();
		RusherHackAPI.getModuleManager().registerFeature(shikariNewChunks);
		
		//creating and registering a new hud element
		//final ExampleHudElement exampleHudElement = new ExampleHudElement();
		//RusherHackAPI.getHudManager().registerFeature(exampleHudElement);
		
		//creating and registering a new command
		//final ExampleCommand exampleCommand = new ExampleCommand();
		//RusherHackAPI.getCommandManager().registerFeature(exampleCommand);
	}
	
	@Override
	public void onUnload() {
		this.getLogger().info(this.getName() + " unloaded!");
	}
	
	@Override
	public String getName() {
		return "Shikari";
	}
	
	@Override
	public String getVersion() {
		return "v0.1";
	}
	
	@Override
	public String getDescription() {
		return "Rusherhack basehunting plugin";
	}
	
	@Override
	public String[] getAuthors() {
		return new String[]{"majorsopa"};
	}
}
