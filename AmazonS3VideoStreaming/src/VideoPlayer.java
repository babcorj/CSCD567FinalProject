import java.io.File;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;

public class VideoPlayer extends Thread {

	private static S3Downloader downloader;
	private VideoStream stream;
	private boolean isDone = false;
	
	public VideoPlayer(String bucket, String prefix, String output) {

		stream = new VideoStream();
		downloader = new S3Downloader(bucket, prefix, output, stream);
	}

	@Override
	public void run() {

		downloader.start();
		Frame image = null;
		System.out.println("Starting video player thread");
		CanvasFrame canvas = new CanvasFrame("Web Cam");
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		File video = null;
		FFmpegFrameGrabber grabber = null;
		
		while (!isDone) {

			try {
				double secondsPlayed = 0.0;
				long startTime = System.currentTimeMillis();
				System.out.printf("Time played: %.2f\n", secondsPlayed);
				
				while (!stream.isEmpty()) {

					video = stream.getFrame();
					grabber = new FFmpegFrameGrabber(video.getAbsolutePath());
					
					try {
						grabber.start();
						image = grabber.grabFrame();
//						System.out.println("Player got frame " + image.toString());

						while (image != null) {

//							System.out.println("Player got frame " + image.getClass().getName());
							canvas.showImage(image);
							secondsPlayed = ((double)System.currentTimeMillis() - startTime)/1000;
							System.out.printf("Time played: %.2f\n", secondsPlayed);
							image = grabber.grabFrame();
							try{
								Thread.sleep((long)(grabber.getFrameRate() *
										image.imageChannels));
							} catch(NullPointerException npe){
								//npe.printStackTrace();
								Thread.sleep((long)(grabber.getFrameRate() *
										3));
							}
						}
						//grabber.stop();
						video.delete();
						
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				synchronized (stream) {
					try{
						System.out.println("Player waiting");
						stream.wait();
					} catch(InterruptedException ie){
						end();
						continue;
					}
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try{
			grabber.stop();
			video.delete();
		} catch (Exception e){
			e.printStackTrace();
		}
		System.out.println("VideoPlayer successfully closed");
	}

	public void end(){
		System.out.println("Attempting to close VideoPlayer...");
		isDone = true;
	}
	
	public static void main(String[] args) {

		final String OUTPUT = ".\\";
		final String BUCKET = "icc-videostream-00";
		VideoPlayer player = new VideoPlayer(BUCKET, "myvideo", OUTPUT);

		VideoPlayerShutDownHook shutdownInstructions =
				new VideoPlayerShutDownHook(downloader, player);
		Runtime.getRuntime().addShutdownHook(shutdownInstructions);
		player.start();
	}
}
