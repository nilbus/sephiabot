public class WTFException extends java.lang.Exception {
	public WTFException() {
		super("WTFException!");
	}

	public WTFException(String message) {
		super("WTFException: " + message);
	}
}
