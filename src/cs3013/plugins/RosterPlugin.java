package cs3013.plugins;

import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import cs3013.Command;
import cs3013.JabberClient;
import cs3013.JabberID;
import cs3013.Plugin;
import cs3013.StanzaHandler;
import cs3013.XMLUtils;

public class RosterPlugin implements Plugin, StanzaHandler {

	@Override
	public void init(JabberClient client) {
		client.registerCommand("roster", new RosterCommand());
		client.registerStanzaHandler(this);
		
		this.client = client;
	}

	@Override
	public void terminate() {
	}

	@Override
	public boolean onStanza(XMLStreamReader parser) throws XMLStreamException {
		// Only interested in <iq>
		if(!parser.getLocalName().equals("iq")) return false;

		// With a recorded id
		String id = parser.getAttributeValue(null, "id");
		if(!rosterStanzaIds.contains(id)) return false;
		rosterStanzaIds.remove(id);
		
		parser.nextTag();//Skip to <query>
		boolean done = false;
		while(!done) {
			switch(parser.nextTag()) {
			case XMLStreamConstants.START_ELEMENT:
				client.print(parser.getAttributeValue(null, "jid"));
				XMLUtils.skipElement(parser);
				break;
			case XMLStreamConstants.END_ELEMENT://reached </query>
				done = true;
				break;
			}
		}
		
		parser.nextTag();//Skip to </iq>
		return true;
	}
	
	private JabberClient client;
	private Set<String> rosterStanzaIds = new HashSet<String>();
	
	private class RosterCommand implements Command {

		@Override
		public String getShortDescription() {
			return "Gets the roster list";
		}

		@Override
		public String getLongDescription() {
			return "@roster\n\n"
			      +"Get the roster list";
		}

		@Override
		public void execute(String[] args) throws InterruptedException {
			String stanzaId = client.newStanzaId();
			rosterStanzaIds.add(stanzaId);

			JabberID jid = client.getJID();
			String stanza = String.format(
				  "<iq from='%s/%s' id='%s' type='get'>"
				+    " <query xmlns='jabber:iq:roster'/>"
				+ "</iq>",
				
				jid.getJabberID(), jid.getResource(), stanzaId
			);
			
			client.send(stanza);
		}

	}
}
