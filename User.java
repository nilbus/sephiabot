class User {
	String userName;
	String hosts[];
	long lastSeenTimes[];
	String away;
	String password;
	long leaveTime;
	long lastTalked;
	int memberType = USER_MEMBER;

	static final int USER_MEMBER = 0;
	static final int USER_ADMIN = 1;
	
	User(String userName, String password, int memberType) {
		this.userName = userName;
		this.password = password;
		this.memberType = memberType;
		this.hosts = new String[10];
		this.lastSeenTimes = new long[10];
	}
}
