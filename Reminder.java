import java.util.regex.*;

class Reminder {
	String target;
	String sender;
	String message;
	long timeSent;
	long timeToArrive;
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

		String ones = "(one|two|three|four|five|six|seven|eight|nine)";
		String teens = "(ten|eleven|twelve|(thir|four|fif|six|seven|eigh|nine)teen)";
		String tens = "(twenty|thirty|fou?rty|fifty|sixty|seventy|eighty|ninety)[- ]?";
		String words = "(a (few|couple)( of)?)";
		String numberWords = "("+tens+"|("+tens+")?"+ones+"|"+teens+"|"+words+")";

		String months = "((jan(uary)?|mar(ch)?|apr(il)?|may|june?|july?|aug(ust)?|sep(t(ember)?)?|oct(ober)?|nov(ember)?|dec(ember)?)\\.?)";
		String days = "((sun|mon|tues?|wed(nes)?|thu(r(s)?)?|fri|sat(ur)?)(day)?)";
		String dateNumber = "([0-9]?(1(st)?|2(nd)?|3(rd)?|[0-9&&[^1-3]](th)?))";

		String time = "[0-2]?[0-9](:[0-9]{2})?( ?([ap]m?)?| in the morning| at night| in the evening)?";
		String duration = "(([0-9]+|an?|"+numberWords+") ?"+"(s(ec(ond)?s?)?|m(in(ute)?s?)?|h((ou)?rs?)?|d(ays?)?|w(ee)?ks?|mo(nth)?s?|y(ea)?rs?)|[0-9]+)";
		String date = "("+days+"|(the )?"+dateNumber+" of "+months+"|("+months+"( the)?|the) "+dateNumber+")"; //Wed|the 4th of july|Jul 4th|the 4th
		String onReturn = "(when (I'm back|\\w+ ((gets?|comes?) (in|here|back|on(line)?)|returns?)))";

		String pattern = "\\b(at (ab(ou)?t )?"+time+"|in (ab(ou)?t )?"+duration+"|on "+date+"|"+onReturn+")\\b";
		//Add within, sometime, after, before, later
		//Add 'general' specifics like tonight, tomorrow, next week, a while
		//Add remind me about
		Pattern when = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		Matcher matcher = when.matcher(message);
		if (!matcher.find())
			throw new WTFException("Pattern did not match");
		this.message = matcher.group();
		this.timeToArrive = 0;
	}

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
