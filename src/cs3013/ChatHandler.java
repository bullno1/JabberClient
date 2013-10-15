package cs3013;

import java.io.IOException;

public interface ChatHandler {
	public void sendMessage(String msg) throws IOException;
}
