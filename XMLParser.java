/* SephiaBot
 * Copyright (C) 2005 Jorge Rodriguez and Ed Anderson
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 */

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory; 
import javax.xml.parsers.ParserConfigurationException;
 
import org.xml.sax.SAXException; 
import org.xml.sax.SAXParseException; 

import org.w3c.dom.*;

import java.io.*;
import java.util.*;

public class XMLParser {

	SephiaBotData data;
	Document document; 
	
	public XMLParser(SephiaBotData data) {
		this.data = data;
	}

	private boolean setupParsing(String directory, String fileName) {
		DocumentBuilderFactory factory =
			DocumentBuilderFactory.newInstance();
		
		try {
			DocumentBuilder builder =
				factory.newDocumentBuilder();
			if (directory != null && directory.trim().length() > 0)
				document = builder.parse(new File(directory, fileName));
			else
				document = builder.parse(new File(fileName));
			return true;
		} catch (SAXParseException spe) {
			// Error generated by the parser
			data.log("XML parsing error, line " + spe.getLineNumber() + ", uri " + spe.getSystemId() + "  " + spe.getMessage() );
			
			// Use the contained exception, if any
			Exception x = spe;
			if (spe.getException() != null)
				x = spe.getException();
			x.printStackTrace();
			return false;
		} catch (SAXException sxe) {
			// Error generated by this application
			// (or a parser-initialization error)
			Exception x = sxe;
			if (sxe.getException() != null)
				x = sxe.getException();
			x.printStackTrace();
			return false;
		} catch (ParserConfigurationException pce) {
			// Parser with specified options can't be built
			pce.printStackTrace();
			return false;
		} catch (IOException ioe) {
			// I/O error
			ioe.printStackTrace();
			return false;
		}
	}
	
	void parseConfig(String directory, String fileName) {
		if (!setupParsing(directory, fileName)) {
			data.logerror("Error opening " + fileName);
			return;
		}

		String sephiadir = "", logdir = "", dataFileName = "", usersFileName = "";
		
		NodeList groupNodes = document.getDocumentElement().getChildNodes();

		for (int i = 0; i < groupNodes.getLength(); i++) {
			Node node = groupNodes.item(i);
			if (parseConfigGlobal(node, -1, -1))
				continue;
			else if (node.getNodeName().equals("Logdir")) {
				logdir = node.getChildNodes().item(0).getNodeValue();
				continue;
			} else if (node.getNodeName().equals("Sephiadir")) {
				sephiadir = node.getChildNodes().item(0).getNodeValue();
				continue;
			} else if (node.getNodeName().equals("DataFileName")) {
				dataFileName = node.getChildNodes().item(0).getNodeValue();
				continue;
			} else if (node.getNodeName().equals("UsersFileName")) {
				usersFileName = node.getChildNodes().item(0).getNodeValue();
				continue;
			} else if (node.getNodeName().equals("Blacklist")) {
				data.addToBlacklist(node.getChildNodes().item(0).getNodeValue());
				continue;
			} else if (node.getNodeName().equals("Server")) {
				parseConfigServer(node);
				continue;
			}
		}

		data.setGlobals(sephiadir, logdir, dataFileName, usersFileName);
	}

	boolean parseConfigGlobal(Node node, int server, int channel) {
		if (node.getNodeName().equals("Hello")) {
			data.setHellos(node.getChildNodes().item(0).getNodeValue(), server, channel);
			return true;
		} else if (node.getNodeName().equals("Censor")) {
			data.setCensor(data.stringToBoolean(node.getChildNodes().item(0).getNodeValue()), server, channel);
			return true;
		} else if (node.getNodeName().equals("Greeting")) {
			if (node.getChildNodes().item(0) != null)
				data.setGreeting(node.getChildNodes().item(0).getNodeValue(), server, channel);
			return true;
		} else if (node.getNodeName().equals("HelloReplies")) {
			data.setHelloReplies(node.getChildNodes().item(0).getNodeValue(), server, channel);
			return true;
		}
		return false;
	}

	void parseConfigServer(Node node) {
		String host = "", nick = "", port = "";
		int server = data.addServer();
		NodeList subNodes = node.getChildNodes();
		for (int i = 0; i < subNodes.getLength(); i++) {
			Node subNode = subNodes.item(i);
			if (parseConfigGlobal(subNode, server, -1))
				continue;
			if (subNode.getNodeName().equals("Host")) {
				host = subNode.getChildNodes().item(0).getNodeValue();
				continue;
			} else if (subNode.getNodeName().equals("Nick")) {
				nick = subNode.getChildNodes().item(0).getNodeValue();
				continue;
			} else if (subNode.getNodeName().equals("Port")) {
				port = subNode.getChildNodes().item(0).getNodeValue();
				continue;
			} else if (subNode.getNodeName().equals("Channel")) {
				parseConfigChannel(server, subNode);
				continue;
			}
		}
		data.setServerInfo(server, host, port, nick);
	}

	void parseConfigChannel(int server, Node node) {
		int channel = data.addChannel(server);
		NodeList subNodes = node.getChildNodes();
		for (int i = 0; i < subNodes.getLength(); i++) {
			Node subNode = subNodes.item(i);
			if (parseConfigGlobal(subNode, server, channel))
				continue;
			if (subNode.getNodeName().equals("Name")) {
				data.setChannelInfo(server, channel, subNode.getChildNodes().item(0).getNodeValue());
				continue;
			}
		}
	}

	User[] parseUsers(String directory, String fileName) {
		if (!setupParsing(directory, fileName)) {
			data.logerror("Error opening " + fileName);
			return new User[0];
		}
		return parseUsers(parseGroups());
	}
	
	XMLGroup[] parseGroups( ) {
		Vector groupList = new Vector(5);
		NodeList groupNodes = document.getDocumentElement().getChildNodes();

		for (int i = 0; i < groupNodes.getLength(); i++) {
			Node groupNode = groupNodes.item(i);
			if (!groupNode.getNodeName().equals("Group"))
				continue;
			String groupName = null, groupPermissions = null;
			NodeList groupSubNodes = groupNode.getChildNodes();
			for (int j = 0; j < groupSubNodes.getLength(); j++) {
				Node subNode = groupSubNodes.item(j);
				if (subNode.getNodeName().equals("Name"))
					groupName = subNode.getChildNodes().item(0).getNodeValue();
				else if (subNode.getNodeName().equals("Permissions"))
					groupPermissions = subNode.getChildNodes().item(0).getNodeValue();
			}
			if (groupPermissions == null || groupName == null)
				continue;
			String permissionsArray[] = groupPermissions.split(",");
			int highestPermissions = User.USER_NOBODY;
			for (int j = 0; j < permissionsArray.length; j++) {
				if (permissionsArray[j].equals("sephiabot"))
					highestPermissions = User.USER_MEMBER;
				else if (permissionsArray[j].equals("sephiabot-admin"))
					highestPermissions = User.USER_ADMIN;
			}
			if (highestPermissions < User.USER_MEMBER)
				continue;
			groupList.add(new XMLGroup(groupName, highestPermissions));
		}
		XMLGroup[] groups = new XMLGroup[groupList.size()];
		return (XMLGroup[])groupList.toArray(groups);
	}

	User[] parseUsers(XMLGroup groups[]) {
		Vector newUserList = new Vector(10);
		NodeList userNodes = document.getDocumentElement().getChildNodes();

		for (int i = 0; i < userNodes.getLength(); i++) {
			Node userNode = userNodes.item(i);
			if (!userNode.getNodeName().equals("User"))
				continue;
			NodeList groupSubNodes = userNode.getChildNodes();
			String userName = null, password = null, groupNames = null, description = null, home = null;
			Vector aliasList = new Vector (3);
			for (int j = 0; j < groupSubNodes.getLength(); j++) {
				Node subNode = groupSubNodes.item(j);
				if (subNode.getNodeName().equals("Nick"))
					userName = subNode.getChildNodes().item(0).getNodeValue();
				else if (subNode.getNodeName().equals("Password"))
					password = subNode.getChildNodes().item(0).getNodeValue();
				else if (subNode.getNodeName().equals("Groups"))
					groupNames = subNode.getChildNodes().item(0).getNodeValue();
				else if (subNode.getNodeName().equals("Description"))
					description = subNode.getChildNodes().item(0).getNodeValue();
				else if (subNode.getNodeName().equals("Alias"))
					aliasList.add(subNode.getChildNodes().item(0).getNodeValue());
				else if (subNode.getNodeName().equals("Home"))
					home = subNode.getChildNodes().item(0).getNodeValue();
			}
			if (userName == null || password == null || groupNames == null)
				continue;
			String groupsArray[] = groupNames.split(",");
			int memberType = User.USER_NOBODY;
			for (int j = 0; j < groupsArray.length; j++) {
				for (int k = 0; k < groups.length; k++) {
					if (groupsArray[j].equals(groups[k].name) && groups[k].permissions > memberType) {
						memberType = groups[k].permissions;
					}
				}
			}
			if (memberType < User.USER_MEMBER)
				continue;
			User newUser = new User(userName, password, memberType, home);
			if (description != null)
				newUser.description = description;
			if (newUserList.size() > 0) {
				String[] aliases = new String[aliasList.size()];
				newUser.aliases = (String[])aliasList.toArray(aliases);
			}
			//TODO: Parse aliases and descriptions.
			newUserList.add(newUser);
		}
		User[] users = new User[newUserList.size()];
		return (User[])newUserList.toArray(users);
	}

	void logerror(String msg) {
		data.logerror(msg);
	}
}

class XMLGroup {
	String name;
	int permissions = User.USER_NOBODY;

	XMLGroup(String name, int permissions) {
		this.name = name;
		this.permissions = permissions;
	}
}
