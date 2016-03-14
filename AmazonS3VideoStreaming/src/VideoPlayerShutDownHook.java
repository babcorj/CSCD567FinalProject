
public class VideoPlayerShutDownHook extends Thread {
	S3Downloader s3;
	VideoPlayer vp;
	
	public VideoPlayerShutDownHook(S3Downloader S3, VideoPlayer player) {
			s3 = S3;
			vp = player;
	}

	public void run(){
		try{
			s3.end();
//			s3.interrupt();
			s3.join();
			vp.end();
			vp.interrupt();
			vp.join();
		} catch(Exception e){
			System.err.println(e + ": Unable to close threads!");
			e.printStackTrace();
		}
	}
}
