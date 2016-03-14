
public class ICCRunnerShutdownHook extends Thread {
	S3Uploader s3;
	ICCRunner iccr;
	
	public ICCRunnerShutdownHook(S3Uploader S3, ICCRunner iccrThread) {
			s3 = S3;
			iccr = iccrThread;
	}

	public void run(){
		try{
			s3.end();
			s3.interrupt();
			s3.join();
			iccr.end();
			iccr.join();
		} catch(Exception e){
			System.err.println(e + ": Unable to close S3!");
			e.printStackTrace();
		}
	}
}