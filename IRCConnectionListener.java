// This needs a better name
interface IRCConnectionListener {

	public void messageReceived(IRCConnection con, String msg);
	public void messageModeChange(IRCConnection con, String nick, String host, String channelName, String mode, String recipient);
	public void messageNickChange(IRCConnection con, String nick, String host, String newname);
	public void messageChannelJoin(IRCConnection con, String nick, String host, String channelName);
	public void messageChannelPart(IRCConnection con, String nick, String host, String channelName, String message, boolean kick);
	public void messagePrivMsg(IRCConnection con, String nick, String host, String recipient, String msg);
	public void messagePrivEmote(IRCConnection con, String nick, String host, String recipient, String msg);
	public void messageQuit(IRCConnection con, String nick, String host, String message);

	public void messageChanList(IRCConnection con, String channelName, String list);
	public void messageUserHosts(IRCConnection con, String users);
	public void messageWho(IRCConnection con, String userchannel, String usernick, String username, String host, String realname);

	public void log(String msg);
	public void logerror(String msg);

	public String getLogdir();
}
