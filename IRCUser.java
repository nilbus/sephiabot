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
