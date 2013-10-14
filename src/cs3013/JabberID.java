package cs3013;

/**
 * Class that stores the details of a Jabber ID.
 * <p> Contains the Jabber ID details: 
 * username, domain, resource, password, server, port.</p>
 *
 * <p> The resource is assigned by the server after 
 * the connection is set up.</p>
 **/
public class JabberID {

	/** Constructor for initialising JID details. */
	public JabberID( String jid , 
	                 String password , 
	                 String serverName , 
	                 int serverPort ) {

		setJabberID( jid );
		setPassword( password );
		setServerName( serverName );
		setServerPort( serverPort );
		setResource( null );
	}

	/** Gets the Jabber ID (without resource part). */
	public String getJabberID() {
		return jid;
	}

	/** Gets the User name. */
	public String getUsername() {
		return username;
	}

	/** Gets the Domain. */
	public String getDomain() {
		return domain;
	}

	/** Gets the Resource. */
	public String getResource() {
		return resource;
	}

	/** Gets the Password. */
	public String getPassword() {
		return password;
	}

	/** Gets the Server Name/IP. */
	public String getServerName() {
		return serverName;
	}

	/** Gets the Server port. */
	public int getServerPort() {
		return serverPort;
	}

	/** Sets the Jabber ID. */
	public void setJabberID( String jid ) {

		// Split the JID into username and domain
		// Eg: Split 'kar.kbc@gmail.com' into 'kar.kbc' and 'gmail.com'
		// Note: The Domain need not be the same as the server name
		//	   For google, the server is 'talk.google.com'
		String[] parts = jid.split( "@" );
		if ( parts.length != 2 ) {
			throw new IllegalArgumentException( "Jabber ID invalid: Must be in format username@domain" );
		}
		this.jid = jid;
		this.username = parts[0];
		this.domain = parts[1];
	}

	/** Sets the Resource. */
	public void setResource( String resource ) {
		this.resource = resource;
	}

	/** Sets the Password. */
	public void setPassword( String password ) {
		this.password = password;
	}

	/** Sets the Server Name. */
	public void setServerName( String serverName ) {
		this.serverName = serverName;
	}

	/** Sets the Server Port. */
	public void setServerPort( int serverPort ) {

		// Check if the port is ok
		if ( serverPort <= 0 || serverPort >= 65536 ) {
			throw new IllegalArgumentException( "Invalid port: Must be in [1, 65535]" );
		}

		this.serverPort = serverPort;
	}

	/* 
	   Note: hashCode() and equals() are needed so that
			 this class can be used as a key in maps.
	 */

	/** {@inheritDoc} */
	@Override
	public int hashCode() {

		// Strings cache their hash code for efficiency
		return jid.hashCode();
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals( Object otherObject ) {

		// Check memory addresses are equal
		if ( this == otherObject ) 
			return true;

		// Check if null
		if ( otherObject == null )
			return false;

		// Check the class types
		if ( ! this.getClass().equals ( otherObject.getClass() ) )
			return false;

		// Cast the object
		JabberID other = (JabberID) otherObject;

		// Test if fields are the same
		// JID is assumed to uniquely identify this object
		return ( jid.equals( other.jid ) );
	}

	/** JabberID. */
	private String jid;
	/** User name. */
	private String username;
	/** Domain. */
	private String domain;
	/** Resource. */
	private String resource;
	/** Password. */
	private String password;
	/** Server Name/IP. */
	private String serverName;
	/** Server port. */
	private int serverPort;
}
