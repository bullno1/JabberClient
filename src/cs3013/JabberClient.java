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
import javax.xml.stream.XMLStreamReader;

import cs3013.plugins.*;

public class JabberClient {
	public JabberClient(JabberID jid) {
		this.jid = jid;
	}
	
	public void start() throws IOException {
		installPlugin(new CorePlugin());
		installPlugin(new RosterPlugin());
		installPlugin(new ChatPlugin());
		installPlugin(new RawStanza());
		clientRunning = true;

		Thread commandHandler = new Thread() {
			@Override
			public void run() {
				commandLoop();
			}
		};
		commandHandler.start();
		
		boolean firstTime = true;
		while(clientRunning) {
			mainLoop(firstTime);
			firstTime = false;
		}

		try {
			connection.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public synchronized void stop() {
		if(clientRunning) {
			clientRunning = false;
			stopSession();
			send("</stream:stream>");

			for(Plugin plugin: plugins) {
				plugin.terminate();
			}
		}
	}

	public String newStanzaId() {
		return Long.toString(random.nextLong(), 16);
	}
	
	public synchronized void send(String msg) {
		BufferedWriter writer = connection.getWriter();
		try {
			writer.write(msg);
			writer.flush();
		} catch (IOException e) {
			onConnectionLost();
		}
	}

	public synchronized void print(String msg) {
		System.out.println(msg);
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
	
	private void mainLoop(boolean firstTime) {
		sessionRunning = true;
		mainThread = Thread.currentThread();

		connectLoop(firstTime);
		if(!sessionRunning) return;

		Thread socketHandler = new Thread() {
			@Override
			public void run() {
				socketLoop();
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
		
		try {
			while(!Thread.interrupted()) {//keepalive
				Thread.sleep(60 * 5 * 1000);
				send(" ");
			}
		} catch (InterruptedException e) {
			if(sessionRunning)//interrupted for some unknown reason
				e.printStackTrace();
		}
		
		try {
			socketHandler.interrupt();
			socketHandler.join();
		} catch (InterruptedException e) {//can't be interrupted again, something is wrong
			e.printStackTrace();
		}
	}
	
	private void connectLoop(boolean firstTime) {
		int numAttempts = firstTime ? 0 : 1;

		try {
			boolean connected = false;
			while(sessionRunning && !connected) {
				int numSlots = 20;
				Random random = new Random();
				int upperBound = Math.min((int)Math.pow(2, numAttempts), numSlots) - 1;
				long waitTime = random.nextInt(upperBound + 1) * 60 * 1000 / numSlots;

				if(numAttempts > 0 || !firstTime) {
					print("Waiting " + waitTime / 1000 + "s");
				}

				Thread.sleep(waitTime);
				try {
					print("---------------------------------------------------");
					connection = new XmppConnection(jid);
					connection.connect();
					print("-------------------- Connected --------------------");
					connected = true;
				} catch (IOException e) {
					print("---------------- Failed to connect ----------------");
				} catch (XmppException e) {
					e.printStackTrace();
					stop();
				}
				++numAttempts;
			}
		}
		catch(InterruptedException _) {
		}
	}
	
	private void commandLoop() {
		try(Scanner scanner = new Scanner(System.in)) {
			while(clientRunning) {
				String str = scanner.nextLine();
				if(isCommand(str)) {
					String[] cmd = str.split("\\s+");
					String cmdName = cmd[0].substring(1);
					Command command = commands.get(cmdName);
					if(command != null) {
						synchronized(this) {
							command.execute(cmd);
						}
					}
					else {
						print("Invalid command");
					}
				}
				else {
					synchronized(this) {
						chatHandler.sendMessage(str);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void socketLoop() {
		XMLStreamReader parser = connection.getParser();
		try {
			while(!Thread.interrupted()) {
				if(parser.next() != XMLStreamConstants.START_ELEMENT) continue;
				
				boolean handled = false;
		
				synchronized(this) {
					for(StanzaHandler handler: stanzaHandlers) {
						if(handler.onStanza(parser)) {
							handled = true;
							break;
						}
					}
				}
				
				if(!handled) {
					if(developerMode) {
						synchronized(this) {
							XMLUtils.dumpXML(parser);
						}
					}
					else {
						XMLUtils.skipElement(parser);
					}
				}
			}
		}
		catch(Exception e) {
			if(sessionRunning) {
				//confirm if it is due to socket
				boolean disconnected = false;
				try {
					disconnected = connection.getSocket().getInputStream().read() == -1;
				} catch (IOException _) {
					disconnected = true;
				}
				
				if(disconnected) {
					onConnectionLost();
				}
				else {
					e.printStackTrace();
				}
			}
		}
	}

	private void onConnectionLost() {
		if(!stopSession()) return;

		print("------------------ Connection lost ----------------");
	}
	
	private synchronized boolean stopSession() {
		if(!sessionRunning) return false;
		sessionRunning = false;

		mainThread.interrupt();
		return true;
	}

	private void installPlugin(Plugin plugin) {
		plugin.init(this);
		plugins.add(plugin);
	}
	
	private boolean clientRunning;
	private boolean sessionRunning;
	private boolean developerMode = false;
	private List<Plugin> plugins = new ArrayList<Plugin>();
	private Map<String, Command> commands = new HashMap<String, Command>();
	private List<StanzaHandler> stanzaHandlers = new ArrayList<StanzaHandler>();
	private ChatHandler chatHandler;
	private JabberID jid;
	private XmppConnection connection;
	private Random random = new Random();
	private Thread mainThread;
	
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
		public void execute(String[] args) {
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
		public void execute(String[] args) {
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
		public void execute(String[] args) {
			stop();
		}
	}
}
