package org.gsma.joyn.ft;

import org.gsma.joyn.IJoynServiceRegistrationListener;
import org.gsma.joyn.ft.IFileTransfer;
import org.gsma.joyn.ft.IFileTransferListener;
import org.gsma.joyn.ft.INewFileTransferListener;
import org.gsma.joyn.ft.IFileSpamReportListener;
import org.gsma.joyn.ft.FileTransferServiceConfiguration;

/**
 * File transfer service API
 */
interface IFileTransferService {
	boolean isServiceRegistered();

	void addServiceRegistrationListener(IJoynServiceRegistrationListener listener);

	void removeServiceRegistrationListener(IJoynServiceRegistrationListener listener); 

	FileTransferServiceConfiguration getConfiguration();

	List<IBinder> getFileTransfers();
	
	IFileTransfer getFileTransfer(in String transferId);

	IFileTransfer transferFile(in String contact, in String filename, in String fileicon, in IFileTransferListener listener);
	
	IFileTransfer resumeFileTransfer(in String fileTranferId, in IFileTransferListener listener);

	IFileTransfer transferBurnFile(in String contact, in String filename, in String fileicon, in IFileTransferListener listener);

        IFileTransfer transferGeoLocFile(in String contact, in String filename, in String fileicon, in IFileTransferListener listener);
	
      IFileTransfer transferPublicChatFile(in String contact, in String filename, in String fileicon, in IFileTransferListener listener,int timeLen);
	
	IFileTransfer transferLargeModeFile(in String contact, in String filename, in String fileicon, in IFileTransferListener listener);

    IFileTransfer transferLargeModeBurnFile(in String contact, in String filename, in String fileicon, in IFileTransferListener listener);
	
	IFileTransfer transferFileToGroup(in String chatId,in List<String> contacts, in String filename, in String fileicon,in int timeLen, in IFileTransferListener listener);
	
	IFileTransfer transferMedia(in String contact,in String filename,in String fileicon,in IFileTransferListener listener,in int timeLen);
	
	IFileTransfer transferFileToMultirecepient(in List<String> contacts,in String filename,in boolean fileIcon,in IFileTransferListener listener,in int timeLen);
	
	void addNewFileTransferListener(in INewFileTransferListener listener);

	void removeNewFileTransferListener(in INewFileTransferListener listener);
	
	int getServiceVersion();

	void initiateFileSpamReport(String contact, String messageId);
	
	void addFileSpamReportListener(in IFileSpamReportListener listener);
	
	void removeFileSpamReportListener(in IFileSpamReportListener listener);

  int getMaxFileTransfers();
  
  IFileTransfer resumeGroupFileTransfer(in String chatId, in String fileTranferId, in IFileTransferListener listener);
  
  IFileTransfer resumePublicFileTransfer(in String fileTranferId, in IFileTransferListener listener, int timeLen);
  
}