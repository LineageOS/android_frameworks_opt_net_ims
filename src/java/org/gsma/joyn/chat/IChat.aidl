package org.gsma.joyn.chat;

import org.gsma.joyn.chat.IChatListener;
import org.gsma.joyn.chat.ISpamReportListener;
import org.gsma.joyn.chat.Geoloc;

/**
 * Chat interface
 */
interface IChat {
	String getRemoteContact();
	

	String sendMessage(in String message);
	
	void sendDisplayedDeliveryReport(in String msgId);
	
	void sendIsComposingEvent(in boolean status);
	
	void addEventListener(in IChatListener listener);
	
	void removeEventListener(in IChatListener listener);

	void addSpamReportListener(in ISpamReportListener listener);
	
	void removeSpamReportListener(in ISpamReportListener listener);

	String sendGeoloc(in Geoloc geoloc);
	
	int resendMessage(in String msgId);
	
	int reSendMultiMessageByPagerMode(in String msgId);
	
	String sendMessageByLargeMode(in String message);
	
        String sendCloudMessageByLargeMode(in String message);
	
	String sendPublicAccountMessageByLargeMode(in String message);
	
	String sendMessageByPagerMode(in String message ,in boolean isBurnMessage , in boolean isPublicMessage , in boolean isMultiMessage , in boolean isPayEmoticon,in List<String> participants);
	
	void sendSpamMessageByPagerMode(in String contact, in String messageId) ;
	
	String sendOnetoMultiMessage(in String message, in List<String> participants);
	
	String sendOnetoMultiEmoticonsMessage(in String message, in List<String> participants);
		
	//String sendOnetoMultiCloudMessage(in String message, in List<String> participants);
	
	String sendEmoticonShopMessage(in String message);
	String sendCloudMessage(in String message);
	
	String sendPagerModeBurnMessage(in String message);
	
	String sendLargeModeBurnMessage(in String message);
	
	int getState(in String msgId);
	
	void sendBurnDeliveryReport(in String msgId);
}
