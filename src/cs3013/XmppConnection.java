package cs3013;

import java.util.*;
import java.io.*;
import java.net.*;

import javax.net.ssl.*;
import javax.xml.stream.*;
import javax.security.sasl.*;
import javax.security.auth.callback.*;

// Apache commons codec library
import org.apache.commons.codec.binary.Base64;

/**
   Class that represents a connection to the XMPP server.

   <p> This class takes care of opening/closing the connection
   to the server. Opening the connection includes setting up
   the XML streams, upgrading to TLS, authentication, and 
   resource binding.

   <p> You are NOT required to understand the below code for 
   this assignment. Simply use this class to open/close the
   connection, and get the resulting socket.

   <p> Steps to use this class:
   <ol>
	<li> Create an object of this class using the constructor
		 {@link #XmppConnection(JabberID)} provided. </li>
	<li> Use the {@link #connect()} method to open a 
		 connection to the server. </li>
	<li> You can get the connected socket using the 
		 {@link #getSocket()} method, and associated readers and
		 writers using the methods {@link #getReader()} and 
		 {@link #getWriter()} for I/O.
		 You can use the XML pull-parser I use for parsing XML.
		 Get the parser using {@link #getParser()}.
		 If you are not sure how to go about parsing XML,
		 then peek into the code in this class for examples. </li>
	<li> You may require the stream ID (provided by the server
		 during connection setup). You can get this using the
		 {@link #getStreamID()} method. </li>
	<li> Finally, after you are done, close the connection 
		 using the {@link #close()} method. </li>
   </ol>
 */
public class XmppConnection implements AutoCloseable {

	/** Constructor (just initializes, doesn't connect). */
	public XmppConnection( JabberID jid ) {
		this.jid = jid;
	}

	/** Opens an XMPP connection to the server. */
	public void connect() 
		throws IOException {

		// Create a socket
		socket = new Socket();

		// Attempt to connect to the server
		System.out.println( "Connecting to " + jid.getServerName() + 
							" at port " + jid.getServerPort() );
		socket.connect( new InetSocketAddress( jid.getServerName() , 
											   jid.getServerPort() ) , 
						connectionTimeout );
		System.out.println( "Connected to " + jid.getServerName() + 
							" at port " + jid.getServerPort() );

		// Initialize the reader and writer
		initSocketStreams();

		// Create an XML stream from/to the server
		createXmlStream();
	}

	/** Gets the underlying Socket. */
	public Socket getSocket() {
		return socket;
	}

	/** Gets the Socket Reader. */
	public BufferedReader getReader() {
		return reader;
	}

	/** Gets the Socket Writer. */
	public BufferedWriter getWriter() {
		return writer;
	}

	/** Gets the XML pull-parser. */
	public XMLStreamReader getParser() {
		return parser;
	}

	/** Gets the Stream ID. */
	public String getStreamID() {
		return streamID;
	}

	/** Closes the socket connection. */
	public void close() 
		throws IOException {

		try {

			// Close the socket
			if ( socket != null ) {
				socket.close();
			}
		}
		finally {
			// Set everything except the JID to null
			socket = null;
			reader = null;
			writer = null;
			parser = null;
			streamID = null;
			sc = null;
			mechanismList.clear();
			isSecureConnection = false;
			isUserAuthenticated = false;
			isResourceBound = false;
		}
	}

	/* ***  PRIVATE IMPLEMENTATION DETAILS  *** */

	/** Initialize the reader and writer from/to the socket. */
	private void initSocketStreams() 
		throws IOException {

		// Initialize the reader and writer
		reader = new BufferedReader( new InputStreamReader( socket.getInputStream() , 
															"UTF-8" ) );
		writer = new BufferedWriter( new OutputStreamWriter( socket.getOutputStream() , 
															 "UTF-8" ) );
	}
	
	/** Creates an XML stream from/to the server. */
	private void createXmlStream() 
		throws IOException {

		try {

			// Flag to indicate that we need to re-send the 
			//  stream tag to the server
			boolean sendStreamTag = true;

			while ( sendStreamTag ) {

				// Open an XML stream to the server
				//  by sending the opening <stream> tag
				String streamTag = createStreamStanza();
				writer.write( streamTag );
				// Flush to make sure the data is sent
				writer.flush();

				// DEBUG
				System.out.println( "Sent client's stream tag" );

				// Create a XML pull parser to read from the incoming XML stream
				createXmlParser();

				// DEBUG
				System.out.println( "Created XML Pull parser" );

				// Wait for the XML stream tag response from server
				sendStreamTag = handleServerStream();
			}
		}
		catch ( XMLStreamException e ) {

			// Wrap any XML stream exceptions inside an IOException
			throw new IOException( "Exception while parsing XML" , e );
		}
	}

	/** Creates the opening stream tag. */
	private String createStreamStanza() {

		// Use a StringBuilder since it is much more 
		//  efficient than string concatenation
		StringBuilder sb = new StringBuilder();
		sb.append( "<stream:stream" );
		sb.append( " from=\"" ).append( jid.getJabberID() ).append( "\"" );
		sb.append( " to=\"" ).append( jid.getDomain() ).append( "\"" );
		sb.append( " version=\"1.0\"" );
		sb.append( " xml:lang=\"en\"" );
		sb.append( " xmlns=\"jabber:client\"" );
		sb.append( " xmlns:stream=\"http://etherx.jabber.org/streams\">" );
		return sb.toString();
	}

	/** Create an XML parser to read from the incoming XML stream. */
	private void createXmlParser() 
		throws XMLStreamException {

		// Use the XML Factory class to create a XML pull-parser
		XMLInputFactory xif = XMLInputFactory.newInstance();

		// Create an XML stream reader (aka 'pull-parser' in this code)
		parser = xif.createXMLStreamReader( reader );
	}

	/** Handles a new XML stream from the server. */
	private boolean handleServerStream() 
		throws IOException , XMLStreamException {

		// Get the next XML event
		int eventType = parser.getEventType();
		while ( eventType != XMLStreamConstants.END_DOCUMENT ) {

			// Check if the parse event is a XML start tag
			if ( eventType == XMLStreamConstants.START_ELEMENT ) {

				// Case 1: Stream tag
				if ( parser.getLocalName().equals( "stream" ) ) {
					handleStreamTag();
				}

				// Case 2: Features tag
				else if ( parser.getLocalName().equals( "features" ) ) {
					handleFeaturesTag();
				}

				// Case 3: Starttls Proceed tag
				else if ( parser.getLocalName().equals( "proceed" ) ) {
					upgradeToTls();
					// We need to re-send the stream tag after TLS
					return true;
				}

				// Case 4: Authentication Challenge tag
				else if ( parser.getLocalName().equals( "challenge" ) ) {
					respondToChallenge();
				}

				// Case 5: Authentication Success tag
				else if ( parser.getLocalName().equals( "success" ) ) {
					handleSuccessTag();
					// We need to re-send the stream tag after SASL
					return true;
				}

				// Case 6: Failure tag (for TLS and SASL)
				else if ( parser.getLocalName().equals( "failure" ) ) {
					handleFailureTag();
				}

				// Case 7: IQ tag (for resource ID request's response)
				else if ( parser.getLocalName().equals( "iq" ) ) {
					handleIQTag();

					// Return, we have successfully opened the connection
					return false;
				}
			}

			// Get the next XML event from the parser
			eventType = parser.next();
		}

		// We are done, no need to re-send the stream tag
		return false;
	}

	/** Handles the server's stream tag. */
	private void handleStreamTag() 
		throws IOException {

		// DEBUG
		System.out.println( "Handling server's stream tag" );

		// Get the Stream ID from the 'id' attribute
		// The ID may not be present if the stream tag is being re-sent
		//  after TLS or SASL
		int numAttributes = parser.getAttributeCount();
		for ( int i = 0 ; i < numAttributes ; ++i ) {
			if ( parser.getAttributeLocalName( i ).equals( "id" ) ) {
				streamID = parser.getAttributeValue( i );

				// DEBUG
				System.out.println( "Obtained stream ID: " + streamID );
			}
		}
	}

	/** Handles the features tag. */
	private void handleFeaturesTag() 
		throws IOException , XMLStreamException {

		// DEBUG
		System.out.println( "Handling server's features tag" );

		// Flag to indicate that the server supports TLS
		boolean hasStarttls = false;
		// Flag to indicate that the server requires resource binding
		// Note: According to the spec, the server MUST require it
		boolean hasBind = false;

		// Flag to indicate that we have finished parsing the 
		//  features tag
		boolean done = false;

		while ( !done ) {

			// Get the next XML event
			int eventType = parser.next();

			// Check if the parse event is a XML start tag
			if ( eventType == XMLStreamConstants.START_ELEMENT ) {

				// Case 1: Starttls tag
				if ( parser.getLocalName().equals( "starttls" ) ) {

					// DEBUG
					System.out.println( "Handling server's starttls tag" );

					hasStarttls = true;
				}

				// Case 2: SASL Mechanisms tag
				else if ( parser.getLocalName().equals( "mechanisms" ) ) {
					handleMechanismsTag();
				}

				// Case 3: Resource binding
				else if ( parser.getLocalName().equals( "bind" ) ) {

					// DEBUG
					System.out.println( "Handling server's bind tag" );

					hasBind = true;
				}
			}
			else if ( eventType == XMLStreamConstants.END_ELEMENT ) {
				if ( parser.getLocalName().equals( "features" ) ) {
					done = true;
				}
			}
		}

		// Upgrade the connection to TLS if server supports it
		if ( hasStarttls && ! isSecureConnection ) {
			starttls();
		}
		// If the server doesn't support TLS, or if
		//  the connection has already been upgraded to TLS,
		//  then we won't see the starttls tag, and we only
		//  need to authenticate the user
		else if ( ! isUserAuthenticated ) {
			authenticate();
		}
		// After authentication, we need to bind the resource
		else if ( hasBind && ! isResourceBound ) {
			bindResource();
		}
	}

	/** Handles the SASL mechanisms tag. */
	private void handleMechanismsTag() 
		throws IOException , XMLStreamException {

		// DEBUG
		System.out.println( "Handling server's mechanisms tag" );

		// Clear the list of supported SASL mechanisms
		mechanismList.clear();

		// Flag to indicate that we have finished parsing the 
		//  mechanisms tag
		boolean done = false;

		while ( !done ) {

			// Get the next XML event from the parser
			int eventType = parser.next();

			// Check if the parse event is a XML start tag
			if ( eventType == XMLStreamConstants.START_ELEMENT ) {

				// Case 1: mechanism (without 's') tag
				if ( parser.getLocalName().equals( "mechanism" ) ) {
					// Add the authentication mechanism to the list
					mechanismList.add( parser.getElementText() );
				}
			}
			else if ( eventType == XMLStreamConstants.END_ELEMENT ) {
				if ( parser.getLocalName().equals( "mechanisms" ) ) {
					done = true;
				}
			}
		}
	}

	/** Send the starttls tag. */
	private void starttls() 
		throws IOException {

		// DEBUG
		System.out.println( "Sending client's starttls tag" );

		// Respond to the server with a starttls tag
		//  saying that we want to upgrade to TLS
		String startlsTag = createStarttlsStanza();
		writer.write( startlsTag );
		// Flush to make sure the data is sent
		writer.flush();

		// Server should respond with a proceed tag after this
	}

	/** Creates the starttls tag. */
	private String createStarttlsStanza() {
		return "<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>";
	}

	/** Upgrades to a secure socket. */
	private void upgradeToTls() 
		throws IOException {

		// DEBUG
		System.out.println( "Upgrading to TLS..." );

		// Create an SSL Socket Factory (used to create SSL sockets)
		// Use the default factory
		SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();

		// Wrap the existing socket in a SSL socket
		SSLSocket sslSocket = (SSLSocket)
			factory.createSocket( socket ,									// Old Socket
								  socket.getInetAddress().getHostAddress() ,  // Server IP
								  socket.getPort() ,						  // Server port
								  true );									 // Auto-close flag
		socket = sslSocket;

		// Tell the OS to keep this connection alive
		// Students will in addition do application-level keep-alive
		//  using white-space
		socket.setKeepAlive( true );

		// Initialise the reader and writer again?
		//  Have to, otherwise the XML parser seems to throw
		//  and exception.
		initSocketStreams();

		// Do TLS handshaking
		sslSocket.startHandshake();

		// We are now a secure connection
		isSecureConnection = true;

		// At this point, we need to:
		//  1. Re-send the stream tag to the server
		//  2. Re-init the XML parser
		// This is done after this method returns

		// DEBUG
		System.out.println( "Upgraded to TLS" );
	}

	/** Authenticate the user. */
	private void authenticate() 
		throws IOException {

		// DEBUG
		System.out.print( "Authentication Mechanisms supported: " );
		for ( String mechanism : mechanismList ) {
			System.out.print( mechanism + ", " );
		}
		System.out.println();

		// Ref: http://docs.oracle.com/javase/1.5.0/docs/guide/security/sasl/sasl-refguide.html

		// Create an array of mechanisms supported by the server
		//  At this point of time, Google supports PLAIN and Facebook supports DIGEST-MD5
		// Note: PLAIN Password is still secure, since both Google and Facebook support TLS
		String[] mechanismArray = mechanismList.toArray( new String[0] );
		// Map of authentication properties we require (empty for this program)
		Map <String , String> authProperties = new HashMap <String , String>();
		// Create a SASL mechanism callback handler
		CallbackHandler handler = new JabberCallbackHandler();

		// Create an SASL client
		sc = 
			Sasl.createSaslClient( mechanismArray ,		// Mechanisms supported by server
								   jid.getJabberID() ,	 // Jabber ID
								   "xmpp" ,				// Application Protocol
								   jid.getServerName() ,   // Server name
								   authProperties ,		// Auth Properties required
								   handler );			  // Callback handler (for password)

		// Send first response (null if not required)
		byte[] response = null;
		if ( sc.hasInitialResponse() ) {
			// We don't have a server challenge, so pass an empty array
			response = sc.evaluateChallenge( new byte[0] );
		}

		// Send the Authentication stanza to the server
		String authStanza = createAuthenticationStanza( sc.getMechanismName() , 
														response );
		writer.write( authStanza );
		// Flush to make sure the data is sent
		writer.flush();

		// DEBUG
		System.out.println( "Sent client's authentication tag" );
	}

	/** Creates the Authentication stanza. */
	private String createAuthenticationStanza( String authName , 
											   byte[] response ) {

		// Use a StringBuilder since it is much more 
		//  efficient than string concatenation
		StringBuilder sb = new StringBuilder();
		sb.append( "<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"" );
		sb.append( " mechanism=\"" ).append( authName ).append( "\">" );
		// IMP: Initial response may be null
		if ( response != null ) {

			// IMP: Convert bytes to text using Base64 encoding
			//	  Here we use the Base64 class from Apache Commons
			String base64 = Base64.encodeBase64String( response );
			sb.append( base64 );
		}
		sb.append( "</auth>" );
		return sb.toString();
	}

	/** Responds to the Server's authentication challenge. */
	private void respondToChallenge() 
		throws IOException , XMLStreamException {

		// DEBUG
		System.out.println( "Handling server's challenge tag" );

		// Get the challenge string and de-code using Base64
		String challengeString = parser.getElementText();
		byte[] challenge = Base64.decodeBase64( challengeString );

		// Generate the response
		byte[] response = sc.evaluateChallenge( challenge );

		// Send the Response stanza to the server
		String responseStanza = createResponseStanza( response );
		writer.write( responseStanza );
		// Flush to make sure the data is sent
		writer.flush();

		// DEBUG
		System.out.println( "Sent client's response tag" );
	}

	/** Creates the Response stanza. */
	private String createResponseStanza( byte[] response ) {

		// Use a StringBuilder since it is much more 
		//  efficient than string concatenation
		StringBuilder sb = new StringBuilder();
		sb.append( "<response xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">" );
		// IMP: Response may be null
		if ( response != null ) {

			// IMP: Convert bytes to text using Base64 encoding
			//	  Here we use the Base64 class from Apache Commons
			String base64 = Base64.encodeBase64String( response );
			sb.append( base64 );
		}
		sb.append( "</response>" );
		return sb.toString();
	}

	/** Handles Authentication success tag. */
	private void handleSuccessTag() {

		// DEBUG
		System.out.println( "Handling server's success tag" );

		// User is now authenticated
		isUserAuthenticated = true;

		// GC the SASL Client
		sc = null;

		// At this point, we need to:
		//  1. Re-send the stream tag
		//  2. Re-init the XML parser
		// Both are done after this method returns
	}

	/** Handles TLS or SASL Failure tag. */
	private void handleFailureTag() 
		throws IOException , XMLStreamException {

		// DEBUG
		System.out.println( "Handling server's failure tag" );

		// Check the namespace to see whether it is TLS or SASL failure
		// Note: These are not really IOExceptions, we should actually
		//	   make a custom Exception class
		String namespace = parser.getNamespaceURI();
		if ( namespace == null ) {
			throw new IOException( "Server sent Failure stanza: Unknown reason" );
		}
		else if ( namespace.equals( "urn:ietf:params:xml:ns:xmpp-sasl" ) ) {
			throw new IOException( "Server sent Failure stanza: Authentication failure" );
		}
		else if ( namespace.equals( "urn:ietf:params:xml:ns:xmpp-tls" ) ) {
			throw new IOException( "Server sent Failure stanza: Unable to upgrade to TLS" );
		}
		else {
			throw new IOException( "Server sent Failure stanza: Namespace: " + 
								   namespace );
		}
	}

	/** Class that handles callbacks (such as password) from SASL mechanisms. */
	private class JabberCallbackHandler 
		implements CallbackHandler {

		/** Handle all SASL Mechanism callbacks. */
		public void handle( Callback[] callbacks ) 
			throws IOException , UnsupportedCallbackException {

			// Go through each callback one by one
			for ( Callback callback : callbacks ) {

				// Check the callback type
				// Case 1: User Name callback
				if ( callback instanceof NameCallback ) {
					NameCallback cb = (NameCallback) callback;
					cb.setName( jid.getJabberID() );
				}
				// Case 2: Password callback
				else if ( callback instanceof PasswordCallback ) {
					PasswordCallback cb = (PasswordCallback) callback;
					cb.setPassword( jid.getPassword().toCharArray() );
				}
				// We don't support other callbacks for now
				else {
					throw new UnsupportedCallbackException( callback );
				}
			}
		}
	}

	/** Binds the resource using a resource ID from the server. */
	private void bindResource() 
		throws IOException {

		// Send a request for a resource ID from the server
		String resourceStanza = createResourceRequestStanza();
		writer.write( resourceStanza );
		// Flush to make sure the data is sent
		writer.flush();

		// DEBUG
		System.out.println( "Sent client's resource ID request" );
	}

	/** Creates the Resource query stanza. */
	private String createResourceRequestStanza() {

		// Use a StringBuilder since it is much more 
		//  efficient than string concatenation
		StringBuilder sb = new StringBuilder();
		sb.append( "<iq id=\"" ).append( streamID ).append( "\" type=\"set\">" );
		sb.append( " <bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/>" );
		sb.append( "</iq>" );
		return sb.toString();
	}

	/** Handles the IQ tag. */
	private void handleIQTag() 
		throws IOException , XMLStreamException {

		// DEBUG
		System.out.println( "Handling server's IQ tag" );

		// Flag to indicate that we have finished parsing the 
		//  IQ tag
		boolean done = false;

		while ( !done ) {

			// Get the next XML event
			int eventType = parser.next();

			// Check if the parse event is a XML start tag
			if ( eventType == XMLStreamConstants.START_ELEMENT ) {

				// Case 1: Bind tag
				if ( parser.getLocalName().equals( "bind" ) ) {
					handleIQBindTag();
				}

				// Case 2: Error tag
				else if ( parser.getLocalName().equals( "error" ) ) {
					// This is not really an IOException, we need to 
					//  define a custom exception class
					throw new IOException( "Resource bind error" );
				}
			}
			else if ( eventType == XMLStreamConstants.END_ELEMENT ) {
				if ( parser.getLocalName().equals( "iq" ) ) {
					done = true;
				}
			}
		}
	}

	/** Handles the IQ Bind tag. */
	private void handleIQBindTag() 
		throws IOException , XMLStreamException {

		// DEBUG
		System.out.println( "Handling server's IQ Bind tag" );

		// Flag to indicate that we have finished parsing the 
		//  IQ Bind tag
		boolean done = false;

		while ( !done ) {

			// Get the next XML event
			int eventType = parser.next();

			// Check if the parse event is a XML start tag
			if ( eventType == XMLStreamConstants.START_ELEMENT ) {

				// Case 1: JID tag
				if ( parser.getLocalName().equals( "jid" ) ) {

					// Get and set the resource ID sent by the server
					String fullJID = parser.getElementText();
					String[] split = fullJID.split( "/" );
					if ( split.length != 2 ) {
						throw new IOException( "Ill-formatted JID sent by server: " + fullJID );
					}
					jid.setResource( split[1] );

					// DEBUG
					System.out.println( "Obtained Resource ID: " + jid.getJabberID() + "/" + jid.getResource() );
				}
			}
			else if ( eventType == XMLStreamConstants.END_ELEMENT ) {
				if ( parser.getLocalName().equals( "bind" ) ) {
					done = true;
				}
			}
		}
	}

	/** Jabber ID. */
	private JabberID jid;

	/** Socket used to connect to the server. */
	private Socket socket;
	/** Socket Reader. */
	private BufferedReader reader;
	/** Socket writer. */
	private BufferedWriter writer;
	/** XML Pull-parser. */
	private XMLStreamReader parser;

	/** Stream ID. */
	private String streamID;

	/** Indicates whether the connection is secure. */
	private boolean isSecureConnection = false;
	/** Indicates whether the user is authenticated. */
	private boolean isUserAuthenticated = false;
	/** Indicates whether the resource is bound. */
	private boolean isResourceBound = false;

	/** List of supported SASL authentication mechanisms. */
	private List <String> mechanismList = 
		new ArrayList <String>();
	/** SASL Client. */
	private SaslClient sc;

	/** Connection-establishment timeout (millisec). */
	private final int connectionTimeout = 5000;
}
