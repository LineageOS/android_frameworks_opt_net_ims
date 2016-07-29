package org.gsma.joyn.chat;

/**
 * New chat invitation event listener
 */
interface ISpamReportListener {
	void onSpamReportSuccess( String contact, String msgId);
	
	void onSpamReportFailed( String contact, String msgId, int errorCode);
}
