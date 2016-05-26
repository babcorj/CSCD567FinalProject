package videoSender;

import java.io.File;

public class ICCCleaner extends Thread {
	S3Uploader s3;
	String toDelete,
		   localFolder;
	
	public ICCCleaner(S3Uploader S3, String file, String fileFolder) {
		s3 = S3;
		toDelete = file;
		localFolder = fileFolder;
	}
	
	public void set(String file){
		toDelete = file;
	}
	
	public void run(){
		s3.delete(toDelete);
		File fdel = new File(localFolder + toDelete);
		fdel.delete();
		System.out.println("Successfully deleted '"+toDelete+"' locally");
	}
}
