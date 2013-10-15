package cs3013;

public interface Plugin {
	void init(JabberClient client);
	void terminate();
}
