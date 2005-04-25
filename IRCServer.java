class IRCServer {

	String network;
	int port;
	IRCChannel channels[];
	IRCConnection myConnection = null;

	public static int ACCESS_UNKNOWN = -1;
	public static int ACCESS_NONE = 0;
	public static int ACCESS_VOICE = 1;
	public static int ACCESS_HALFOP = 2;
	public static int ACCESS_OP = 3;

	IRCServer(String network, int port, String[] channels, IRCConnection con) {
	this.myConnection = con;
		this.network = network;
		this.port = port;
		this.channels = new IRCChannel[channels.length];
		// It is important to maintain the order of the channels here,
		// because the positions of the channels are also their index when
		// calling into SephiaBotData (if that made any sense.)
		for (int i = 0; i < channels.length; i++) {
			this.channels[i] = new IRCChannel(channels[i]);
		this.channels[i].myServer = this;
		}
	}

	int getChannelIndex(String channel) {
		for (int i = 0; i < channels.length; i++)
			if (SephiaBotData.iequals(channels[i].name, channel))
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
	IRCServer myServer;

	IRCChannel(String name) {
		this.name = name;
	}

	void addUser(String user, String host, int access) {

		if (users == null) {
			users = new IRCUser(user, host, access);
			return;
		}

		IRCUser curr = users;
		while (true) {
			if (SephiaBotData.iequals(curr.name, user)) {
				curr.access = access;
				return;
			}
			if (curr.next == null) {
				curr.next = new IRCUser(user, host, access);
				return;
			}
			curr = curr.next;
		}
	}

	//do nothing if user was not found
	void deleteUser(String user) {

		if (users == null) {
			return;
		}

		if (users.next == null) {
			if (SephiaBotData.iequals(users.name, user)) {
				users = null;
			}
			return;
		}

		IRCUser curr = users;
		IRCUser last = null;
		while (curr != null) {
			if (SephiaBotData.iequals(curr.name, user)) {
				if (last == null) //first node
					users = curr.next;
				else
					last.next = curr.next;
				return;
			}
			last = curr;
			curr = curr.next;
		}
	}

	IRCUser getUser(String name) {
		for (IRCUser curr = users; curr != null; curr = curr.next)
			if (SephiaBotData.iequals(name, curr.name))
				return curr;
		return null;
	}
	
	void updateUser(String oldname, String newname, String host, int access) {
		IRCUser user = getUser(oldname);
		if (user != null) {
			if (newname != null)
				user.name = newname;
			if (host != null)
				user.host = host;
			if (access != IRCServer.ACCESS_UNKNOWN)
				user.access = access;
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
