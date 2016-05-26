package videoUtility;

public abstract class S3UserStream extends Thread {
	
	protected String bucketName;
	protected String key;
	protected boolean isDone;
	
	public S3UserStream (String bucket){
		bucketName = bucket;
		isDone = false;
	}
	
	public abstract void end();
}
