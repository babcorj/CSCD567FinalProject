import java.io.File;

import org.opencv.core.*;
import org.opencv.videoio.*;

public class VideoPlayer extends VideoSource {

	private final static String TEMP_FOLDER = "";
	private final static String BUCKET = "icc-videostream-00";
	private final static String VIDEO_PREFIX = "myvideo";

	private static S3Downloader downloader;
	
	private VideoStream stream;
	
	public VideoPlayer(String bucket, String prefix, String output) {
		super();
		className = "video player";
		stream = new VideoStream();
		downloader = new S3Downloader(bucket, prefix, output, stream);
	}

	@Override
	public void run() {
		downloader.start();

		double FPS;
		long startTime = System.currentTimeMillis();
		String videoFilename = "";
		File video = null;
		VideoCapture grabber = null;

		System.out.println("Starting video player...");

		while (!isDone) {

			System.out.printf("Time played: %.2f\n", (float) (System.currentTimeMillis() - startTime)/1000);

			try {
				videoFilename = stream.getFrame();
				video = new File(videoFilename);
				grabber = new VideoCapture(video.getAbsolutePath());
				FPS = grabber.get(Videoio.CAP_PROP_FPS);

				while (grabber.read(mat) == true) {
					try{
						Utility.pause((long) FPS * mat.channels());
					} catch(NullPointerException npe){
						Utility.pause((long) FPS * 3);
					}
				}
				grabber.release();
				video.delete();

			} catch (Exception e) {
				System.err.println("Problem reading video file: " + videoFilename);;
			}
		}

		try{
			grabber.release();
			video.delete();
		} catch (Exception e){
			//Video or grabber = NULL
		}
		System.out.println("VideoPlayer successfully closed");
	}
	
	public static void main(String[] args) {
		
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		
		VideoPlayer player = new VideoPlayer(BUCKET, VIDEO_PREFIX, TEMP_FOLDER);
		
		player.start();
		
		initDisplay(downloader, player);
	}
	
	private static void initDisplay(S3UserStream s3, VideoPlayer player){
		DisplayFrame display = null;
		
		while(display == null){
			try{
				System.out.println("Attempting to load display...");
				display = new DisplayFrame("S3 Video Feed", player);
				display.setVisible(true);
			}
			catch(Exception e){
				System.err.println("Failed to load display!");
				Utility.pause(1000);
			}
		}
		DisplayFrameShutdownHook shutdownInstructions = new DisplayFrameShutdownHook(s3, player);
		Runtime.getRuntime().addShutdownHook(shutdownInstructions);
	}
}
