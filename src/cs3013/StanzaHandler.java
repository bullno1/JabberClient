package cs3013;

import javax.xml.stream.XMLStreamReader;

public interface StanzaHandler {
	public boolean onStanza(XMLStreamReader parser) throws Exception;
}
