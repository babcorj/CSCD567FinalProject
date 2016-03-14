import java.io.File;

public class ICCCleaner extends Thread {
	S3Uploader s3;
	String toDelete;
	
	public ICCCleaner(S3Uploader S3) {
		s3 = S3;
	}
	
	public ICCCleaner(S3Uploader S3, String file) {
		s3 = S3;
		toDelete = file;
	}
	
	public void set(String file){
		toDelete = file;
	}
	
	public void run(){
		s3.delete(toDelete);
		File fdel = new File(toDelete);
		fdel.delete();
		System.out.println("Successfully deleted '"+toDelete+"' locally");
	}
}
