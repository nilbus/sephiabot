class User {
	String userName;
	String aliases[];
	String hosts[];
	long lastSeenTimes[];
	String away;
	String password;
	String description;
	long leaveTime;
	long lastTalked;
	int memberType = USER_NOBODY;

	static final int USER_NOBODY = 0;
	static final int USER_MEMBER = 1;
	static final int USER_ADMIN = 2;
	
	User(String userName, String password, int memberType) {
		this.userName = userName;
		this.password = password;
		this.memberType = memberType;
		this.hosts = new String[10];
		this.lastSeenTimes = new long[10];
		this.description = "A person.";
		this.aliases = new String[0];
	}
}
