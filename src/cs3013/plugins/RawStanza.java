package cs3013.plugins;

import cs3013.Command;
import cs3013.JabberClient;
import cs3013.Plugin;

public class RawStanza implements Plugin, Command {
	@Override
	public void init(JabberClient client) {
		client.registerCommand("raw", this);		
		
		this.client = client;
	}

	@Override
	public void terminate() {
	}

	@Override
	public String getShortDescription() {
		return "Sends a raw stanza to the server";
	}

	@Override
	public String getLongDescription() {
		return "@raw <stanza>\n\n" + getShortDescription();
	}

	@Override
	public void execute(String[] args) throws InterruptedException {
		for(int i = 1; i < args.length; ++i) {
			client.send(args[i]);
			client.send(" ");
		}
	}

	private JabberClient client;
}
