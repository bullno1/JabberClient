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

public class Vcard implements Plugin, StanzaHandler {

	@Override
	public boolean onStanza(XMLStreamReader parser) throws XMLStreamException {
		if(!parser.getLocalName().equals("iq")) return false;
		
		String stanzaId = parser.getAttributeValue(null, "id");
		if(!iqStanzaIds.contains(stanzaId)) return false;
		iqStanzaIds.remove(stanzaId);
		
		
		parser.nextTag();//Skip to <vCard>
		boolean done = false;
		while(!done) {
			switch(parser.nextTag()) {
			case XMLStreamConstants.START_ELEMENT:
				String name = parser.getLocalName();
				if(name.equals("FN")) {
					client.print("Full name: " + parser.getElementText());
				}
				else if(name.equals("URL")) {
					client.print("Website: " + parser.getElementText());				
				}
				else if(name.equals("DESC")) {
					client.print("About: " + parser.getElementText());				
				}
				else {
					XMLUtils.skipElement(parser);
				}
				break;
			case XMLStreamConstants.END_ELEMENT://reached </vCard>
				done = true;
				break;
			}
		}
		
		parser.nextTag();//Skip to </iq>
		return true;
	}

	@Override
	public void init(JabberClient client) {
		client.registerStanzaHandler(this);
		client.registerCommand("vcard", new VcardCommand());

		this.client = client;
	}

	@Override
	public void terminate() {
	}
	
	private JabberClient client;
	private Set<String> iqStanzaIds = new HashSet<String>();

	private class VcardCommand implements Command {
		@Override
		public String getShortDescription() {
			return "Retrives contact information";
		}

		@Override
		public String getLongDescription() {
			return "vcard - Retrieves your own contact information.\n"
			     + "vcard <jabber_id> - Retrives your friend's contact information.";
		}

		@Override
		public void execute(String[] args) throws InterruptedException {
			String stanzaId = client.newStanzaId();
			iqStanzaIds.add(stanzaId);

			JabberID jid = client.getJID();
			String stanza;

			if(args.length == 1) {//own vcard
				stanza = String.format(
					  "<iq from='%s/%s' id='%s' type='get'>"
					+     "<vCard xmlns='vcard-temp'/>"
					+ "</iq>",
					jid.getJabberID(), jid.getResource(), stanzaId
				);
			}
			else {//friend's vcard
				stanza = String.format(
					  "<iq from='%s/%s' to='%s' id='%s' type='get'>"
					+     "<vCard xmlns='vcard-temp'/>"
					+ "</iq>",
					jid.getJabberID(), jid.getResource(), args[1], stanzaId
				);
			}

			client.send(stanza);
		}
	}
}
