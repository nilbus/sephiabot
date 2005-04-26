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
