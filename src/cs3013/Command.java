package cs3013;

import java.io.IOException;

public interface Command {
	public String getShortDescription();
	public String getLongDescription();
	
	public void execute(String[] args) throws IOException;
}
