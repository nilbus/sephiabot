interface IRCListener {

	public void messageReceived(String msg);
	public void messageModeChange(String nick, String host, String channel, String mode, String recipient);
	public void messageNickChange(String nick, String host, String newname);
	public void messageChannelJoin(String nick, String host, String channel);
	public void messageChannelPart(String nick, String host, String channel, String message);
	public void messageChannelKick(String nick, String host, String channel, String message);
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
