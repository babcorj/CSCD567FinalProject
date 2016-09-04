package videoUtility;

public abstract class S3UserStream extends Thread {
	
	protected String bucketName = FileData.BUCKET.print();
	protected String key;
	protected boolean isDone = false;

	public abstract void end();
}
