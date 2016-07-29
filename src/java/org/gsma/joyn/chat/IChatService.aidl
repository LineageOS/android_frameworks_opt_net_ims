package org.gsma.joyn.chat;

import org.gsma.joyn.IJoynServiceRegistrationListener;
import org.gsma.joyn.chat.IChatListener;
import org.gsma.joyn.chat.IChat;
import org.gsma.joyn.chat.IGroupChatListener;
import org.gsma.joyn.chat.IGroupChat;
import org.gsma.joyn.chat.INewChatListener;
import org.gsma.joyn.chat.ISpamReportListener;
import org.gsma.joyn.chat.IGroupChatSyncingListener;
import org.gsma.joyn.chat.ChatServiceConfiguration;

/**
 * Chat service API
 */
interface IChatService {
	boolean isServiceRegistered();
    
	void addServiceRegistrationListener(IJoynServiceRegistrationListener listener);

	void removeServiceRegistrationListener(IJoynServiceRegistrationListener listener); 

	ChatServiceConfiguration getConfiguration();
    
	IChat openSingleChat(in String contact, in IChatListener listener);
	
    IChat openMultiChat(in List<String> participants, in IChatListener listener);

	IChat initPublicAccountChat(in String contact, in IChatListener listener);

	IGroupChat initiateGroupChat(in List<String> contacts, in String subject, in IGroupChatListener listener);
    
	String sendOne2MultiMessage(in List<String> contacts, in String messages, in IGroupChatListener listener);
        
	int resendOne2MultiMessage(in String megId, in IGroupChatListener listener);
    
        String sendOne2MultiCloudMessageLargeMode(in List<String> contacts, in String messages, in IGroupChatListener listener);
    
	IGroupChat rejoinGroupChat(in String chatId);
    
	IGroupChat rejoinGroupChatId(in String chatId, in String rejoinId);
    
	IGroupChat restartGroupChat(in String chatId);

    void syncAllGroupChats(in IGroupChatSyncingListener listener);
    void syncGroupChat(in String chatId, in IGroupChatSyncingListener listener);
    
	void addEventListener(in INewChatListener listener);
    
	void removeEventListener(in INewChatListener listener);
    
	void initiateSpamReport(String contact, String messageId);
	
	void addSpamReportListener(in ISpamReportListener listener);
	
	void removeSpamReportListener(in ISpamReportListener listener);
    
	IChat getChat(in String chatId);

	IChat getPublicAccountChat(in String chatId);

	List<IBinder> getChats();

	List<IBinder> getGroupChats();
    
	IGroupChat getGroupChat(in String chatId);
	
	void blockGroupMessages(in String chatId, in boolean flag);
	
	int getServiceVersion();
	
	boolean isImCapAlwaysOn();
}