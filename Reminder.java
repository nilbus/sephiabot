class Reminder {
	String target;
	String sender;
	String message;
	long timeSent;
	long timeToArrive;
	Reminder next;
	boolean notified;

	Reminder(String target, String message, String sender, long timeToArrive) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.timeSent = System.currentTimeMillis();
		this.timeToArrive = timeToArrive;
		this.notified = false;
		next = null;
	}

	Reminder(String target, String message, String sender, boolean notified, long timeToArrive, long timeSent) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.timeSent = timeSent;
		this.timeToArrive = timeToArrive;
		this.notified = notified;
		next = null;
	}
}
