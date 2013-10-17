package cs3013.plugins;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.lang3.StringEscapeUtils;

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
		client.registerCommand("log", new LogCommand());
		client.registerStanzaHandler(this);
		client.setChatHandler(this);
		
		this.client = client;
	}

	@Override
	public void terminate() {
	}

	@Override
	public void sendMessage(String msg) throws InterruptedException {
		if(currentFriendJID == null) {
			client.print("Not chatting with any one right now");
		}
		else {
			sendMessage(currentFriendJID, msg);
			chatLog.append("me: ").append(msg).append("\n");
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
					String msg = parser.getElementText();
					client.print(friendJID + " says: " + msg);
					if(friendJID.equals(currentFriendJID)) {
						chatLog.append(friendJID).append(": ").append(msg).append("\n");
					}
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
	
	private void sendMessage(String receiver, String msg) throws InterruptedException {
		JabberID jid = client.getJID();
		
		String stanza = String.format(
			  "<message from='%s/%s' to='%s' type='chat' xml:lang='en'>"
			+     "<body>%s</body>"
			+ "</message>",

			jid.getJabberID(), jid.getResource(), currentFriendJID, StringEscapeUtils.escapeXml(msg)
		);

		client.send(stanza);
	}
	
	private void startSession(String friendJID) {
		if(currentFriendJID != null) {
			endSession();
		}

		currentFriendJID = friendJID;
		chatLog.setLength(0);
	}
	
	private void endSession() {
		if(currentFriendJID == null) return;

		if(logEnabled) sendChatLog();

		currentFriendJID = null;
	}
	
	private void sendChatLog() {
		client.print("Sending chat log");

		try(Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(logServerAddress, logServerPort));
			try(DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
				//friend jid
				output.writeUTF(currentFriendJID);
				//size
				byte[] log = chatLog.toString().getBytes("UTF-8");
				output.writeInt(log.length);
				output.write(log);
			}
		} catch (IOException e) {
			client.print("Failed to send log");
			e.printStackTrace();
		}
		client.print("Done");
	}

	StringBuilder chatLog = new StringBuilder();
	String logServerAddress = "127.0.0.1";
	int logServerPort = 9001;
	boolean logEnabled = true;
	String currentFriendJID;
	JabberClient client;

	private class LogCommand implements Command {

		@Override
		public String getShortDescription() {
			return "View/set log settings";
		}

		@Override
		public String getLongDescription() {
			return "@log - view current log setting.\n"
			      +"@log on - enable logging (enabled by default).\n"
				  +"@log on <address> <port> - enable logging and change server settings.\n"
			      +"@log off - disable logging.";
		}

		@Override
		public void execute(String[] args) throws InterruptedException {
			switch(args.length) {
			case 1://view settings
				if(logEnabled) {
					client.print("Logging is on");
					client.print("Log server: " + logServerAddress);
					client.print("Port: " + logServerPort);
				}
				else {
					client.print("Logging is off");
				}
				break;
			case 2://enable/disable
				logEnabled = args[1].equals("on");
				break;
			case 4:
				try {
					logServerPort = Integer.parseInt(args[3]);
					logEnabled = args[1].equals("on");
					logServerAddress = args[2];
				}
				catch(NumberFormatException e) {
					client.print("Syntax error");
				}
				break;
			default:
				client.print("Syntax error");
				break;
			}
		}
	}
	
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
				startSession(args[1]);
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
			endSession();
		}
	}
}
