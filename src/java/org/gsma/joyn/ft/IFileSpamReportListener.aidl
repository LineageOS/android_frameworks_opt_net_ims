package org.gsma.joyn.ft;

/**
 * New chat invitation event listener
 */
interface IFileSpamReportListener {
	void onFileSpamReportSuccess( String contact, String ftId);
	
	void onFileSpamReportFailed( String contact, String ftId, int errorCode);
}
