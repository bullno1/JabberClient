package cs3013;
import java.util.*;

/**
   Class containing the {@link #main(String[])} method.

   <p> This class creates an XMPP connection to the server
   specified in the command-line arguments, using the
   {@link XmppConnection} class.
 */
public class JabberMain {

	/** Main method that starts off everything. 
	 * @throws Exception 
	 **/
	public static void main( String[] args ) throws Exception {
		// In this assignment, you need to connect to *one*
		//  Jabber server only. For extra credit, you can
		//  extend your client to handle multiple Jabber
		//  servers (multiple Jabber IDs) simultaneously.

		// Check if number of args are ok (multiple of 4)
		if ( args.length < 4 || args.length % 4 != 0 ) {
			System.err.println( "Usage: java JabberMain " + 
								"jabber_id password server_name server_port " + 
								"[more Jabber ID details] ... " );
			return;
		}
		System.out.println();

		// Get the list of Jabber IDs
		List <JabberID> jidList = getJidList( args );

		// In this assignment, handling one server is sufficient
		// Create an XMPP connection
		JabberID jid = jidList.get( 0 );
		
		JabberClient client = new JabberClient(jid);
		try {
			client.start();
		}
		finally {
			client.stop();
		}
	}

	/** Helper method that gets the list of Jabber IDs specified as args. */
	private static List <JabberID> getJidList( String[] args ) {

		// Get the list of Jabber IDs
		List <JabberID> jidList = new ArrayList <JabberID>();
		for ( int i = 0 ; i < args.length ; i += 4 ) {

			// Try to convert the port number to int
			int port;
			try {
				port = Integer.parseInt( args[ i + 3 ] );
			}
			catch ( NumberFormatException e ) {
				throw new IllegalArgumentException( "Invalid port: Not a number" , e );
			}

			// Add the Jabber ID to the list
			jidList.add( new JabberID( args[i] ,      // Jabber ID: username@domain
			                           args[i + 1] ,  // Password
			                           args[i + 2] ,  // Server name
			                           port ) );      // Server port
		}

		return jidList;
	}
}
