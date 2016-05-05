
public class DisplayFrameShutdownHook extends Thread{
	S3UserStream _s3;
	VideoSource _source;
	
	public DisplayFrameShutdownHook(S3UserStream S3, VideoSource source) {
			_s3 = S3;
			_source = source;
	}

	public void run(){
		try{
			_s3.end();
			_s3.interrupt();
			_s3.join();
			_source.end();
			_source.interrupt();
			_source.join();
		} catch(Exception e){
			System.err.println(e + ": Unable to close thread(s)!");
			e.printStackTrace();
		}
	}
}
