package videoUtility;

public abstract class S3UserStream extends Thread {
	
	protected String _bucketName = FileData.BUCKET;
	protected String _key;
	protected boolean _isDone = false;

	public abstract void end();
}
