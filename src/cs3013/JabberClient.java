package cs3013;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class JabberClient {
	public JabberClient(JabberID jid) {
		this.jid = jid;
		this.connection = new XmppConnection(jid);
	}
	
	public void start() throws IOException {
		connection.connect();

		new XMLStreamWatcher(connection.getParser(), new TypedCallback<Boolean, XMLStreamReader>() {
			@Override
			public Boolean call(XMLStreamReader reader) throws Exception {
				handleTag(reader);
				return true;
			}
		}).start();
		
		try(Scanner scanner = new Scanner(System.in)) {
			while(true) {
				String str = scanner.nextLine();
				if(isCommand(str)) {
					String[] cmd = str.split("\\s+");
					String cmdName = cmd[0].substring(1);
					if(cmdName.equals("help")) {
						showHelp();
					}
					else if(cmdName.equals("roster")) {
						getRoster();
					}
					else if(cmdName.equals("chat")) {
						print("Under construction");
					}
					else if(cmdName.equals("end")) {
						print("Under construction");
					}
					else if(cmdName.equals("quit")) {
						send("</stream:stream>");
						return;
					}
				}
				else {
					sendMessage(str);
				}
			}
		}
	}

	public void stop() throws IOException {
		if(connection != null) {
			connection.close();
			connection = null;
		}
	}

	private static boolean isCommand(String str) {
		return str.startsWith("@");
	}
	
	private void showHelp() {
		System.out.println(
			  "@roster - Gets the roster list\n"
			+ "@chat <friend_jabber_id> - This ends any ongoing chat session, and starts\n"
			+ "a new chat session with a friend with specified Jabber ID.\n"
			+ "@end - End any ongoing chat"
			+ "@help - Display this help menu"
		);
	}

	private boolean handleTag(XMLStreamReader parser) throws Exception {
		switch(parser.getEventType()) {
		case XMLStreamConstants.START_ELEMENT:
			String tagName = parser.getLocalName();
			if(tagName.equals("iq")) {
				String id = parser.getAttributeValue(null, "id");
				TypedCallback<Void, XMLStreamReader> iqHandler = iqHandlers.get(id);
				if(iqHandler != null) {
					iqHandler.call(parser);
					iqHandlers.remove(id);
				}
				while(parser.getEventType() != XMLStreamConstants.END_ELEMENT
				      || !parser.getLocalName().equals("iq")) {
					parser.next();
				}
			} else if (tagName.equals("message")) {
				//handle message
			} else {
				System.out.println("Unknown tag " + tagName + ", skipping");
				parser.nextTag();
			}
			break;
		case XMLStreamConstants.END_ELEMENT:
			print(parser.getLocalName());
			break;
		}
		
		return true;
	}
		
	private void getRoster() throws IOException {
		String stanzaId = newStanzaId();
		iqHandlers.put(stanzaId, rosterCallback);
		String stanza = String.format(
			  "<iq from='%s/%s'"
			+    " id='%s'"
			+    " type='get'>"
			+    " <query xmlns='jabber:iq:roster'/>"
			+ "</iq>",
			
			jid.getJabberID(), jid.getResource(), stanzaId
		);
		
		send(stanza);
	}

	private void handleRoster(XMLStreamReader parser) throws XMLStreamException {
		parser.nextTag();//Skip to <query>
		while(true) {
			switch(parser.nextTag()) {
			case XMLStreamConstants.START_ELEMENT:
				print(parser.getAttributeValue(null, "jid"));
				skipElement(parser);
				break;
			case XMLStreamConstants.END_ELEMENT:
				return;
			}
		}
	}
	
	private void skipElement(XMLStreamReader parser) throws XMLStreamException {
		int numEndTags = 1;
		while(numEndTags > 0) {
			switch(parser.next()) {
			case XMLStreamConstants.START_ELEMENT:
				++numEndTags;//go down
				break;
			case XMLStreamConstants.END_ELEMENT:
				--numEndTags;//go up
				break;
			}
		}
	}

	private void sendMessage(String msg) {
		print("Under construction");
	}
	
	private String newStanzaId() {
		return Long.toString(random.nextLong(), 16);
	}
	
	private void send(String msg) throws IOException {
		BufferedWriter writer = connection.getWriter();
		synchronized(writer) {
			writer.write(msg);
			writer.flush();
		}
	}
	
	private void print(String msg) {
		synchronized(System.out) {
			System.out.println(msg);
		}
	}

	private JabberID jid;
	private XmppConnection connection;
	private Map<String, TypedCallback<Void, XMLStreamReader>> iqHandlers
		= new HashMap<String, TypedCallback<Void, XMLStreamReader>>();
	private Random random = new Random();
	private TypedCallback<Void, XMLStreamReader> rosterCallback = new TypedCallback<Void, XMLStreamReader>() {
		@Override
		public Void call(XMLStreamReader parser) throws Exception {
			handleRoster(parser);
			return null;
		}
	};
}
