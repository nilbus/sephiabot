/* SephiaBot
 * Copyright (C) 2005 Jorge Rodriguez and Ed Anderson
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 */

import java.util.*;
import java.io.IOException;
import java.net.UnknownHostException;

class SephiaBot implements IRCConnectionListener {

	private SephiaBotData data;
	
	private IRCConnection connections[];

	private long nextWho;
	private long nextHi;
	private long SPAM_WAIT = 1000;

	private long lastNickAttempt = 0;

	//XXX: For every place censor() is used, IRCConnection must set currChannel higher in the stack for it to work correctly.
	private boolean censor(IRCConnection con) { return data.getCensor(con.getIndex(), con.getCurrentChannel()); }

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

		while (sephiaBot.hasConnections()) {
			sephiaBot.poll();
		}
		sephiaBot.log("All connections have been closed. Exiting.");
	}

	public SephiaBot(String config) {

		this.nextWho = 0;
		this.nextHi = 0;

		this.data = new SephiaBotData(config);
		
		log("----------------------------------------------------------------------------\nSephiaBot Started!");
		
		data.parseConfig();

		// this must happen after config parsing.
		this.connections = new IRCConnection[data.getNumNetworks()];
	}

	boolean hasConnections() {
		for (int i = 0; i < connections.length; i++)
			if (connections[i].isConnected())
				return true;
		return false;
	}
	
	String makeTime(long time) {
		long dur = Math.abs(time - System.currentTimeMillis()); 
		String result = "";
		dur /= 1000L;//Seconds
		if (dur < 60)
			result += dur + " second";
		else {
		dur /= 60L; // Minutes
		if (dur < 60)
			result += dur + " minute";
		else {
		dur /= 60L; // Hours
		if (dur < 24)
			result += dur + " hour";
		else {
		dur /= 24L; // Days
		if (dur < 30)		//Precision isn't necessary; use avg month length
			result += dur + " day";
		else {
		dur /= 30L; // Months
		if (dur < 12)
			result += dur + " month";
		else {
		dur /= 12L; // Years
		result += dur + " year";
		}}}}}

		if (dur != 1)
			result += "s";
		return result;
	}

	void connect() {

		String[] channels;
		for (int i = 0; i < data.getNumNetworks(); i++) {
			log("Network: " + data.getNetwork(i) + " " + data.getPort(i) + " : " + data.getName(i));
			
			//Quickly build a channel list.
			channels = new String[data.getNumChannels(i)];
			for (int j = 0; j < channels.length; j++)
				channels[j] = data.getChannel(i, j);

			connections[i] = new IRCConnection(this, i);
			//Try CONNECT_ATTEMPT times, or until connected
			for (int j = 0; !connections[i].isConnected() && j < IRCConnection.CONNECT_ATTEMPTS; j++)
				try {
					connections[i].connect(channels, data.getNetwork(i), data.getPort(i), data.getName(i));
				} catch (UnknownHostException ioe) {
					log("Connection failed: Host not found: " + ioe.getMessage() + ". Giving up.");
					connections[i].disconnect();
					break;
				} catch (IOException ioe) {
					log("Connection attempt " + (j+1) + " failed: " + ioe.getMessage() + ". " +
							(j < IRCConnection.CONNECT_ATTEMPTS-1?"Trying again.":"Giving up."));
					connections[i].disconnect();
				}
		}
		
	}

	void poll() {
		for (int i = 0; i < connections.length; i++) {
			IRCIO io = connections[i].getIRCIO();
			try {
				if (!data.getName(i).equals(io.getName()) &&
						System.currentTimeMillis() > lastNickAttempt + 60000) { //If we didn't get the nick we wanted
					lastNickAttempt = System.currentTimeMillis();
					io.changeNick(data.getName(i));
				}
				io.poll();
			} catch (IOException ioe) {
				logerror("Couldn't poll for input on connection to " + io.getName() + ": " + ioe.getMessage());
				log("Reconnecting.");
				try {
					io.connect();
					io.login();
				} catch (IOException ioe2) {
					logerror("Couldn't reconnect: " + ioe2.getMessage());
					io.disconnect();
					broadcast(io.getName() + " died. :(");
				}
			}
		}
		checkForReminders(connections);
		
		try {
			Thread.sleep(100);
		} catch (InterruptedException ie) {}
	}

	private boolean iregex(String pattern, String string) {
		return SephiaBotData.iregex(pattern, string);
	}
	
	private boolean iequals(String str1, String str2) {
		return SephiaBotData.iequals(str1, str2);
	}

	   
	//Check if this person has any messages, or missed reminders.
	public void checkForMessages(IRCConnection con, String nick, String host, String recipient) {
		User user = data.getUserByHost(host);
		int totalMessages = 0;
		Message messages[] = data.getMessagesByReceiver(nick, user);
		data.removeRecentReminders(nick, user);
		Reminder reminders[] = data.getRemindersByReceiver(nick, user, true);

		if (messages.length > 0 && reminders.length > 0)
			con.getIRCIO().privmsg(recipient, nick + ", you have messages and reminders!");
		else if (messages.length > 0)
			con.getIRCIO().privmsg(recipient, nick + ", you have messages!");
		else if (reminders.length > 0)
			con.getIRCIO().privmsg(recipient, nick + ", you had reminders!");
		else
			return;
		
		for (int i = 0; i < messages.length; i++) {
			Message message = messages[i];
			if (totalMessages >= 5) {
				con.getIRCIO().privmsg(recipient, "You have more messages.");
				return;
			}
			totalMessages++;
			con.getIRCIO().privmsg(recipient, "Message from " + message.sender + " [" + makeTime(message.time) + " ago]:" + message.message);
			data.removeMessage(message);
		}
		
		for (int i = 0; i < reminders.length; i++) {
			Reminder reminder = reminders[i];
			if (!reminder.notified)
				continue;
			if (totalMessages >= 5) {
				con.getIRCIO().privmsg(recipient, "You have more reminders.");
				return;
			}
			totalMessages++;
			String sender = reminder.sender;
			if (iequals(reminder.sender, reminder.target))
				sender = "yourself";
			con.getIRCIO().privmsg(recipient, "Reminder from " + sender + " [" + makeTime(reminder.timeSent) + " ago]: " + reminder.message);
			data.removeReminder(reminder);
		}
	}

	//Announce any reminders that have just happened or that haven't been
	// announced yet.
	//This function is called in the poll loop. It must not take long.
	public void checkForReminders(IRCConnection[] connections) {
		Reminder reminders[] = data.getUnnotifiedReminders();
		if (reminders.length <= 0)
			return;
		for (int i = 0; i < reminders.length; i++) {
			Reminder reminder = reminders[i];
			IRCChannel channel = null;
			//Find this reminder's target's user, if any
			User target = data.getUserByName(reminder.target);
			IRCConnection con = null;

			String sender = reminder.sender;
			if (iequals(reminder.sender, reminder.target))
				sender = "yourself";

			//Search for the server and channel this person last spoke in,
			// or if not known, just find them.
			//Jorge would call this a hack, because it uses short circuiting :p
			if (target != null && target.lastChannel != null) {
				//the easy way
				channel = target.lastChannel;
				con = channel.myServer.myConnection;
			} else {
				//otherwise we must look for this person in every channel
				search:
				for (int k = 0; k < connections.length; k++) {
					con = connections[k];
					IRCChannel[] channels = con.getServer().channels;
					for (int j = 0; j < channels.length; j++)
						if (channels[j].userInChannel(target, reminder.target)) {
							channel = channels[j];
							break search;
						}
				}
			}
			//Don't send the reminder if they're offline.
			if (con != null && channel != null) {
				con.getIRCIO().privmsg(channel.name, reminder.target + ", reminder from " + sender + " [" + makeTime(reminder.timeSent) + " ago]: " + reminder.message);
				reminder.notified = true;
				reminder.timeNotified = System.currentTimeMillis();
				data.writeData();
				data.findNextReminderTime();
			}
		}
	}
	
	public void messagePrivEmote(IRCConnection con, String nick, String host, String recipient, String msg) {
		String log;
		
		log = "* " + nick + " " + msg;

		con.logfile(recipient, log);
		
		msg = msg.trim();

		data.updateUserTimes(nick, host, con.getServer(), recipient);
		checkForMessages(con, nick, host, recipient);
		checkForBlacklist(con, nick, host, recipient);
		
		if (System.currentTimeMillis() > nextWho) { //!spam
			nextWho = System.currentTimeMillis() + SPAM_WAIT;
						
			if (iregex("hugs " + data.getName(con.getIndex()), msg)) {
				if (data.isVino(host))
					con.getIRCIO().privemote(recipient, "hugs Vino!");
				else if (censor(con))
					con.getIRCIO().privemote(recipient, "hugs " + nick + "!");
				else
					con.getIRCIO().privmsg(recipient, "Get the fuck off.");
			} else if (iregex("p[ea]ts " + data.getName(con.getIndex()), msg)) {
				con.getIRCIO().privemote(recipient, "purrs.");
			} else if (iregex("pokes " + data.getName(con.getIndex()), msg)) {
				boolean tickle = new Random().nextBoolean();
				if (tickle == true) {
					con.getIRCIO().privemote(recipient, "laughs.");
				} else {
					con.getIRCIO().privmsg(recipient, "Ouch!"); 
				}
			} else if (iregex("tickles " + data.getName(con.getIndex()), msg)) {
				User user = data.getUserByNick(connections, nick);
				if (user != null) { 
					con.getIRCIO().privemote(recipient, "giggles."); 
				} else {

					con.getIRCIO().privemote(recipient, "slaps " + nick + " across the face.");
				}
			}
			return;
		}
	}

	public void messagePrivMsg(IRCConnection con, String nick, String host, String recipient, String origmsg) {
		boolean pm = false;
		String log;
		String msg = origmsg;

		if (iregex("^"+data.getName(con.getIndex())+"-*$", recipient)) {
			recipient = nick;
			pm = true;
		}

		log = "<" + nick + "> ";
		log += msg.substring(0, msg.length());

		con.logfile(recipient, log);

		msg = msg.trim();

		data.updateUserTimes(nick, host, con.getServer(), recipient);
		checkForMessages(con, nick, host, recipient);
		checkForBlacklist(con, nick, host, recipient);
		con.updateHistory(nick, msg);
		
		String name = data.getName(con.getIndex());

		//Say hello!
		int nameEnd = name.length() < 4 ? name.length() : 4;
		if (iregex(name.substring(0, nameEnd), msg)) {
			if (data.matchHellos(msg)) {
				if (System.currentTimeMillis() > nextHi) {  //!spam
					con.getIRCIO().privmsg(recipient, data.getRandomHelloReply());
					nextHi = System.currentTimeMillis() + 500;
					return;
				}
			}
		}

		if (System.currentTimeMillis() > nextHi) {  //!spam
			if (iregex("^(good |g')?morning?,?( all| (you )?guys| (eve?ry(b(o|ud)dy|(1| ?one)))| " + data.getName(con.getIndex()) + ")?[DpP\\W]*$", msg)) {
				con.getIRCIO().privmsg(recipient, "Good morning :D");
				nextHi = System.currentTimeMillis() + 1000;
				return;
			}
		}
										 

		StringTokenizer tok = new StringTokenizer(msg, ",: ");
		String botname;
		if (!pm && tok.hasMoreElements()) {
			botname = tok.nextToken();
		} else {
			botname = "";
		}

		if (pm || talkingToMe(msg, data.getName(con.getIndex()))) {

			//Remove the bot's name
			if (!pm)
				msg = msg.replaceFirst(botname + "[,: ]+", "");

			if (iregex("bring out the strapon", msg)) {
				con.getIRCIO().privemote(recipient, "steps forward with a large strapon and begins mashing potatoes.");
				return;
			}

			msg = data.removePunctuation(msg, ".?!");

			//BEGIN COLLOQUIAL COMMANDS
			//These commands can be used anywhere if the bot's name is spoken first.
			if (iregex("^who are you\\W*$", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					con.getIRCIO().privmsg(recipient, "I am an advanced SephiaBot channel bot.");
					con.getIRCIO().privmsg(recipient, "I'll kick your " + (censor(con)?"butt":"ass") + " in days that end in 'y'.");
					con.getIRCIO().privmsg(recipient, "I was written by Vino. Vino rocks.");
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if ( iregex("^what does marsellus wallace look like\\W*$", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					con.getIRCIO().privmsg(recipient, "He's black.");
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("^who (wrote|made|programmed|coded|created) you\\W*$", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					con.getIRCIO().privmsg(recipient, "I was written by Vino. Vino rocks.");
					con.getIRCIO().privmsg(recipient, "Nilbus helped too.");
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("^who('s| is) here\\W*$", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam

					int channum = channelNumber(con.getIndex(), recipient);
					if (channum == -1 || pm) {
						con.getIRCIO().privmsg(recipient, "It's just you and me in a PM, buddy.");
						nextWho = System.currentTimeMillis() + SPAM_WAIT;
					} else {
						StringBuffer buf = new StringBuffer("Users in this channel:");
						IRCUser current = con.getServer().channels[channum].users;
						for (IRCUser curr = con.getServer().channels[channum].users; curr != null; curr = curr.next) {
							buf.append(" " + curr.name);
						}
						con.getIRCIO().privmsg(recipient, buf.toString());
						nextWho = System.currentTimeMillis() + SPAM_WAIT;
					}
				}
				return;
			} else if (iregex("^who('s| is)", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String whoisName = msg.substring(msg.lastIndexOf(' ')+1, msg.length());
					if (talkingToMe(whoisName, data.getName(con.getIndex())))
						con.getIRCIO().privmsg(recipient, "I am an advanced SephiaBot channel bot.");
					else {
						User target = data.getUserByName(whoisName);
						if (target == null)
							con.getIRCIO().privmsg(recipient, "Nobody important.");
						else
							con.getIRCIO().privmsg(recipient, target.description);
					}
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("^are you (sexy|h(o|aw)t|beautiful|awesome|cool|swell)", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String compliment = data.iregexFind("(sexy|h(o|aw)t|beautiful|awesome|cool|swell)", msg);
					if (censor(con))
						con.getIRCIO().privmsg(recipient, "I am sooo freaking " + compliment + "!");
					else
						con.getIRCIO().privmsg(recipient, "Fuck yes.");
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (!censor(con) && iregex("^wan(na |t to )cyber", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					User user = data.getUserByNick(connections, nick);
					if (data.isVino(host) || user != null && iequals(user.userName, "Yukie")) {
						con.getIRCIO().privmsg(recipient, "Take me, " + nick + "!");
					} else {
						con.getIRCIO().privmsg(recipient, "Fuck no.");
					}
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("^wh?[aeu]re?('?[sz]| i[sz]| si| be?)( m(a[ih]|y))?", msg)) {
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
								con.getIRCIO().privmsg(recipient, user.userName + " has been " + user.away + " for " + makeTime(user.leaveTime) + ".");
								foundAway = true;
							}
						}
						if (!foundAway)
							con.getIRCIO().privmsg(recipient, "Everyone is present and accounted for.");
						return;
					}
					if (iequals(targetName, botname))
						return;
					User target = data.getUserByNick(connections, targetName);
					if (target == null) {
						con.getIRCIO().privmsg(recipient, "Like I know.");
						return;
					} else
						targetName = target.userName; //use correct caps
					if (target.away == null) {
						if (target.lastTalked > 0)
							con.getIRCIO().privmsg(recipient, "I don't know, the last time they said anything was " + makeTime(target.lastTalked) + " ago.");
						else
							con.getIRCIO().privmsg(recipient, "I don't know.");
					} else {
						con.getIRCIO().privmsg(recipient, targetName + " is " + target.away + ". " + targetName + " has been gone for " + makeTime(target.leaveTime) + ".");
					}
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("^who am i", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					User target = data.getUserByHost(host);
					if (data.isVino(host)) {
						con.getIRCIO().privmsg(recipient, "Daddy!");
						con.getIRCIO().privemote(recipient, "hugs " + nick + ".");
					} else if (target != null) {
						con.getIRCIO().privmsg(recipient, "Someone I know.");
					} else {
						con.getIRCIO().privmsg(recipient, "Nobody important.");
					}
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("^who('| i)?s your daddy", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					con.getIRCIO().privmsg(recipient, "Vino's my daddy, ugh! Spank me again Vino!");
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("^knock knock", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					con.getIRCIO().privmsg(recipient, "Who's there?");
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (!censor(con) && iregex("i suck dick", msg)) {
				if (System.currentTimeMillis() > nextWho) { //!spam
					con.getIRCIO().privmsg(recipient, "Yeah, we know you do.");
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (!censor(con) && iregex("words of wisdom", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String phrase = data.randomPhrase("wordsofwisdom.txt");
					if (phrase != null)
						con.getIRCIO().privmsg(recipient, phrase);
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("roll (the )?dice", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					Random rand = new Random();
					int dice = rand.nextInt(5)+2;
					int sides = rand.nextInt(5)+6;
					con.getIRCIO().privemote(recipient, "rolls " + dice + "d" + sides + " dice and gets " + (dice*sides+1) + ".");
					if (!data.isVino(host)) {
						con.getIRCIO().privemote(recipient, "kills " + nick + ".");
					} else {
						con.getIRCIO().privemote(recipient, "hugs " + nick + ".");
					}
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("do a little dance", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					con.getIRCIO().privemote(recipient, "makes a little love.");
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
			} else if (iregex("excuse", msg)) {
				if (System.currentTimeMillis() > nextWho) {	//!spam
					String excuse = data.randomPhrase("excuses.txt");
					if (excuse != null)
						con.getIRCIO().privmsg(recipient, "Your excuse is: " + excuse);
					nextWho = System.currentTimeMillis() + SPAM_WAIT;
				}
				return;
				
			//BEGIN COMMAND SECTION.
			//These are one-word commands only. The command is the first thing you say after the bot name. botname, command arguments.
			} else if (tok.hasMoreElements()) {
				String cmd = tok.nextToken(" ");
				if (tok.hasMoreElements() && (cmd.startsWith(",") || cmd.startsWith(":"))) { 
					cmd = tok.nextToken(" ");
				}
				if (iequals("kill", cmd)) {
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "KILL! KILL! KILL!");
						return;
					}
					String killed = tok.nextToken(" ");
					User killerUser = data.getUserByHost(host);
					User killedUser = data.getUserByNick(connections, killed);
					if ((killerUser == null || (killedUser != null && killedUser.memberType > killerUser.memberType)) && !data.isVino(host)) {
						con.getIRCIO().privemote(recipient, "giggles at " + nick);
					} else if (iequals(killed, botname)) {
						con.getIRCIO().privmsg(recipient, ":(");
					} else {
						int killedAccess = con.getAccess(killed, channelNumber(con.getIndex(), recipient));
						if (killedAccess != -1) {
							con.getIRCIO().privmsg(recipient, "It would be my pleasure.");
							con.getIRCIO().kick(recipient, killed, "This kick was compliments of " + killerUser.userName + ". Have a nice day.");
						} else if (iequals("yourself", killed))	{ //reboot
							if (data.isAdmin(host)) {
								shutdown("*gags and passes out.", true);
							} else {
								con.getIRCIO().privmsg(recipient, "No.");
							}
						} else if (killed.equalsIgnoreCase("message")) {
							if (!tok.hasMoreElements()) {
								con.getIRCIO().privmsg(recipient, "Which message? I need a number");
								return;
							}
							try {
								int msgIndex = Integer.parseInt(tok.nextToken()) - 1;
								User user = data.getUserByHost(host);
								Message messages[] = data.getMessagesBySender(nick, user);
								if (msgIndex >= messages.length || msgIndex < 0) {
									con.getIRCIO().privmsg(recipient, "You don't have that many messages.");
									return;
								}
								con.getIRCIO().privmsg(recipient, "Message removed from " + messages[msgIndex].sender + " to " + messages[msgIndex].target + " " + makeTime(messages[msgIndex].time) + " ago:" + messages[msgIndex].message);
								data.removeMessage(messages[msgIndex]);
							} catch (NumberFormatException nfe) {
								con.getIRCIO().privmsg(recipient, "...if you can call that a number.");
							}
							return;
						} else if (killed.equalsIgnoreCase("reminder")) {
							if (!tok.hasMoreElements()) {
								con.getIRCIO().privmsg(recipient, "Which reminder? I need a number");
								return;
							}
							try {
								int msgIndex = Integer.parseInt(tok.nextToken()) - 1;
								User user = data.getUserByHost(host);
								Reminder reminders[] = data.getRemindersBySender(nick, user);
								if (msgIndex >= reminders.length || msgIndex < 0) {
									con.getIRCIO().privmsg(recipient, "You don't have that many reminders.");
									return;
								}
								Reminder reminder = reminders[msgIndex];
								String timeToArrive;
								if (System.currentTimeMillis() > reminder.timeToArrive)
									timeToArrive = makeTime(reminder.timeToArrive) + " ago";
								else
									timeToArrive = makeTime(reminder.timeToArrive) + " from now";
								con.getIRCIO().privmsg(recipient, "Reminder removed from " + reminder.sender + " to " + reminder.target + " " + makeTime(reminder.timeSent) + " ago for " + timeToArrive + ": " + reminder.message);
								data.removeReminder(reminder);
							} catch (NumberFormatException nfe) {
								con.getIRCIO().privmsg(recipient, "...if you can call that a number.");
							}
							return;
						} else
							con.getIRCIO().privmsg(recipient, "Kill who what now?"); 
						return;
					}
					return;
				} else if (iequals("tell", cmd)) {
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "Tell who what?");
						return;
					}

					String target = tok.nextToken(" ");
					String sender = nick;
					target = data.removePunctuation(target, ".!,");
					//If the target is logged in, send the message to his username instead so he will always get it if he is logged in.
					User targetUser = data.getUserByNick(connections, target);
					if (targetUser != null)
						target = targetUser.userName;
					//If the sending user is logged in, send the message as his username instead so that all the messages are sent by the same user.
					User senderUser = data.getUserByHost(host);
					if (senderUser != null)
						sender = senderUser.userName;

					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "Tell " + target + " what?");
						return;
					}
					String message = tok.nextToken("");
					//If the target was "everybody" or "everyone" then send the message to every user.
					if (data.getUserByHost(host) != null && (iequals("everybody", target) || iequals("everyone", target)))
						data.sendMessageToAllUsers(message, sender);
					else
						data.addMessage(target, message, sender);
					data.writeData();
					con.getIRCIO().privmsg(recipient, "OK, I'll make sure to let them know.");
					return;
				} else if (iequals("remind", cmd)) {
					// Bit, remind [person] ([at time] || [on day]) OR ([in duration]) [to OR that] [something]
					// Bit, remind [person] [to OR that] [something] ([at time] || [on day]) OR ([in duration])
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "Remind who what when?");
						return;
					}
					String target = tok.nextToken(" ");
					boolean myself = false;
					String sender = nick;
					target = data.removePunctuation(target, ".!,:");
					if (iregex("(me|myself)", target)) {
						myself = true;
						target = nick;
					}
					//If the target is logged in, send the reminder to his username instead so he will always get it if he is logged in.
					User targetUser = data.getUserByNick(connections, target);
					//if that didn't work, try by name
					if (targetUser == null)
						targetUser = data.getUserByName(target);
					//did we find a user?
					if (targetUser != null)
						target = targetUser.userName;
					//If the sending user is logged in, send the reminders as his username instead so that all the reminders are sent by the same user.
					User senderUser = data.getUserByHost(host);
					if (senderUser != null)
						sender = senderUser.userName;
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "Remind " + target + " when what?");
						return;
					}
					try {
						Reminder reminder = data.addReminder(target, tok.nextToken("").substring(1), sender);

						con.getIRCIO().privmsg(recipient, "OK, I'll remind " + 
							(myself?"you":target) +" "+ reminder.getTimeExpression());
					} catch (WTFException wtf) {
						con.getIRCIO().privmsg(recipient, wtf.getMessage().substring(14));
					} catch (NumberFormatException nfe) {
						con.getIRCIO().privmsg(recipient, nfe.getMessage());
					}
					return;
				} else if (iregex("^(butt?)?se(x|ck[sz])$", cmd)) {
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privemote(recipient, "anally rapes " + nick + ".");
						return;
					}
					String sexed = tok.nextToken(" ");
					if (iregex("^vino", sexed)) {
						con.getIRCIO().privemote(recipient, "screams as Vino penetrates every orifice of her body!");
					} else if (iequals(botname, sexed)) {
						con.getIRCIO().privemote(recipient, "furiously works the potato masher!");
					} else {
						int sexedAccess = con.getAccess(sexed, channelNumber(con.getIndex(), recipient));
						if (sexedAccess == -1) {
							sexed = nick;
						}
						con.getIRCIO().privemote(recipient, "anally rapes " + sexed + ".");
					}
					return;
				} else if (iregex("^re(boot|start)$", cmd)) {
					if (data.isAdmin(host)) {
						shutdown("Be right back.", true);
					} else
						con.getIRCIO().privmsg(recipient, "No.");
					return;
				} else if (iregex("^(shutdown|die|leave)$", cmd)) {
					if (data.isAdmin(host)) {
						shutdown("Goodbye. :(", false);
					} else {
						con.getIRCIO().privmsg(recipient, "No.");
					}
					return;
				} else if (iequals("reload", cmd)) {
					if (data.isAdmin(host)) {
						data.parseConfig();
						con.getIRCIO().privmsg(recipient, "Done.");
					} else {
						con.getIRCIO().privmsg(recipient, "No.");
					}
					return;
				} else if (iequals("save", cmd)) {
					if (data.isAdmin(host)) {
						data.writeData();
						con.getIRCIO().privmsg(recipient, "Done.");
					} else {
						con.getIRCIO().privmsg(recipient, "No.");
					}
					return;
				} else if (iequals("listhosts", cmd)) {
					User user = data.getUserByHost(host);
					if (user == null)
						return;
					String buffer = "Hosts logged in as " + user.userName + ":";
					for (int i = 0; i < 10; i++)
						if (user.hosts[i] != null && user.hosts[i].length() > 0)
							buffer += " " + (i+1) + ": " + user.hosts[i];
					con.getIRCIO().privmsg(nick, buffer);
					return;
				} else if (iequals("logout", cmd)) {
					User user = data.getUserByHost(host);
					if (user == null)
						return;
					if (!tok.hasMoreElements()) {
						for (int i = 0; i < 10; i++) {
							if (user.hosts[i] != null && user.hosts[i].equals(host)) {
								data.logout(user, i);
								con.getIRCIO().privmsg(nick, "It's too bad things couldn't work out.");
								return;
							}
						}
						con.getIRCIO().privmsg(nick, "Your host is not logged in.");
						return;
					}
					try {
						int i = Integer.parseInt(tok.nextToken("").trim())-1;
						if (i < 0 || i >= 10)
							return;
						if (user.hosts[i] == null || user.hosts[i].length() <= 0)
							con.getIRCIO().privmsg(nick, "That host is not logged in.");
						else {
							con.getIRCIO().privmsg(nick, "It's too bad things couldn't work out.");
							data.logout(user, i);
							data.logout(user, i);
							user.hosts[i] = null;
						}
					} catch (NumberFormatException nfe) {
					}
					return;
				} else if (iequals("login", cmd)) {
					if (tok.countTokens() < 2) {
						con.getIRCIO().privmsg(nick, "Yeah. Sure. Whatever.");
						return;
					}
					String login = tok.nextToken(" ").trim();
					String passwd = tok.nextToken("").trim();
					if (data.validateLogin(login, passwd)) {
						int i, userID = -1;
						User user = data.getUserByName(login);
						if (user == null) {	//WTFException
							con.getIRCIO().privmsg(nick, "WTF? Tell Vino you saw this.");
							return;
						}
						// If getUserByHost returns non-null then the user is logged into this user already,
						// or is logged in as another user and may not re-log in.
						User ghost = data.getUserByHost(host);
						if (ghost != null) {
							con.getIRCIO().privmsg(nick, "Silly you. You're already logged in as " + ghost.userName);
							return;
						}
						data.loginUser(user, host);
						if (data.isVino(user))
							con.getIRCIO().privmsg(nick, "Hi, daddy! :D");
						else
							con.getIRCIO().privmsg(nick, "What's up " + user.userName + "?");
						data.writeData();
					} else {
						con.getIRCIO().privmsg(nick, "No cigar.");
						log("Failed login attempt by " + nick + "!" + host + " with " + login + "/" + passwd + ".");
					}
					return;
				} else if (iregex("^i('|\"| a)?m$", cmd)) {
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "You're what?");
						return;
					}
					User user = data.getUserByHost(host);
					if (user == null) {
						con.getIRCIO().privmsg(recipient, "I don't care.");
						return;
					}
					String location = tok.nextToken("").trim();
					if (iregex("^ba+c?k", location)) {
						if (user.away == null) {
							con.getIRCIO().privmsg(recipient, "Of course you are honey.");
						} else {
							con.getIRCIO().privmsg(recipient, "Welcome back! You've been away for " + makeTime(user.leaveTime) + ".");
							user.away = null;
						}
					} else {
						con.getIRCIO().privmsg(recipient, "Have fun!");
						//Remove punctuation from the end
						location = data.removePunctuation(location, ".!,?");
						user.away = location.replaceAll("\"", "'");
						user.leaveTime = System.currentTimeMillis();
					}
					data.writeData();
					return;
				} else if (iequals("messages", cmd)) {
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
						con.getIRCIO().privmsg(recipient, "You havent sent any messages.");
						return;
					}
					if (firstIndex >= messages.length) {
						con.getIRCIO().privmsg(recipient, "You don't have that many messages.");
						return;
					}
					lastIndex = firstIndex + 5;
					if (lastIndex >= messages.length)
						lastIndex = messages.length-1;
					con.getIRCIO().privmsg(nick, "You have sent the following messages:");
					for (int i = firstIndex; i <= lastIndex; i++) {
						con.getIRCIO().privmsg(nick, "Message " + (i+1) + ": To " + messages[i].target + " " + makeTime(messages[i].time) + " ago:" + messages[i].message);
					}
					return;
				} else if (iequals("reminders", cmd)) {
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
						con.getIRCIO().privmsg(recipient, "You haven't sent any reminders.");
						return;
					}
					if (firstIndex >= reminders.length) {
						con.getIRCIO().privmsg(recipient, "You don't have that many reminders.");
						return;
					}
					lastIndex = firstIndex + 5;
					if (lastIndex >= reminders.length)
						lastIndex = reminders.length-1;
					con.getIRCIO().privmsg(nick, "You have sent the following reminders:");
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
						con.getIRCIO().privmsg(nick, "Reminder " + (i+1) + ": For " + target + ", sent " + makeTime(reminder.timeSent) + " ago for " + timeToArrive + ": " + reminder.message);
					}
					return;
				} else if (iregex("^(say|do|emote)$", cmd)) {
					if (!data.isAdmin(host)) {
						con.getIRCIO().privmsg(recipient, "No.");
						return;
					}
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "Say what?!?");
						return;
					}
					String firstWord = tok.nextToken();
					String everythingElse = "";
					String targetChannel = recipient;
					if (iequals("in", firstWord) && tok.hasMoreTokens()) {
						targetChannel = tok.nextToken();
						if (tok.hasMoreElements()) {
							firstWord = tok.nextToken();
						} else {
							con.getIRCIO().privmsg(targetChannel, "Say what?!?");
							return;
						}
					}
					if (tok.hasMoreTokens())
						everythingElse = tok.nextToken("");
					
					//Check recipient
					IRCChannel channel = null;
					IRCConnection sayCon = con;
					channel = sayCon.getServer().findChannel(targetChannel); //first try the same server
					if (channel == null)
						for (int i = 0; i < connections.length; i++) { //search other servers
							if (connections[i] != sayCon) { //the current one has already been searched
								channel = connections[i].getServer().findChannel(targetChannel);
								if (channel != null) {
									sayCon = connections[i];
									break;
								}
							}
						}
					if (channel == null) {
						con.getIRCIO().privmsg(recipient, "I'm not in that channel.");
						return;
					}
					
					if (iequals("say", cmd))
						sayCon.getIRCIO().privmsg(channel.name, firstWord + everythingElse);
					else
						sayCon.getIRCIO().privemote(channel.name, firstWord + everythingElse);

					if (!targetChannel.equals(recipient))
						con.getIRCIO().privmsg(recipient, "okay");

					return;
				//TODO: Make mode setting colloquial
				} else if (iequals("mode", cmd)) {
					if (!data.isAdmin(host)) {
						con.getIRCIO().privmsg(recipient, "No.");
						return;
					}
					String inchannel = recipient;
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "What channel?");
						return;
					}
					inchannel = tok.nextToken();
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "Who?");
						return;
					}
					String who = tok.nextToken();
					String mode;
					if (!tok.hasMoreElements()) {
						con.getIRCIO().privmsg(recipient, "What mode?");
						return;
					} else mode = tok.nextToken();
					System.out.println("MODE " + inchannel + " " + mode + " " + who + "\n");
					con.getIRCIO().setMode(who, inchannel, mode);
					return;
				} else if (iregex("^(last|seen)$", cmd)) {
					if (!tok.hasMoreTokens()) {
						con.getIRCIO().privmsg(recipient, "When did I last see who?");
						return;
					}
					nick = tok.nextToken();
					User target = data.getUserByNick(connections, nick);
					if (target == null)
						con.getIRCIO().privmsg(recipient, "I wasn't really paying attention to " + nick + ".");
					else
						con.getIRCIO().privmsg(recipient, "Last time I saw " + target.userName + " was " + makeTime(target.lastTalked) + " ago.");
					
					return;
				}

			}	
			//Everything above here should return if it does something.
	
			//Bot has been mentioned?
			if (pm || talkingToMe(origmsg, data.getName(con.getIndex())) || iregex(name, msg)) {
				if (!censor(con)) {
					if (iregex("fuck you", msg)) {
						if (System.currentTimeMillis() > nextWho) {	//!spam
							con.getIRCIO().privmsg(recipient, "Fuck you too, buddy.");
							nextWho = System.currentTimeMillis() + SPAM_WAIT;
							return;
						}
					} else if (iregex("screw you", msg)) {
						if (System.currentTimeMillis() > nextWho) {	//!spam
							con.getIRCIO().privmsg(recipient, "Screw you too, buddy.");
							nextWho = System.currentTimeMillis() + SPAM_WAIT;
							return;
						}
					} else if (iregex("you suck", msg)) {
						if (System.currentTimeMillis() > nextWho) {	//!spam
							con.getIRCIO().privmsg(recipient, "I suck, but you swallow, bitch.");
							nextWho = System.currentTimeMillis() + SPAM_WAIT;
							return;
						}
					}
				}
				if (iregex("(thank( ?(yo)?u|[sz])\\b|\\bt(y|hn?x)\\b)", msg)) {
					if (System.currentTimeMillis() > nextWho) { //!spam
						con.getIRCIO().privmsg(recipient, "No problem.");
						nextWho = System.currentTimeMillis() + SPAM_WAIT;
						return;
					}
				} else if (iregex("bounc[ye]", msg)) {
					if (System.currentTimeMillis() > nextWho) { //!spam
						con.getIRCIO().privmsg(recipient, "Bouncy, bouncy, bouncy!");
						nextWho = System.currentTimeMillis() + SPAM_WAIT;
						return;
					}
				} else if (iregex("(right|correct)", msg)) {
					if (System.currentTimeMillis() > nextWho) { //!spam
						con.getIRCIO().privmsg(recipient, "Absolutely.");
						nextWho = System.currentTimeMillis() + SPAM_WAIT;
						return;
					}
				} else if (iregex("(ice[ -]*cream|custard|gb|good[ -]*berr?y[s']*|(today('s)? )?flavor( of? the? day)?|fotd)", msg)) {
					con.getIRCIO().privmsg(recipient, data.goodberrysFlavorOfTheDay());
				}
			}
		//Wasn't talking to the bot
		} else if (con.parrotOK()) {
			con.setLastRepeat(con.getHistory(0));
			con.getIRCIO().privmsg(recipient, con.getHistory(0));
		}
	}
	public boolean talkingToMe(String msg, String name) {
		int nameEnd = name.length() < 4 ? name.length() : 4;
		return iregex("^"+name.substring(0, nameEnd), msg);
	}

	public void checkForBlacklist(IRCConnection con, String nick, String host, String channelName) {
		//Check for blacklisted nicks.
		if (data.checkForBlacklist(nick)) {
			con.getIRCIO().ban(channelName, nick, host);
			con.getIRCIO().kick(channelName, nick, "You have been blacklisted. Please never return to this channel.");
			return;
		}
	}
		
	public void messageChannelJoin(IRCConnection con, String nick, String host, String channelName) {

		String log;

		log = "--> " + nick + " has joined " + channelName;

		con.logfile(channelName, log);

		//Say something as you enter the channel!
		nick = nick.replaceFirst("-+$", "");
		if (iequals(nick, data.getName(con.getIndex()))) {
			con.getIRCIO().privmsg(channelName, data.getGreeting(con.getIndex(), con.getCurrentChannel()));
		}

		if (iequals("metapod\\", nick)) {
			con.getIRCIO().privmsg(channelName, "Heya meta.");
		}

		if (iequals("luckyremy", nick)) {
			con.getIRCIO().privemote(channelName, "salutes as Remy enters.");
		}

		//Set lastChannel
		User user = data.getUserByHost(host);
		IRCChannel ircchan = con.getServer().findChannel(channelName);
		if (user != null && ircchan != null)
			user.lastChannel = ircchan;

		//blacklist
		checkForBlacklist(con, nick, host, channelName);

		//Add user to channel's user list
		if (ircchan != null) {
			ircchan.addUser(nick, host, IRCServer.ACCESS_NONE);
		} else {
			logerror("couldn't find channel name in SephiaBot.messageChannelJoin");
			return;
		}
	}

	public void messageChannelPart(IRCConnection con, String nick, String host, String channelName, String message, boolean kicked) {

		String log;

		String how = (kicked?"left ":"been kicked from ");
		log = "<-- " + nick + " has " + how + channelName;
		if (message != null) {
			log += " (" + message + ")";
		}
		con.logfile(channelName, log);

		//unset lastChannel if leaving
		User user = data.getUserByHost(host);
		IRCChannel ircChan = con.getServer().findChannel(channelName);
		if (user != null && ircChan == user.lastChannel)
			user.lastChannel = null;

		checkForBlacklist(con, nick, host, channelName);

		//remove user from channel's user list
		IRCChannel ircchan = con.getServer().findChannel(channelName);
		if (ircchan != null) {
			ircchan.deleteUser(nick);
		} else {
			logerror("couldn't find channel name in SephiaBot.messageChannelPart");
			return;
		}
	}

	public void messageQuit(IRCConnection con, String nick, String host, String msg) {
		String log;
		log = "<-- " + nick + " has quit ";
		if (msg != null) {
			log += " (" + msg + ")";
		}
		con.logfile(null, log);

		//unset lastChannel
		User user = data.getUserByHost(host);
		if (user != null)
			user.lastChannel = null;

		//remove user from all channels
		IRCChannel[] channels = con.getServer().channels;
		for (int i = 0; i < channels.length; i++)
			channels[i].deleteUser(nick);
	}

	public void messageNickChange(IRCConnection con, String nick, String host, String newname) {
		String log;
		log = "--- " + nick + " changed his name to " + newname;
		con.logfile(null, log);

		//Is this my nick?
		if (nick.equals(con.getIRCIO().getName()))
			con.getIRCIO().setName(newname);
		
		//update user's nick in all channels
		IRCChannel[] channels = con.getServer().channels;
		for (int i = 0; i < channels.length; i++)
			channels[i].updateUser(nick, newname, null, IRCServer.ACCESS_UNKNOWN);
	}

	public void messageModeChange(IRCConnection con, String nick, String host, String channelName, String mode, String recipient) {

		String log;

		log = "--- " + nick + " set mode " + mode + " on " + recipient;

		con.logfile(channelName, log);

		int access = IRCServer.ACCESS_UNKNOWN;
		if (mode.equalsIgnoreCase("-v") || mode.equalsIgnoreCase("-o"))
			access = IRCServer.ACCESS_NONE;
		else if (mode.equalsIgnoreCase("+o"))
			access = IRCServer.ACCESS_OP;
		else if (mode.equalsIgnoreCase("+v"))
			access = IRCServer.ACCESS_VOICE;

		//update user's nick in the channel's user list
		IRCChannel ircchan = con.getServer().findChannel(channelName);
		if (ircchan != null) {
			ircchan.updateUser(nick, null, null, access);
		} else {
			logerror("couldn't find channel name in SephiaBot.messageModeChange");
			return;
		}
	}

	public void messageChanList(IRCConnection con, String channelName, String list) {

		int channum = channelNumber(con.getIndex(), channelName);

		StringTokenizer tok = new StringTokenizer(list, " ");
//		int usersInWhois = 0;
//		String userhostString = "";
		
		con.getIRCIO().who(channelName);
		
		while (tok.hasMoreElements()) {
			String user = tok.nextToken();
			int access = IRCServer.ACCESS_UNKNOWN;
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
			con.getServer().channels[channum].addUser(user, "", access);
			
		}

	}

	public void messageUserHosts(IRCConnection con, String users) {
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
			for (int i = 0; i < con.getServer().channels.length; i++) {
				for (IRCUser curr = con.getServer().channels[i].users; curr != null; curr = curr.next) {
					if (curr.name.equals(name)) {
						curr.host = host;
						continue hostFinder;
					}
				}
			}
		}
	}

	public void messageWho(IRCConnection con, String userchannel, String usernick, String username, String host, String realname) {
		for (int i = 0; i < con.getServer().channels.length; i++) {
			if (con.getServer().channels[i].name.equalsIgnoreCase(userchannel)) {
				for (IRCUser curr = con.getServer().channels[i].users; curr != null; curr = curr.next) {
					if (curr.name.equalsIgnoreCase(usernick)) {
						curr.host = username + "@" + host;
						return;
					}
				}
				return;
			}
		}
	}
	
	public void messageReceived(IRCConnection con, String msg) {

	}

	public int channelNumber(int serverID, String channelName) {
		for (int channum = 0; channum < data.getNumChannels(serverID); channum++) {
			if (channelName.equalsIgnoreCase(data.getChannel(serverID, channum))) {
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

	public String getLogdir() {
		return data.getLogdir();
	}

	public void broadcast(String message) {
		for (int i = 0; i < connections.length; i++) {
			IRCConnection con = connections[i];
			for (int j = 0; j < con.getServer().channels.length; j++) {
				IRCChannel chan = con.getServer().channels[j];
				if (message.startsWith("*"))
					connections[i].getIRCIO().privemote(chan.name, message.substring(1));
				else
					connections[i].getIRCIO().privmsg(chan.name, message);
			}
		}
	}
	
	public void shutdown(String message, boolean reboot) {
		broadcast(message);
		data.writeData();
		System.exit(reboot?1:0);
	}
}
