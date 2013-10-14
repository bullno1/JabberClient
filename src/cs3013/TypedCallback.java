package cs3013;

public interface TypedCallback<ReturnType, ArgType> {
	public ReturnType call(ArgType arg) throws Exception;
}
