class IRCServer {

  String network;
  int port;
  IRCChannel channels[];
  IRCUser users[];

  public static int ACCESS_NONE = 0;
  public static int ACCESS_VOICE = 1;
  public static int ACCESS_HALFOP = 2;
  public static int ACCESS_OP = 3;

  IRCServer(String network, int port, String[] channels) {
    this.network = network;
    this.port = port;
    this.channels = new IRCChannel[channels.length];
	// It is important to maintain the order of the channels here, because the positions of
	// the channels are also their index when calling into SephiaBotData (if that made any
	// sense.)
    for (int i = 0; i < channels.length; i++) {
      this.channels[i] = new IRCChannel(channels[i]);
    }
  }

  int getChannelIndex(String channel) {
	  for (int i = 0; i < channels.length; i++)
		  if (channels[i].name.equals(channel))
			  return i;
	  return -1;
  }
}

class IRCChannel {

  String name;
  IRCUser users;
  int numusers = 0;

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

  boolean userInChannel(String nick) {
	  for (IRCUser user = users; user != null; user = user.next)
		  if (user.name.equalsIgnoreCase(nick))
			  return true;
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
