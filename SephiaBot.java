/*
TODO: Reminders
TODO: Better handling of dropped connections, etc.
TODO: On JOINs, channel is prefixed with a :. Make sure this is accounted for.
TODO: Track nick changes so who is here works.
*/
import java.io.*;
import java.util.*;

class SephiaBot implements IRCListener {

	private SephiaBotData data;
	
	private int historySize;
	private String historyNick[];
	private String historyText[];
	private String lastRepeat;

	private IRCIO ircio;
	private BufferedWriter logOut[];
	private IRCServer server;

	private long nextWho;
	private long nextHi;

	private boolean censor() { return data.getCensor(); }

	public static void main(String args[]) {
		String cfgPath = "sephiabot.xml";
		final String usage = "\nUsage: sephiabot [-c config file]\n" +
			" Default is to search for sephiabot.xml in the current directory.";

		if (args != null && args.length > 0) {
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("--help")) {
					System.out.println(usage);
					System.exit(0);
				}
			}
			if (args[0].equals("-c"))
				if (args.length > 1)
					cfgPath = args[1];
				else {
					System.out.println("You must specify the path of your config file with -c" + usage);
					System.exit(0);
				}
			else {
				System.out.println("Invalid arguments." + usage);
				System.exit(0);
			}
		}

		SephiaBot sephiaBot = new SephiaBot(cfgPath);
		sephiaBot.connect();

		while (true) {
			sephiaBot.poll();
		}
	}

	public SephiaBot(String config) {

		this.historySize = 3;
		this.historyNick = new String[this.historySize];
		this.historyText = new String[this.historySize];

		this.nextWho = 0;
		this.nextHi = 0;

		this.data = new SephiaBotData(config);
		
		log("----------------------------------------------------------------------------\nSephiaBot Started!");
		
		data.parseConfig();

		log("Network: " + data.getNetwork() + " " + data.getPort() + " : " + data.getName());

		try {
			logOut = new BufferedWriter[data.getNumChannels()];
			for (int i = 0; i < data.getNumChannels(); i++) {
				logOut[i] = new BufferedWriter(new FileWriter(data.getLogdir() +
						"/log-" + data.getChannel(i) + ".txt", true));
			}
		} catch (IOException ioe) {
			logerror("Couldn't open log file.");
		}
	}

	String makeTime(long formerTime) {
		long currentTime = System.currentTimeMillis();
		long lowTime, highTime;
		if (formerTime < currentTime) {
			lowTime = formerTime;
			highTime = currentTime;
		} else {
			lowTime = currentTime;
			highTime = formerTime;
		}
		long timeInSeconds = data.timeInSeconds(lowTime, highTime);
		long timeInMinutes = data.timeInMinutes(lowTime, highTime);
		long timeInHours = data.timeInHours(lowTime, highTime);
		long timeInDays = data.timeInDays(lowTime, highTime);
		if (timeInSeconds < 60) {
			return "about " + timeInSeconds + " second" + ((timeInSeconds!=1)?"s":"");
		} else if (timeInMinutes < 60) {
			return "about " + timeInMinutes + " minute" + ((timeInMinutes!=1)?"s":"");
		} else if (timeInHours < 24) {
			return "about " + timeInHours + " hour" + ((timeInHours!=1)?"s":"");
		} else if (timeInDays < 7) {
			return "about " + timeInDays + " day" + ((timeInDays!=1)?"s":"");
		} else {
			return "more than a week";
		}
	}

	void connect() {

		//Quickly build a channel list.
		String[] channels = new String[data.getNumChannels()];
		for (int i = 0; i < channels.length; i++)
			channels[i] = data.getChannel(i);

		ircio = new IRCIO(this, data.getNetwork(), data.getPort());
		ircio.login(channels, data.getName());
		server = new IRCServer(data.getNetwork(), data.getPort(), channels);

	}

	void poll() {
		ircio.poll();

		checkForReminders();
		
		try {
			Thread.sleep(100);
		} catch (InterruptedException ie) {}

	}

	private boolean iregex(String pattern, String string) {
		return data.iregex(pattern, string);
	}
	
	private boolean iequals(String str1, String str2) {
		return data.iequals(str1, str2);
	}

	   
	//Find a user by his IRC nick. If an IRC nick is found matching the specified nick, any users logged in
	// with that host are returned. Otherwise, any user names matching the specified nick are returned. Note
	// that if someone's occupies a nick but is not logged in as that nick, this function will return null.
	public User getUserByNick(String name) {
		for (int i = 0; i < server.channels.length; i++) {
			for (IRCUser curr = server.channels[i].users; curr != null; curr = curr.next) {
				if (curr.name.equals(name)) {
					return data.getUserByHost(curr.host);
				}
			}
		}
		return data.getUserByName(name);
	}
	
	public void checkForMessages(String nick, String host, String recipient) {
		//Check if this person has any messages.
		User user = data.getUserByHost(host);
		int totalMessages = 0;
		Message messages[] = data.getMessagesByReceiver(nick, user);
		Reminder reminders[] = data.getRemindersByReceiver(nick, user);

		if (messages.length > 0 && reminders.length > 0)
			ircio.privmsg(recipient, nick + ", you have messages and reminders!");
		else if (messages.length > 0)
			ircio.privmsg(recipient, nick + ", you have messages!");
		else if (reminders.length > 0)
			ircio.privmsg(recipient, nick + ", you have reminders!");
		else
			return;
		
		for (int i = 0; i < messages.length; i++) {
			Message message = messages[i];
			if (totalMessages >= 5) {
				ircio.privmsg(recipient, "You have more messages.");
				return;
			}
			totalMessages++;
			ircio.privmsg(recipient, "Message from " + message.sender + " [" + makeTime(message.time) + " ago]:" + message.message);
			data.removeMessage(message);
		}
		
		for (int i = 0; i < reminders.length; i++) {
			Reminder reminder = reminders[i];
			if (!reminder.notified)
				continue;
			if (totalMessages >= 5) {
				ircio.privmsg(recipient, "You have more reminders.");
				return;
			}
			totalMessages++;
			String sender = reminder.sender;
			if (iequals(reminder.sender, reminder.target))
				sender = "yourself";
			ircio.privmsg(recipient, "Reminder from " + sender + " [" + makeTime(reminder.timeSent) + " ago]: " + reminder.message);
			data.removeReminder(reminder);
		}
	}

	public void checkForReminders() {
		Reminder reminders[] = data.getUnnotifiedReminders();
		if (reminders.length <= 0)
			return;
		for (int i = 0; i < reminders.length; i++) {
			Reminder reminder = reminders[i];
			reminder.notified = true;
			for (int j = 0; j < server.channels.length; j++) {
				if (!server.channels[j].userInChannel(reminder.target))
					continue;
				String sender = reminder.sender;
				if (iequals(reminder.sender, reminder.target))
					sender = "yourself";
				ircio.privmsg(server.channels[j].name, reminder.target + ", reminder from " + sender + " [" + makeTime(reminder.timeSent) + " ago]: " + reminder.message);
			}
		}
		data.writeData();
		data.findNextReminderTime();
	}
	
	public void processReminder(String sender, String target, String msg) {
		StringTokenizer tok = new StringTokenizer(msg, " ");
		if (!tok.hasMoreElements())
			return;
		String when = tok.nextToken(" ");
		String message = null;
		long goalTime = 0;
		if (iequals(when, "in")) {
//			processReminderIn(tok.nextToken(""));
		} else if (iequals(when, "on")) {
		} else if (iequals(when, "at")) {
		} else {
			message = msg;
			goalTime = System.currentTimeMillis() + 1000*60;
//			sendReminder(sender, target, message, goalTime);
		}
	}
	
	public boolean processReminderIn(String sender, String target, String msg) {
		StringTokenizer tok = new StringTokenizer(msg, " ");
		if (!tok.hasMoreElements())
			return false;
		String number = tok.nextToken();
		if (!tok.hasMoreElements())
			return false;
		String time = tok.nextToken();
		long goalTime = System.currentTimeMillis() + data.stringToNumber(number) + data.stringToTime(time);
//		sendReminder(sender, target, message, goalTime);
		return true;
	}
	
	public void sendReminder(String sender, String target, String message, String goalTime) {
//		data.addReminder(target, message, sender, goalTime);
	}
	
	public void messagePrivEmote(String nick, String host, String recipient, String msg) {
		String log;
		
		log = "* " + nick + " " + msg;

		logfile(recipient, log);
		
		msg = msg.trim();

		data.updateUserTimes(nick, host);
		checkForMessages(nick, host, recipient);
		checkForBlacklist(nick, host, recipient);
		
		if (System.currentTimeMillis() > nextWho) { //!spam
			nextWho = System.currentTimeMillis() + 5000;
						
			if (iregex("hugs " + data.getName(), msg)) {
				if (data.isVino(host))
					ircio.privemote(recipient, "hugs Vino!");
				else if (censor())
					ircio.privemote(recipient, "hugs " + nick + "!");
				else
					ircio.privmsg(recipient, "Get the fuck off.");
			} else if (iregex("pets " + data.getName(), msg)) {
				ircio.privemote(recipient, "purrs.");
			}
	
			return;
		}
	}

	public void updateHistory (String nick, String msg) {
		for (int i = this.historySize - 1; i > 0; i--) {
			historyNick[i] = historyNick[i-1];
			historyText[i] = historyText[i-1];
		}
		historyNick[0] = nick;
		historyText[0] = msg;
	}

	public void messagePrivMsg(String nick, String host, String recipient, String origmsg) {
		boolean pm = false;
		String log;
		String msg = origmsg;

		if (iequals(recipient, data.getName())) {
			recipient = nick;
			pm = true;
		}

		log = "<" + nick + "> ";
		log += msg.substring(0, msg.length());

		logfile(recipient, log);

		msg = msg.trim();

		data.updateUserTimes(nick, host);
		checkForMessages(nick, host, recipient);
		checkForBlacklist(nick, host, recipient);
		updateHistory(nick, msg);
		
		String name = data.getName();

		//Say hello!
		int nameEnd = name.length() < 4 ? name.length() : 4;
		if (iregex(name.substring(0, nameEnd), msg)) {
			if (data.matchHellos(msg)) {
				if (System.currentTimeMillis() > nextHi) {  //!spam
					ircio.privmsg(recipient, data.getRandomHelloReply());
					nextHi = System.currentTimeMillis() + 500;
					return;
				}
			}
		}

		StringTokenizer tok = new StringTokenizer(msg, ",: ");
		String botname;
		if (!pm && tok.hasMoreElements()) {
			botname = tok.nextToken();
		} else {
			botname = "";
		}

		if (pm || talkingToMe(msg)) {

			//Remove the bot's name
			if (!pm)
				msg = msg.substring(msg.indexOf(" ")+1);

			if (iregex("bring out the strapon", msg)) {
				ircio.privemote(recipient, "steps forward with a large strapon and begins mashing potatoes.");
				return;
			}

			msg = data.removePunctuation(msg, ".?!");

			//BEGIN COLLOQUIAL COMMANDS
			//These commands can be used anywhere if the bot's name is spoken first.
			if (iequals("who are you", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "I am an advanced SephiaBot channel bot.");
					ircio.privmsg(recipient, "I'll kick your " + (censor()?"butt":"ass") + " in days that end in 'y'.");
					ircio.privmsg(recipient, "I was written by Vino. Vino rocks.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if ( iregex("what does marsellus wallace look like", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "He's black.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who wrote you", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "I was written by Vino. Vino rocks.");
					ircio.privmsg(recipient, "Nilbus helped too.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who is here", msg) && nick.equals("Nilbus")) {
				if (System.currentTimeMillis() > nextWho) {	//!spam

					int channum = channelNumber(recipient);
					if (channum == -1 || pm) {
						ircio.privmsg(recipient, "It's just you and me in a PM, buddy.");
						nextWho = System.currentTimeMillis() + 5000;
						return;
					} else {
						StringBuffer buf = new StringBuffer("Users in this channel:");
						IRCUser current = server.channels[channum].users;
						for (int i = 0; i < server.channels[channum].numusers; i++) {
							buf.append(" " + current.name);
							current = current.next;
						}
						//ircio.privmsg(recipient, buf.toString());
						ircio.privmsg("Nilbus", buf.toString()); //XXX: debug
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				}
			} else if (iregex("who is vino", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "A dirty cuban.");
					ircio.privmsg(recipient, "And my daddy.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who is remy", msg)
					|| iregex("who is luckyremy",msg)) {
				if (System.currentTimeMillis() > nextWho) {     //!spam
					ircio.privmsg(recipient, "Father of the Black Sheep.");
					ircio.privmsg(recipient, "Harbinger of Doom.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who ?i?\'?s", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					User target = data.getUserByName(msg.substring(msg.lastIndexOf(' ')+1, msg.length()));
					if (target == null)
						ircio.privmsg(recipient, "Nobody important.");
					else
						ircio.privmsg(recipient, target.description);
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (!censor() && iregex("are you (sexy|hot)", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "Fuck yes.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (!censor() && iregex("want to cyber", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					if (!data.isVino(host)) {
						ircio.privmsg(recipient, "Fuck no.");
					} else {
						ircio.privmsg(recipient, "Take me, " + nick + "!");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("wh?[aeu]re?('?[sz]| i[sz]| si| be?)( m(a[ih]|y))? vino", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					User vino = data.getUser(SephiaBotData.USER_VINO);
					if (vino.away == null) {
						if (vino.lastTalked > 0)
							ircio.privmsg(recipient, "If he's not here, I dunno. He hasn't told me he's gone. The last time he said anything was " + makeTime(vino.lastTalked) + " ago.");
						else
							ircio.privmsg(recipient, "If he's not here, I dunno. He hasn't told me he's gone.");
					} else {
						ircio.privmsg(recipient, "He's " + vino.away + ". He's been gone for " + makeTime(vino.leaveTime) + ".");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("wh?[aeu]re?('?[sz]| i[sz]| si| be?)( m(a[ih]|y))?", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String targetName = msg.substring(msg.lastIndexOf(' ')+1, msg.length());
					targetName = data.removePunctuation(targetName, "!?,");
					boolean foundAway = false;
					if (iregex("eve?ry(b(o|ud)dy|(1|one?))", targetName)) {
						//Find out where everybody is and tell the channel.
						for (int i = 0; i < data.getNumUsers(); i++) {
							User user = data.getUser(i);
							//Do not display away message if the person has not logged in for a while.
							if (user.away != null && data.timeInWeeks(user.lastTalked, System.currentTimeMillis()) < 1) {
								ircio.privmsg(recipient, user.userName + " has been " + user.away + " for " + makeTime(user.leaveTime) + ".");
								foundAway = true;
							}
						}
						if (!foundAway)
							ircio.privmsg(recipient, "Everyone is present and accounted for.");
						return;
					}
					if (iequals(targetName, botname))
						return;
					User target = getUserByNick(targetName);
					if (target == null) {
						ircio.privmsg(recipient, "Like I know.");
						return;
					} else
						targetName = target.userName; //use correct caps
					if (target.away == null) {
						if (target.lastTalked > 0)
							ircio.privmsg(recipient, "I don't know, the last time they said anything was " + makeTime(target.lastTalked) + " ago.");
						else
							ircio.privmsg(recipient, "I don't know.");
					} else {
						ircio.privmsg(recipient, targetName + " is " + target.away + ". " + targetName + " has been gone for " + makeTime(target.leaveTime) + ".");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who am i", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					User target = data.getUserByHost(host);
					if (data.isVino(host)) {
						ircio.privmsg(recipient, "Daddy!");
						ircio.privemote(recipient, "hugs " + nick + ".");
					} else if (target != null) {
						ircio.privmsg(recipient, "Someone I know.");
					} else {
						ircio.privmsg(recipient, "Nobody important.");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("who('| i)?s your daddy", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "Vino's my daddy, ugh! Spank me again Vino!");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("knock knock", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privmsg(recipient, "Who's there?");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (!censor() && iregex("i suck dick", msg)) {
				if (System.currentTimeMillis() > nextWho) { //!spam
					ircio.privmsg(recipient, "Yeah, we know you do.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (!censor() && iregex("words of wisdom", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String phrase = data.randomPhrase("wordsofwisdom.txt");
					if (phrase != null)
						ircio.privmsg(recipient, phrase);
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("roll (the )?dice", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					Random rand = new Random();
					int dice = rand.nextInt(5)+2;
					int sides = rand.nextInt(5)+6;
					ircio.privemote(recipient, "rolls " + dice + "d" + sides + " dice and gets " + (dice*sides+1) + ".");
					if (!data.isVino(host)) {
						ircio.privemote(recipient, "kills " + nick + ".");
					} else {
						ircio.privemote(recipient, "hugs " + nick + ".");
					}
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("do a little dance", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					ircio.privemote(recipient, "makes a little love.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("excuse", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String excuse = data.randomPhrase("excuses.txt");
					if (excuse != null)
						ircio.privmsg(recipient, "Your excuse is: " + excuse);
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
				
			//BEGIN COMMAND SECTION.
			//These are one-word commands only. The command is the first thing you say after the bot name. botname, command arguments.
			} else if (tok.hasMoreElements()) {
				String cmd = tok.nextToken(" ");
				if (tok.hasMoreElements() && (cmd.startsWith(",") || cmd.startsWith(":"))) { 
					cmd = tok.nextToken(" ");
				}
				if (iequals(cmd, "kill")) {
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "KILL! KILL! KILL!");
						return;
					}
					String killed = tok.nextToken(" ");
					User killerUser = data.getUserByHost(host);
					User killedUser = getUserByNick(killed);
					if ((killerUser == null || (killedUser != null && killedUser.memberType > killerUser.memberType)) && !data.isVino(host)) {
						ircio.privemote(recipient, "giggles at " + nick);
					} else if (iequals(killed, botname)) {
						ircio.privmsg(recipient, ":(");
					} else {
						if (data.isVino(host))
							killerUser = data.getUser(SephiaBotData.USER_VINO);
						int killedAccess = getAccess(killed, channelNumber(recipient));
						if (killedAccess != -1) {
							ircio.privmsg(recipient, "It would be my pleasure.");
							ircio.kick(recipient, killed, "This kick was compliments of " + killerUser.userName + ". Have a nice day.");
						} else if (killed.equalsIgnoreCase("message")) {
							if (!tok.hasMoreElements()) {
								ircio.privmsg(recipient, "Which message? I need a number");
								return;
							}
							try {
								int msgIndex = Integer.parseInt(tok.nextToken()) - 1;
								User user = data.getUserByHost(host);
								Message messages[] = data.getMessagesBySender(nick, user);
								if (msgIndex >= messages.length || msgIndex < 0) {
									ircio.privmsg(recipient, "You don't have that many messages.");
									return;
								}
								ircio.privmsg(recipient, "Message removed from " + messages[msgIndex].sender + " to " + messages[msgIndex].target + " " + makeTime(messages[msgIndex].time) + " ago:" + messages[msgIndex].message);
								data.removeMessage(messages[msgIndex]);
							} catch (NumberFormatException nfe) {
								ircio.privmsg(recipient, "...if you can call that a number.");
							}
							return;
						} else if (killed.equalsIgnoreCase("reminder")) {
							if (!tok.hasMoreElements()) {
								ircio.privmsg(recipient, "Which reminder? I need a number");
								return;
							}
							try {
								int msgIndex = Integer.parseInt(tok.nextToken()) - 1;
								User user = data.getUserByHost(host);
								Reminder reminders[] = data.getRemindersBySender(nick, user);
								if (msgIndex >= reminders.length || msgIndex < 0) {
									ircio.privmsg(recipient, "You don't have that many reminders.");
									return;
								}
								Reminder reminder = reminders[msgIndex];
								String timeToArrive;
								if (System.currentTimeMillis() > reminder.timeToArrive)
									timeToArrive = makeTime(reminder.timeToArrive) + " ago";
								else
									timeToArrive = makeTime(reminder.timeToArrive) + " from now";
								ircio.privmsg(recipient, "Reminder removed from " + reminder.sender + " to " + reminder.target + " " + makeTime(reminder.timeSent) + " ago for " + timeToArrive + ": " + reminder.message);
								data.removeReminder(reminder);
							} catch (NumberFormatException nfe) {
								ircio.privmsg(recipient, "...if you can call that a number.");
							}
							return;
						} else
							ircio.privmsg(recipient, "Kill who what now?"); 
						return;
					}
				} else if (iequals(cmd, "tell")) {
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Tell who what?");
						return;
					}

					String target = tok.nextToken(" ");
					String sender = nick;
					target = data.removePunctuation(target, ".!,");
					//If the target is logged in, send the message to his username instead so he will always get it if he is logged in.
					User targetUser = getUserByNick(target);
					if (targetUser != null)
						target = targetUser.userName;
					//If the sending user is logged in, send the message as his username instead so that all the messages are sent by the same user.
					User senderUser = data.getUserByHost(host);
					if (senderUser != null)
						sender = senderUser.userName;

					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Tell " + target + " what?");
						return;
					}
					String message = tok.nextToken("");
					//If the target was "everybody" or "everyone" then send the message to every user.
					if (data.getUserByHost(host) != null && (iequals(target, "everybody") || iequals(target, "everyone")))
						data.sendMessageToAllUsers(message, sender);
					else
						data.addMessage(target, message, sender);
					data.writeData();
					ircio.privmsg(recipient, "OK, I'll make sure to let them know.");
				} else if (iregex("^remind(er)?$", cmd)) {
					// Bit, remind [person] ([at time] || [on day]) OR ([in duration]) [to OR that] [something]
					// Bit, remind [person] [to OR that] [something] ([at time] || [on day]) OR ([in duration])
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Remind who what when?");
						return;
					}
					String target = tok.nextToken(" ");
					String sender = nick;
					target = data.removePunctuation(target, ".!,");
					if (iregex("(me|myself)", target))
						target = nick;
					//If the target is logged in, send the reminder to his username instead so he will always get it if he is logged in.
					User targetUser = getUserByNick(target);
					if (targetUser != null)
						target = targetUser.userName;
					//If the sending user is logged in, send the reminders as his username instead so that all the reminders are sent by the same user.
					User senderUser = data.getUserByHost(host);
					if (senderUser != null)
						sender = senderUser.userName;
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Remind " + target + " when what?");
						return;
					}
					try {
						Reminder reminder = new Reminder(target, tok.nextToken("").substring(1), sender);
						ircio.privmsg(recipient, "OK, I'll remind you " + reminder.message);
					} catch (WTFException wtf) {
						ircio.privmsg(recipient, "Um, when was that?");
					}
					
					/*
					long goalTime = System.currentTimeMillis() + 1000*5;
					if (message == null) {
						if (!tok.hasMoreElements()) {
							ircio.privmsg(recipient, "Remind " + target + " what?");
							return;
						}
						message = tok.nextToken("");
					} else if (tok.hasMoreElements()) {
						message += tok.nextToken("");
					}
					data.addReminder(target, message, sender, goalTime);
					ircio.privmsg(recipient, "OK, I won't forget.");
					*/
				} else if (iregex("^(butt?)?se(x|ck[sz])$", cmd)) {
					if (!tok.hasMoreElements()) {
						ircio.privemote(recipient, "anally rapes " + nick + ".");
						return;
					}
					String sexed = tok.nextToken(" ");
					if (iregex("^vino", sexed)) {
						ircio.privemote(recipient, "screams as Vino penetrates every orifice of her body!");
					} else if (iequals(botname, sexed)) {
						ircio.privemote(recipient, "furiously works the potato masher!");
					} else {
						int sexedAccess = getAccess(sexed, channelNumber(recipient));
						if (sexedAccess == -1) {
							sexed = nick;
						}
						ircio.privemote(recipient, "anally rapes " + sexed + ".");
					}
				} else if (iequals(cmd, "reboot")) {
					if (data.isAdmin(host)) {
						ircio.privmsg(recipient, "Be right back.");
						data.writeData();
						System.exit(1);
					} else
						ircio.privmsg(recipient, "No.");
					return;
				} else if (iequals(cmd, "shutdown")) {
					if (data.isVino(host)) {
						ircio.privmsg(recipient, "Goodbye everybody!");
						data.writeData();
						System.exit(0);
					} else {
						ircio.privmsg(recipient, "No.");
					}
					return;
				} else if (iequals(cmd, "reload")) {
					if (data.isAdmin(host)) {
						data.parseConfig();
						ircio.privmsg(recipient, "Done.");
					} else {
						ircio.privmsg(recipient, "No.");
					}
					return;
				} else if (iequals(cmd, "save")) {
					if (data.isAdmin(host)) {
						data.writeData();
						ircio.privmsg(recipient, "Done.");
					} else {
						ircio.privmsg(recipient, "No.");
					}
					return;
				} else if (iequals(cmd, "listhosts")) {
					User user = data.getUserByHost(host);
					if (user == null)
						return;
					String buffer = "Hosts logged in as " + user.userName + ":";
					for (int i = 0; i < 10; i++)
						if (user.hosts[i] != null && user.hosts[i].length() > 0)
							buffer += " " + (i+1) + ": " + user.hosts[i];
					ircio.privmsg(nick, buffer);
				} else if (iequals(cmd, "logout")) {
					//TODO: data.logout(host) instead of doing this manually.
					User user = data.getUserByHost(host);
					if (user == null)
						return;
					if (!tok.hasMoreElements()) {
						for (int i = 0; i < 10; i++) {
							if (user.hosts[i] != null && user.hosts[i].equals(host)) {
								user.hosts[i] = null;
								ircio.privmsg(nick, "It's too bad things couldn't work out.");
								data.writeData();
								return;
							}
						}
						ircio.privmsg(nick, "Your host is not logged in.");
						return;
					}
					try {
						int i = Integer.parseInt(tok.nextToken("").trim())-1;
						if (i < 0 || i >= 10)
							return;
						if (user.hosts[i] == null || user.hosts[i].length() <= 0)
							ircio.privmsg(nick, "That host is not logged in.");
						else {
							ircio.privmsg(nick, "It's too bad things couldn't work out.");
							user.hosts[i] = null;
						}
					} catch (NumberFormatException nfe) {
					}
					data.writeData();
				} else if (iequals(cmd, "login")) {
					if (tok.countTokens() < 2) {
						ircio.privmsg(nick, "Yeah. Sure. Whatever.");
						return;
					}
					String login = tok.nextToken(" ").trim();
					String passwd = tok.nextToken("").trim();
					if (data.validateLogin(login, passwd)) {
						boolean foundSpot = false;
						int i, userID = -1;
						User user = data.getUserByName(login);
						if (user == null) {	//WTFException
							ircio.privmsg(nick, "WTF? Tell Vino you saw this.");
							return;
						}
						// If getUserByHost returns non-null then the user is logged into this user already,
						// or is logged in as another user and may not re-log in.
						if (data.getUserByHost(host) != null) {
							ircio.privmsg(nick, "Silly you. You're already logged in.");
							return;
						}
						if (data.loginUser(user, host))
							if (data.isVino(user))
								ircio.privmsg(nick, "Hi, daddy! :D");
							else
								ircio.privmsg(nick, "What's up " + user.userName + "?");
						else
							ircio.privmsg(nick, "No spots left.");
						data.writeData();
					} else {
						ircio.privmsg(nick, "No cigar.");
						log("Failed login attempt by " + nick + "!" + host + " with " + login + "/" + passwd + ".");
					}
				} else if (iequals(cmd, "i'm")) {
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "You're what?");
						return;
					}
					User user = data.getUserByHost(host);
					if (user == null) {
						ircio.privmsg(recipient, "I don't care.");
						return;
					}
					String location = tok.nextToken("").trim();
					if (iregex("^ba+ck", location)) {
						if (user.away == null) {
							ircio.privmsg(recipient, "Of course you are honey.");
						} else {
							ircio.privmsg(recipient, "Welcome back! You've been away for " + makeTime(user.leaveTime) + ".");
							user.away = null;
						}
					} else {
						ircio.privmsg(recipient, "Have fun!");
						//Remove punctuation from the end
						location = data.removePunctuation(location, ".!,?");
						user.away = location.replaceAll("\"", "'");
						user.leaveTime = System.currentTimeMillis();
					}
					data.writeData();
					return;
				} else if (iequals(cmd, "messages")) {
					User user = data.getUserByHost(host);
					int lastIndex, firstIndex = 0;
					if (tok.hasMoreElements()) {
						try {
							firstIndex = Integer.parseInt(tok.nextToken())-1;
							if (firstIndex < 1)
								firstIndex = 1;
						} catch (NumberFormatException nfe) {
						}
					}
					Message messages[] = data.getMessagesBySender(nick, user);
					if (messages.length == 0) {
						ircio.privmsg(recipient, "You havent sent any messages.");
						return;
					}
					if (firstIndex >= messages.length) {
						ircio.privmsg(recipient, "You don't have that many messages.");
						return;
					}
					lastIndex = firstIndex + 5;
					if (lastIndex >= messages.length)
						lastIndex = messages.length-1;
					ircio.privmsg(recipient, "You have sent the following messages:");
					for (int i = firstIndex; i <= lastIndex; i++) {
						ircio.privmsg(recipient, "Message " + (i+1) + ": To " + messages[i].target + " " + makeTime(messages[i].time) + " ago:" + messages[i].message);
					}
				} else if (iequals(cmd, "reminders")) {
					User user = data.getUserByHost(host);
					int lastIndex, firstIndex = 0;
					if (tok.hasMoreElements()) {
						try {
							firstIndex = Integer.parseInt(tok.nextToken())-1;
							if (firstIndex < 1)
								firstIndex = 1;
						} catch (NumberFormatException nfe) {
						}
					}
					Reminder reminders[] = data.getRemindersBySender(nick, user);
					if (reminders.length == 0) {
						ircio.privmsg(recipient, "You havent sent any reminders.");
						return;
					}
					if (firstIndex >= reminders.length) {
						ircio.privmsg(recipient, "You don't have that many reminders.");
						return;
					}
					lastIndex = firstIndex + 5;
					if (lastIndex >= reminders.length)
						lastIndex = reminders.length-1;
					ircio.privmsg(recipient, "You have sent the following reminders:");
					for (int i = firstIndex; i <= lastIndex; i++) {
						Reminder reminder = reminders[i];
						String target = reminder.target;
						if (iequals(reminder.target, nick))
							target = "you";
						String sender = reminder.sender;
						if (iequals(reminder.sender, nick))
							sender = "you";
						String timeToArrive;
						if (System.currentTimeMillis() > reminder.timeToArrive)
							timeToArrive = makeTime(reminder.timeToArrive) + " ago";
						else
							timeToArrive = makeTime(reminder.timeToArrive) + " from now";
						ircio.privmsg(recipient, "Reminder " + (i+1) + ": From " + sender + " to " + target + ", sent " + makeTime(reminder.timeSent) + " ago for " + timeToArrive + ": " + reminder.message);
					}
				} else if (iequals(cmd, "say")) {
					if (!data.isAdmin(host)) {
						ircio.privmsg(recipient, "No.");
						return;
					}
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Say what?!?");
						return;
					}
					String inchannel = recipient;
					if (iequals("in", tok.nextToken())) {
						inchannel = tok.nextToken();
					}
					ircio.privmsg(inchannel, tok.nextToken("").substring(1));
					return;
				//TODO: Make mode setting colloquial
				} else if (iequals(cmd, "mode")) {
					if (!data.isAdmin(host)) {
						ircio.privmsg(recipient, "No.");
						return;
					}
					String inchannel = recipient;
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "What channel?");
						return;
					}
					inchannel = tok.nextToken();
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "Who?");
						return;
					}
					String who = tok.nextToken();
					String mode;
					if (!tok.hasMoreElements()) {
						ircio.privmsg(recipient, "What mode?");
						return;
					} else mode = tok.nextToken();
					System.out.println("MODE " + inchannel + " " + mode + " " + who + "\n");
					ircio.setMode(who, inchannel, mode);
					return;
				}
			}
		} else if (spelledMyNameWrong(botname)) {
//			ircio.privmsg(recipient, nick + ", " + spell);
		} else {
			//Act like a parrot only if the message isn't a command
			//Only repeat when 2 people said the same thing, but not the 3rd time.
			if (iequals(historyText[0], historyText[1]) && !iequals(historyText[0], this.lastRepeat) && !iequals(historyNick[0], historyNick[1]) && !historyText[0].trim().startsWith("!")) {
				this.lastRepeat = historyText[0];
				ircio.privmsg(recipient, historyText[0]);
			}
		}
		
		//Bot has been mentioned?
		if (pm || talkingToMe(origmsg) || iregex(name, msg)) {
			if (!censor()) {
				if (iregex("fuck you", msg)) {
					if (System.currentTimeMillis() > nextWho) {	//!spam
						ircio.privmsg(recipient, "Fuck you too, buddy.");
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				} else if (iregex("screw you", msg)) {
					if (System.currentTimeMillis() > nextWho) {	//!spam
						ircio.privmsg(recipient, "Screw you too, buddy.");
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				} else if (iregex("you suck", msg)) {
					if (System.currentTimeMillis() > nextWho) {	//!spam
						ircio.privmsg(recipient, "I suck, but you swallow, bitch.");
						nextWho = System.currentTimeMillis() + 5000;
						return;
					}
				}
			}
			if (iregex("(thank( ?(yo)?u|[sz])\\b|\\bt(y|hn?x)\\b)", msg)) {
				if (System.currentTimeMillis() > nextWho) { //!spam
					ircio.privmsg(recipient, "No problem.");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			} else if (iregex("bounc[ye]", msg)) {
				if (System.currentTimeMillis() > nextWho) { //!spam
					ircio.privmsg(recipient, "Bouncy, bouncy, bouncy!");
					nextWho = System.currentTimeMillis() + 5000;
					return;
				}
			}
		}

	}

	public boolean talkingToMe(String msg) {
		String name = data.getName();
		int nameEnd = name.length() < 4 ? name.length() : 4;
		return iregex("^"+name.substring(0, nameEnd), msg);
	}

	public boolean spelledMyNameWrong(String msg) {
		int offset = data.getName().compareToIgnoreCase(msg);

		//These are common values. replacing a with 4 gives you -16, etc.
		//63 -16 45
		return (offset == 63 || offset == -16 || offset == 45 || offset == 50 || offset == -19);
	}

	   
	public void checkForBlacklist(String nick, String host, String channel) {
		//Check for blacklisted nicks.
		if (data.checkForBlacklist(nick)) {
			ircio.ban(channel, nick, host);
			ircio.kick(channel, nick, "You have been blacklisted. Please never return to this channel.");
			return;
		}
	}
		
	public int getAccess(String user, int channum) {
		if (channum == -1) {
//			log ("chan -1");                      //XXX: debug
			return -1;
		}
		IRCUser current = server.channels[channum].users;
		for (int i = 0; i < server.channels[channum].numusers; i++) {
			if (iequals(user, current.name)) {
//				log (current.name + " access " + current.access); //XXX: debug
				return current.access;
			}
			current = current.next;
		}
//		log(user + " access -1");  //XXX: debug
		return -1;
	}

	public void messageChannelJoin(String nick, String host, String channel) {

		String log;

		log = "--> " + nick + " has joined " + channel;

		logfile(channel, log);

		//Say something as you enter the channel!
		if (iequals(nick, data.getName())) {
			ircio.privmsg(channel, data.getGreeting());
		}

		if (iequals(nick, "metapod\\")) {
			ircio.privmsg(channel, "Heya meta.");
		}

		if (iequals(nick, "luckyremy")) {
			ircio.privemote(channel, "salutes as Remy enters.");
		}

		checkForBlacklist(nick, host, channel);
		
		int channum = channelNumber(channel);

		if (channum > -1) {
			server.channels[channelNumber(channel)].addUser(nick, host, IRCServer.ACCESS_NONE);
		}

	}

	public void messageChannelPart(String nick, String host, String channel, String message) {

		String log;

		log = "<-- " + nick + " has left " + channel;
		if (message != null) {
			log += " (" + message + ")";
		}

		logfile(channel, log);

	}

	public void messageNickChange(String nick, String host, String newname) {

		String log;

		log = "--- " + nick + " changed his name to " + newname;

		logfile(null, log);

	}

	public void messageModeChange(String nick, String host, String channel, String mode, String recipient) {

		String log;

		log = "--- " + nick + " set mode " + mode + " on " + recipient;

		logfile(channel, log);

		int channum = channelNumber(channel);
		int access;
		if (mode.equalsIgnoreCase("-v") || mode.equalsIgnoreCase("-o")) {
			access = IRCServer.ACCESS_NONE;
			server.channels[channum].addUser(recipient, host, access);
		} else if (mode.equalsIgnoreCase("+o")) {
			access = IRCServer.ACCESS_OP;
			server.channels[channum].addUser(recipient, host, access);
		} else if (mode.equalsIgnoreCase("+v")) {
			access = IRCServer.ACCESS_VOICE;
			server.channels[channum].addUser(recipient, host, access);
		}

	}

	public void messageQuit(String nick, String host, String msg) {

		String log;

		log = "<-- " + nick + " has quit ";
		if (msg != null) {
			log += " (" + msg + ")";
		}

		logfile(null, log);

	}

	public void messageChanList(String channel, String list) {

		int channum = channelNumber(channel);

		StringTokenizer tok = new StringTokenizer(list, " ");
//		int usersInWhois = 0;
//		String userhostString = "";
		
		ircio.who(channel);
		
		while (tok.hasMoreElements()) {
			String user = tok.nextToken();
			int access;
			if (user.startsWith("@")) {
				access = IRCServer.ACCESS_OP;
			} else if (user.startsWith("%")) {
				access = IRCServer.ACCESS_HALFOP;
			} else if (user.startsWith("+")) {
				access = IRCServer.ACCESS_VOICE;
			} else {
				access = IRCServer.ACCESS_NONE;
			}
			if (access > 0) {
				user = user.substring(1);
			}
			server.channels[channum].addUser(user, "", access);
			
//			userhostString += " " + user;
//			if (++usersInWhois == 5) {
//				ircio.userhost(userhostString);
//				usersInWhois = 0;
//				userhostString = "";
//			}
		}
//		if (usersInWhois > 0)
//			ircio.userhost(userhostString);

	}

	public void messageUserHosts(String users) {
		StringTokenizer tok = new StringTokenizer(users, " =");

hostFinder:
		while (tok.hasMoreElements()) {
			String name = tok.nextToken();

			//If no more elements, throw WTFException
			if (!tok.hasMoreElements())
				return;

			String host = tok.nextToken();
			host = host.substring(1, host.length());

			//For every channel, find users that fit this username and assign this host to them.
			for (int i = 0; i < server.channels.length; i++) {
				for (IRCUser curr = server.channels[i].users; curr != null; curr = curr.next) {
					if (curr.name.equals(name)) {
						curr.host = host;
						continue hostFinder;
					}
				}
			}
		}
	}

	public void messageWho(String userchannel, String usernick, String username, String host, String realname) {
		for (int i = 0; i < server.channels.length; i++) {
			if (server.channels[i].name.equalsIgnoreCase(userchannel)) {
				for (IRCUser curr = server.channels[i].users; curr != null; curr = curr.next) {
					if (curr.name.equalsIgnoreCase(usernick)) {
						curr.host = username + "@" + host;
						return;
					}
				}
				return;
			}
		}
	}
	
	public void messageReceived(String msg) {

	}

	public int channelNumber(String channel) {
		for (int channum = 0; channum < data.getNumChannels(); channum++) {
			if (channel.equalsIgnoreCase(data.getChannel(channum))) {
				return channum;
			}
		}
		return -1;
	}

	//Bot system log
	public void log(String log) {
		data.log(log);
	}

	//Error log
	public void logerror (String log) {
		log("SYSERR: " + log);
	}

	//Channel log
	public void logfile(String recipient, String msg) {
		try {
			Calendar now = Calendar.getInstance();
			int hour = now.get(Calendar.HOUR_OF_DAY);
			int minute = now.get(Calendar.MINUTE);
			int second = now.get(Calendar.SECOND);
			msg = (hour<10?"0":"") + hour + ":" + (minute<10?"0":"") + minute + "." + (second<10?"0":"") + second + " " + msg;

			for (int i = 0; i < data.getNumChannels(); i++) {
				if (recipient != null && !recipient.equalsIgnoreCase(data.getChannel(i))) {
					continue;
				}
				logOut[i].write(msg, 0, msg.length());
				logOut[i].newLine();
				logOut[i].flush();
			}
		} catch (IOException ioe) {
			logerror("Couldn't log to file!");
		}
	}

}

interface IRCListener {

	public void messageReceived(String msg);
	public void messageModeChange(String nick, String host, String channel, String mode, String recipient);
	public void messageNickChange(String nick, String host, String newname);
	public void messageChannelJoin(String nick, String host, String channel);
	public void messageChannelPart(String nick, String host, String channel, String message);
	public void messagePrivMsg(String nick, String host, String recipient, String msg);
	public void messagePrivEmote(String nick, String host, String recipient, String msg);
	public void messageQuit(String nick, String host, String message);

	public void messageChanList(String channel, String list);
	public void messageUserHosts(String users);
	public void messageWho(String userchannel, String usernick, String username, String host, String realname);

	public void logfile(String recipient, String msg);
	public void log(String msg);
	public void logerror(String msg);

}
