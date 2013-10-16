package cs3013;

public interface Command {
	public String getShortDescription();
	public String getLongDescription();
	
	public void execute(String[] args) throws InterruptedException;
}
