class Message {
	String target;
	String sender;
	String message;
	long time;
	Message next;

	Message(String target, String message, String sender) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		time = System.currentTimeMillis();
		next = null;
	}

	Message(String target, String message, String sender, long time) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.time = time;
		next = null;
	}
}
