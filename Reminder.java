import java.util.regex.*;

class Reminder {
	String target;
	String sender;
	String message;
	long timeSent;
	long timeToArrive;
	String timeExpression;
	String originalTimeExpression;
	Reminder next;
	boolean notified;

	//This constructor will figure out the timeToArrive itself, or throw a WTFException
	//For now, (until I write it) this constructor sets the time phrase to message instead of timeSent, for testing.
	Reminder(String target, String message, String sender) throws WTFException {
		System.out.println("Message: '"+message+"'\n");
		this.target = target;
		this.sender = sender;
		this.timeSent = System.currentTimeMillis();
		this.notified = false;
		ParseTime pt = new ParseTime();
		this.timeToArrive = pt.textToTime(message);
		this.timeExpression = pt.getTimeExpression();
		this.originalTimeExpression = pt.getOriginalTimeExpression();
		this.message = message.replaceAll(this.originalTimeExpression + " ", "");
		this.next = null;
	}

	Reminder(String target, String message, String sender, long timeToArrive) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.timeSent = System.currentTimeMillis();
		this.timeToArrive = timeToArrive;
		this.notified = false;
		this.next = null;
	}

	Reminder(String target, String message, String sender, boolean notified, long timeToArrive, long timeSent) {
		this.target = target;
		this.message = message;
		this.sender = sender;
		this.timeSent = timeSent;
		this.timeToArrive = timeToArrive;
		this.notified = notified;
		this.next = null;
	}

	public String getOriginalTimeExpression() {
		return originalTimeExpression;
	}

	public String getTimeExpression() {
		return timeExpression;
	}
}
