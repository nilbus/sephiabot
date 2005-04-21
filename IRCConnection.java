import java.io.*;
import java.util.*;

class IRCConnection implements IRCListener {

	private int historySize;
	private String historyNick[];
	private String historyText[];
	private String lastRepeat;

	private IRCIO ircio;
	private BufferedWriter logOut[];
	private IRCServer server;

	private long nextWho;
	private long nextHi;

	private IRCConnectionListener listener;

	private int index;
	private int currChannel;

	public IRCConnection(IRCConnectionListener listener, int index) {
		this.listener = listener;

		this.historySize = 3;
		this.historyNick = new String[this.historySize];
		this.historyText = new String[this.historySize];

		this.nextWho = 0;
		this.nextHi = 0;
		this.index = index;
		this.currChannel = -1;
	}

	public int getIndex() {
		return index;
	}

	public int getCurrentChannel() {
		return currChannel;
	}
	
	public IRCIO getIRCIO() {
		return ircio;
	}

	public IRCServer getServer() {
		return server;
	}
	
	void initLogs(String channels[]) {
		try {
			logOut = new BufferedWriter[channels.length];
			for (int i = 0; i < channels.length; i++) {
				logOut[i] = new BufferedWriter(new FileWriter(listener.getLogdir() +
						"/log-" + channels[i] + ".txt", true));
			}
		} catch (IOException ioe) {
			logerror("Couldn't open log file.");
		}
	}

	void connect(String channels[], String network, int port, String name) {

		initLogs(channels);

		ircio = new IRCIO(this, network, port);
		ircio.login(channels, name);
		server = new IRCServer(network, port, channels, this);

	}

	void poll() {
		ircio.poll();
	}

	public void updateHistory (String nick, String msg) {
		for (int i = this.historySize - 1; i > 0; i--) {
			historyNick[i] = historyNick[i-1];
			historyText[i] = historyText[i-1];
		}
		historyNick[0] = nick;
		historyText[0] = msg;
	}

	public int getAccess(String user, int channum) {
		if (channum == -1) {
//			log ("chan -1");                      //XXX: debug
			return -1;
		}
		IRCUser current = server.channels[channum].users;
		for (int i = 0; i < server.channels[channum].numusers; i++) {
			if (SephiaBotData.iequals(user, current.name)) {
//				log (current.name + " access " + current.access); //XXX: debug
				return current.access;
			}
			current = current.next;
		}
//		log(user + " access -1");  //XXX: debug
		return -1;
	}

	public void logfile(String recipient, String msg) {
		if (server == null)
			return;
		try {
			Calendar now = Calendar.getInstance();
			int hour = now.get(Calendar.HOUR_OF_DAY);
			int minute = now.get(Calendar.MINUTE);
			int second = now.get(Calendar.SECOND);
			msg = (hour<10?"0":"") + hour + ":" + (minute<10?"0":"") + minute + "." + (second<10?"0":"") + second + " " + msg;

			for (int i = 0; i < server.channels.length; i++) {
				if (recipient != null && !recipient.equalsIgnoreCase(server.channels[i].name)) {
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

	public void log(String msg) {
		listener.log(msg);
	}
	
	public void logerror(String msg) {
		listener.logerror(msg);
	}
	
	public void messageReceived(String msg) {
		listener.messageReceived(this, msg);
	}
	
	public void messageModeChange(String nick, String host, String channel, String mode, String recipient) {
		listener.messageModeChange(this, nick, host, channel, mode, recipient);
	}
	
	public void messageNickChange(String nick, String host, String newname) {
		listener.messageNickChange(this, nick, host, newname);
	}
	
	public void messageChannelJoin(String nick, String host, String channel) {
		currChannel = server.getChannelIndex(channel);
		listener.messageChannelJoin(this, nick, host, channel);
		currChannel = -1;
	}
	
	public void messageChannelPart(String nick, String host, String channel, String message) {
		listener.messageChannelPart(this, nick, host, channel, message);
	}

	public void messagePrivMsg(String nick, String host, String recipient, String msg) {
		currChannel = server.getChannelIndex(recipient);
		listener.messagePrivMsg(this, nick, host, recipient, msg);
		currChannel = -1;
	}

	public void messagePrivEmote(String nick, String host, String recipient, String msg) {
		currChannel = server.getChannelIndex(recipient);
		listener.messagePrivEmote(this, nick, host, recipient, msg);
		currChannel = -1;
	}

	public void messageQuit(String nick, String host, String message) {
		listener.messageQuit(this, nick, host, message);
	}

	public void messageChanList(String channel, String list) {
		listener.messageChanList(this, channel, list);
	}

	public void messageUserHosts(String users) {
		listener.messageUserHosts(this, users);
	}

	public void messageWho(String userchannel, String usernick, String username, String host, String realname) {
		listener.messageWho(this, userchannel, usernick, username, host, realname);
	}

	public boolean parrotOK() {
		//Act like a parrot only if the message isn't a command
		//Only repeat when 2 people said the same thing, but not the 3rd time.
		if (SephiaBotData.iequals(historyText[0], historyText[1])
			&& !SephiaBotData.iequals(historyText[0], this.lastRepeat)
			&& !SephiaBotData.iequals(historyNick[0], historyNick[1])
			&& !historyText[0].trim().startsWith("!"))
			return true;
		else
			return false;
	}

	public String getHistory(int i) {
		return historyText[i];
	}

	public void setLastRepeat(String lastRepeat) {
		this.lastRepeat = lastRepeat;
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
