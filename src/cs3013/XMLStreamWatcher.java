package cs3013;

import javax.xml.stream.XMLStreamReader;

class XMLStreamWatcher implements Runnable {
	public XMLStreamWatcher(XMLStreamReader reader,
	                        TypedCallback<Boolean, XMLStreamReader> callback) {
		this.reader = reader;
		this.callback = callback;
	}
	
	@Override
	public void run() {
		try {
			while(true) {
				reader.next();
				if(!callback.call(reader)) {
					break;
				}
			}
		} catch (Exception e) {
			if(!stopped)
				e.printStackTrace();
		}
	}
	
	public void start() {
		thread = new Thread(this);
		thread.start();
	}
	
	public void stop() {
		stopped = true;
		thread.interrupt();
	}

	private boolean stopped = false;
	private Thread thread;
	private XMLStreamReader reader;
	private TypedCallback<Boolean, XMLStreamReader> callback;
}
