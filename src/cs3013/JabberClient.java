package cs3013;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import cs3013.plugins.*;

public class JabberClient {
	public JabberClient(JabberID jid) {
		this.jid = jid;
		this.connection = new XmppConnection(jid);
	}
	
	public void start() throws IOException {
		installPlugin(new CorePlugin());
		installPlugin(new RosterPlugin());
		installPlugin(new ChatPlugin());
		installPlugin(new RawStanza());

		connection.connect();
		
		running = true;
		socketHandler = new Thread() {
			@Override
			public void run() {
				handleSocket();
			}
		};
		socketHandler.start();

		send("<presence/>");//set presence
		//this is required for Facebook chat
		String sessionStanza = String.format(
			  "<iq type='set' id='%s' to='%s'>"
			+     "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>"
			+ "</iq>",

			newStanzaId(), jid.getDomain()
		);
		send(sessionStanza);
		
		try(Scanner scanner = new Scanner(System.in)) {
			while(running) {
				String str = scanner.nextLine();
				if(isCommand(str)) {
					String[] cmd = str.split("\\s+");
					String cmdName = cmd[0].substring(1);
					Command command = commands.get(cmdName);
					if(command != null) {
						command.execute(cmd);
					}
					else {
						print("Invalid command");
					}
				}
				else {
					chatHandler.sendMessage(str);
				}
			}
		}
	}

	public void stop() throws IOException {
		if(connection != null) {
			for(Plugin plugin: plugins) {
				plugin.terminate();
			}

			running = false;
			socketHandler.interrupt();
			send("</stream:stream>");

			connection.close();
			connection = null;
		}
	}

	public String newStanzaId() {
		return Long.toString(random.nextLong(), 16);
	}
	
	public void send(String msg) throws IOException {
		BufferedWriter writer = connection.getWriter();
		synchronized(writer) {
			writer.write(msg);
			writer.flush();
		}
	}

	public void print(String msg) {
		synchronized(System.out) {
			System.out.println(msg);
		}
	}
	
	public JabberID getJID() {
		return jid;
	}

	public void registerCommand(String name, Command command) {
		commands.put(name, command);
	}

	public void registerStanzaHandler(StanzaHandler handler) {
		stanzaHandlers.add(handler);
	}

	private static boolean isCommand(String str) {
		return str.startsWith("@");
	}

	public void setChatHandler(ChatHandler handler) {
		chatHandler = handler;
	}
	
	private void handleSocket() {
		XMLStreamReader parser = connection.getParser();
		try {
			while(running) {
				if(parser.next() != XMLStreamConstants.START_ELEMENT) continue;
				
				boolean handled = false;
		
				for(StanzaHandler handler: stanzaHandlers) {
					if(handler.onStanza(parser)) {
						handled = true;
						break;
					}
				}
				
				if(!handled) {
					if(developerMode) {
						dumpXML(parser);
					}
					else {
						XMLUtils.skipElement(parser);
					}
				}
			}
		}
		catch(Exception e) {
			if(running) {
				//confirm if it is due to socket
				boolean disconnected = false;
				try {
					disconnected = connection.getSocket().getInputStream().read() == -1;
				} catch (IOException _) {
					disconnected = true;
				}
				
				if(!disconnected) {
					e.printStackTrace();
				}
				else {
					System.out.println("Connection lost");
				}
			}
		}
	}

	private void installPlugin(Plugin plugin) {
		plugin.init(this);
		plugins.add(plugin);
	}

	private void dumpXML(XMLStreamReader parser) throws XMLStreamException {
		int depth = 1;
		synchronized(System.out) {
			while(depth > 0) {
				switch(parser.getEventType()) {
				case XMLStreamConstants.START_ELEMENT:
					for(int i = 1; i < depth; ++i) {
						System.out.print("    ");
					}
					System.out.print("<");
					System.out.print(parser.getLocalName());
					for(int i = 0; i < parser.getAttributeCount(); ++i) {
						System.out.print(" ");
						System.out.print(parser.getAttributeLocalName(i));
						System.out.print("=\"");
						System.out.print(parser.getAttributeValue(i));
						System.out.print("\"");
					}
					System.out.println(">");

					++depth;
					break;
				case XMLStreamConstants.END_ELEMENT:
					--depth;
					for(int i = 1; i < depth; ++i) {
						System.out.print("    ");
					}
					System.out.print("</");
					System.out.print(parser.getLocalName());
					System.out.println(">");
					break;
				case XMLStreamConstants.CHARACTERS:
					for(int i = 1; i < depth; ++i) {
						System.out.print("    ");
					}
					
					System.out.println(parser.getText());
					break;
				}
				parser.next();
			}
		}
	}
	
	private boolean running;
	private boolean developerMode = false;
	private List<Plugin> plugins = new ArrayList<Plugin>();
	private Map<String, Command> commands = new HashMap<String, Command>();
	private List<StanzaHandler> stanzaHandlers = new ArrayList<StanzaHandler>();
	private ChatHandler chatHandler;
	private JabberID jid;
	private XmppConnection connection;
	private Random random = new Random();
	private Thread socketHandler;
	
	private class CorePlugin implements Plugin {
		@Override
		public void init(JabberClient client) {
			client.registerCommand("help", new HelpCommand());
			client.registerCommand("quit", new QuitCommand());
			client.registerCommand("dev", new DeveloperCommand());
		}

		@Override
		public void terminate() {
		}
	}
	
	private class DeveloperCommand implements Command {
		@Override
		public String getShortDescription() {
			return "Developer mode.";
		}

		@Override
		public String getLongDescription() {
			return "@dev on - Enable developer mode.\n"
			      +"@dev off - Disable developer mode.\n"
			      +"@dev - Check status.";
		}

		@Override
		public void execute(String[] args) throws IOException {
			if(args.length == 1) {
				printStatus();
			}
			else {
				developerMode = args[1].equals("on");
				printStatus();
			}
		}

		private void printStatus() {
			print("Developer mode is: " + (developerMode ? "on" : "off"));
		}
		
	}

	private class HelpCommand implements Command {
		@Override
		public String getShortDescription() {
			return "Shows help. Use @help <command> to know more about a command";
		}
		
		@Override
		public String getLongDescription() {
			return "@help [command]\n\n" + getShortDescription();
		}
		
		@Override
		public void execute(String[] args) throws IOException {
			if(args.length < 2) {//short help
				for(Map.Entry<String, Command> pair: commands.entrySet()) {
					print("@" + pair.getKey() + " - " + pair.getValue().getShortDescription());
				}
			}
			else {//long help
				String cmdName = args[1];
				Command command = commands.get(cmdName);
				if(command != null) {
					print(command.getLongDescription());
				}
				else {
					print("Unrecognized command");
				}
			}
		}
	};
	
	private class QuitCommand implements Command {

		@Override
		public String getShortDescription() {
			return "Quit the program.";
		}

		@Override
		public String getLongDescription() {
			return "@quit\n\n"+getShortDescription();
		}

		@Override
		public void execute(String[] args) throws IOException {
			stop();
		}
	}
}
