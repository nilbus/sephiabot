class User {
	String userName;
	String hosts[];
	String away;
	String password;
	long leavetime;
	int memberType = USER_MEMBER;

	static final int USER_MEMBER = 0;
	static final int USER_ADMIN = 1;
	
	User(String userName, String password, int memberType) {
		this.userName = userName;
		this.password = password;
		this.memberType = memberType;
		this.hosts = new String[10];
	}
	
	User(String userName, String password, int memberType, String hosts[], String away, long leavetime) {
		this(userName, password, memberType);
		this.hosts = hosts;
		this.away = away;
		this.leavetime = leavetime;
	}
}
