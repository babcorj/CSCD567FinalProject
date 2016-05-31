package videoReceiver;
//local package
import videoUtility.DisplayFrame;
import videoUtility.DisplayFrameShutdownHook;
import videoUtility.S3UserStream;
import videoUtility.Utility;
import videoUtility.VideoObject;
import videoUtility.VideoSource;

import java.io.BufferedReader;
//Java package
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;

//OpenCV package
import org.opencv.core.*;
import org.opencv.videoio.*;
import videoUtility.PerformanceLogger;

public class VideoPlayer extends VideoSource {

	private final static String BUCKET = "icc-videostream-00";
	private final static String SETUPFILE = "setup.txt";
	private final static String TEMP_FOLDER = "";
	private final static String VIDEO_PREFIX = "myvideo";

	private static PerformanceLogger _logger;
	private static S3Downloader downloader;

	private VideoStream stream;

	public VideoPlayer(String bucket, String prefix, String output) {
		super();
		className = "ICC VideoPlayer";
		stream = new VideoStream();
		downloader = new S3Downloader(bucket, prefix, output, stream);
		initLogger();
	}

	public static void main(String[] args) {

		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		VideoPlayer player = new VideoPlayer(BUCKET, VIDEO_PREFIX, TEMP_FOLDER);

		player.start();

		initDisplay(downloader, player);
	}

	@Override
	public void run() {
		downloader.start();

		double FPS;
		long startTime = System.currentTimeMillis();
		File video = null;
		VideoCapture grabber = null;
		VideoObject videoSegment = null;

		System.out.println("Starting video player...");

		while (!isDone) {

			System.out.printf("Time played: %.2f\n", (float) (System.currentTimeMillis() - startTime)/1000);

			try {
				videoSegment = stream.getFrame();
    			//log time sent vs. time received
				_logger.logTime();
				_logger.log(" ");
				_logger.log(videoSegment.getTimeStamp() + "\n");
				
				video = new File(videoSegment.getFileName());
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
				System.err.println("Problem reading video file: " + videoSegment.getFileName());
			}
		}

		try{
			_logger.close();
			grabber.release();
			video.delete();
		} catch (Exception e){
			//Video or grabber = NULL
		}
		System.out.println("VideoPlayer successfully closed");
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

	//---------------------------------------------------------------------
	//Setup -- sets PerformanceLogger start time
	private static void initLogger(){
		PerformanceLogger s3logger = null;

		try{
			_logger = new PerformanceLogger("VideoPlayer_log.txt");
			s3logger = new PerformanceLogger("S3Downloader_log.txt");
			BufferedReader br = new BufferedReader(new FileReader(SETUPFILE));
			String millis = br.readLine();
			BigDecimal startTime = new BigDecimal(millis);
			_logger.setStartTime(startTime);
			s3logger.setStartTime(new BigDecimal(millis));
			br.close();
		}
		catch(IOException ioe){
			System.err.println("Failed to open performance log");
		}

		downloader.setPerformanceLog(s3logger);
	}
}
