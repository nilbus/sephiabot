class IRCServer {

	String network;
	int port;
	IRCChannel channels[];
	IRCUser users[];
	IRCConnection myConnection = null;

	public static int ACCESS_NONE = 0;
	public static int ACCESS_VOICE = 1;
	public static int ACCESS_HALFOP = 2;
	public static int ACCESS_OP = 3;

	IRCServer(String network, int port, String[] channels, IRCConnection con) {
	this.myConnection = con;
		this.network = network;
		this.port = port;
		this.channels = new IRCChannel[channels.length];
		// It is important to maintain the order of the channels here, because the positions of
		// the channels are also their index when calling into SephiaBotData (if that made any
		// sense.)
		for (int i = 0; i < channels.length; i++) {
			this.channels[i] = new IRCChannel(channels[i]);
		this.channels[i].myServer = this;
		}
	}

	int getChannelIndex(String channel) {
		for (int i = 0; i < channels.length; i++)
			if (channels[i].name.equals(channel))
				return i;
		return -1;
	}

	IRCChannel findChannel(String channel) {
		for (int i = 0; i < channels.length; i++)
			if (SephiaBotData.iequals(channels[i].name, channel))
				return channels[i];
		return null;
	}
}

class IRCChannel {

	String name;
	IRCUser users;
	int numusers = 0;
	IRCServer myServer;

	IRCChannel(String name) {
		this.name = name;
	}

	void addUser(String user, String host, int access) {

		if (users == null) {
			users = new IRCUser(user, host, access);
			numusers = 1;
			return;
		}

		IRCUser curr = users;
		while (true) {
			if (curr.name.equals(user)) {
				curr.access = access;
				return;
			}
			if (curr.next == null) {
				curr.next = new IRCUser(user, host, access);
				numusers++;
				return;
			}
			curr = curr.next;
		}
	}

	void remUser(String user) {

		if (users == null) {
			numusers = 0;
			return;
		}

		if (users.next == null) {
			users = null;
			numusers = 0;
			return;
		}

		IRCUser curr = users;
		IRCUser last = users;
		while (true) {
			if (curr.name.equals(user)) {
				last.next = curr.next;
				numusers--;
				return;
			}
			if (curr.next == null) {
				return;
			}
			last = curr;
			curr = curr.next;
		}
	}

	//Check if the nick or user or its aliases are in this channel
	boolean userInChannel(User user, String nick) {
		nick = nick.trim();
		for (IRCUser ircUser = users; ircUser != null; ircUser = ircUser.next){
			if (SephiaBotData.iequals(ircUser.name, nick))
				return true;
			if (user != null)
				for (int j = 0; j < user.aliases.length; j++)
					if (SephiaBotData.iequals(user.aliases[j], nick))
						return true;
		}
		return false;
	}
}

class IRCUser {

	String name;
	String host;
	int access;

	IRCUser next;

	IRCUser(String name, String host, int access) {
		this.name = name;
		this.host = host;
		this.access = access;
	}
}
