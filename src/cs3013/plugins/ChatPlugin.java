package cs3013.plugins;

import java.io.IOException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import cs3013.ChatHandler;
import cs3013.Command;
import cs3013.JabberClient;
import cs3013.JabberID;
import cs3013.Plugin;
import cs3013.StanzaHandler;
import cs3013.XMLUtils;

public class ChatPlugin implements Plugin, ChatHandler, StanzaHandler {

	@Override
	public void init(JabberClient client) {
		client.registerCommand("chat", new ChatCommand());
		client.registerCommand("end", new EndCommand());
		client.registerStanzaHandler(this);
		client.setChatHandler(this);
		
		this.client = client;
	}

	@Override
	public void terminate() {
	}

	@Override
	public void sendMessage(String msg) throws IOException {
		if(currentFriendJID == null) {
			client.print("Not chatting with any one right now");
		}
		else {
			sendMessage(currentFriendJID, msg);
		}
	}

	@Override
	public boolean onStanza(XMLStreamReader parser) throws XMLStreamException {
		if(!parser.getLocalName().equals("message")) return false;

		String friendJID = parser.getAttributeValue(null, "from").split("/")[0];
		while(true) {
			switch(parser.nextTag()) {
			case XMLStreamConstants.START_ELEMENT:
				String tagName = parser.getLocalName();
				if(tagName.equals("body")) {
					client.print(friendJID + " says: " + parser.getElementText());
				}
				else {
					XMLUtils.skipElement(parser);
				}
				break;
			case XMLStreamConstants.END_ELEMENT://</message>
				return true;
			}
		}
	}
	
	private void sendMessage(String receiver, String msg) throws IOException {
		JabberID jid = client.getJID();
		
		String stanza = String.format(
			  "<message from='%s/%s' to='%s' type='chat' xml:lang='en'>"
			+     "<body>%s</body>"
			+ "</message>",

			jid.getJabberID(), jid.getResource(), currentFriendJID, msg
		);

		client.send(stanza);
	}

	String currentFriendJID;
	JabberClient client;
	
	private class ChatCommand implements Command {
		@Override
		public String getShortDescription() {
			return "Starts a new chat session";
		}

		@Override
		public String getLongDescription() {
			return "@chat <friend_jabber_id>\n\n"
			      +"This ends any ongoing chat session, and starts a new chat session "
			      +"with a friend with specified Jabber ID.";
		}

		@Override
		public void execute(String[] args) {
			if(args.length == 2) {
				currentFriendJID = args[1];
			}
			else {
				client.print("Syntax error");
			}
		}
	}

	private class EndCommand implements Command {
		@Override
		public String getShortDescription() {
			return "Ends any ongoing chat session";
		}

		@Override
		public String getLongDescription() {
			return "@end\n\n" + getShortDescription();
		}

		@Override
		public void execute(String[] args) {
			currentFriendJID = null;
		}
	}
}
