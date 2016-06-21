package videoReceiver;

import videoUtility.Calculate;
//local package
import videoUtility.DisplayFrame;
import videoUtility.S3UserStream;
import videoUtility.SharedQueue;
import videoUtility.Utility;
import videoUtility.VideoObject;
import videoUtility.VideoSource;

//Java package
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

//OpenCV package
import org.opencv.core.*;
import org.opencv.videoio.*;

import GNUPlot.GNUScriptParameters;
import GNUPlot.GNUScriptWriter;
import GNUPlot.PlotObject;
import videoUtility.PerformanceLogger;

public class VideoPlayer extends VideoSource {

	private final static String BUCKET = "icc-videostream-00";
	private final static String PLAYERLOG = "VideoPlayer_log.txt";
	private final static String S3LOG = "S3Downloader_log.txt";
	private final static String SCRIPTFILE = "inScript.p";
	private final static String TEMP_FOLDER = "";
	private final static String LOG_FOLDER = "log/";
	private final static String VIDEO_PREFIX = "myvideo";

	private static List<Double> _records;
	private static PerformanceLogger _logger;
	private static S3Downloader downloader;
	private static SharedQueue<String> _signalQueue;
	private static String[] _specs = new String[3];//for GNUPlot:
	//1=Compression, 2=FPS, 3= SegmentLength; taken from setup file on s3
	
	private VideoStream stream;

	//-------------------------------------------------------------------------
	//Constructor method
	
	public VideoPlayer(String bucket, String prefix, String output) {
		super();
		className = "ICC VideoPlayer";
		_records = new ArrayList<>();
		stream = new VideoStream();
		downloader = new S3Downloader(bucket, prefix, output, stream);
		_signalQueue = new SharedQueue<>(10);
		downloader.setSignal(_signalQueue);
		downloader.start();
	}

	//-------------------------------------------------------------------------
	//Main
	
	public static void main(String[] args) {
		
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		VideoPlayer player = new VideoPlayer(BUCKET, VIDEO_PREFIX, TEMP_FOLDER);

		initLogger();

		player.start();

		initDisplay(downloader, player);
	}

	//-------------------------------------------------------------------------
	//Run method
	
	@Override
	public void run() {
		Runtime.getRuntime().addShutdownHook(new VideoPlayerShutdownHook(this));
		
		double fps = Double.parseDouble(_specs[1]);
		long startTime = System.currentTimeMillis();
		File video = null;
		VideoCapture grabber = null;
		VideoObject videoSegment = null;
		
		System.out.println("Starting video player...");

		while (!isDone) {

			System.out.printf("Time played: %.2f\n", (float) (System.currentTimeMillis() - startTime)/1000);

			try {
				videoSegment = stream.getFrame();
				logDelay(videoSegment);

				video = new File(videoSegment.getFileName());
				grabber = new VideoCapture(video.getAbsolutePath());

				grabber.set(Videoio.CAP_PROP_FPS, fps);

				while (grabber.read(mat) == true) {
					Utility.pause((long) (1000/fps));
				}
				grabber.release();
				video.delete();
				
			} catch (Exception e) {
				if(isDone) continue;
				System.err.println("VP: Problem reading video file: " + videoSegment.getFileName());
			}
		}
		closeEverything(video, grabber);
		
		System.out.println("VideoPlayer successfully closed");
	}

	//-------------------------------------------------------------------------
	//Private static methods
	
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
				Utility.pause(200);
			}
		}
	}

	private static void initLogger(){
		String millis = _signalQueue.dequeue();
		PerformanceLogger s3logger = null;

		try{
			_logger = new PerformanceLogger(PLAYERLOG, LOG_FOLDER);
			s3logger = new PerformanceLogger(S3LOG, LOG_FOLDER);
			_logger.setStartTime(new BigDecimal(millis));
			s3logger.setStartTime(new BigDecimal(millis));
			writeGNUPlotScript(SCRIPTFILE, LOG_FOLDER+PLAYERLOG, LOG_FOLDER+S3LOG);
		}
		catch (IOException ioe){
			System.err.println("Failed to open performance log");
		}
		downloader.setPerformanceLog(s3logger);
		_signalQueue.enqueue("VP: Finished setting up log files");
		downloader.interrupt();
	}

	private static void writeGNUPlotScript(String scriptfile, String playerfile, String s3file){
		_specs[0] = _signalQueue.dequeue();//compression
		_specs[1] = _signalQueue.dequeue();//FPS
		_specs[2] = _signalQueue.dequeue();//segmentLength
		int col1[] = {1, 2};
		PlotObject runnerPlot = new PlotObject(new File(playerfile).getAbsolutePath(), "Overall Delay", col1);
		runnerPlot.setLine("lc rgb '#ff9900' lt 1 lw 2 pt 7 ps 1.5");
		PlotObject s3Plot = new PlotObject(new File(s3file).getAbsolutePath(), "Frame Delay", col1);
		s3Plot.setLine("lc rgb '#009900' lt 1 lw 2 pt 5 ps 1.5");
		GNUScriptParameters params = new GNUScriptParameters("ICC Client");
		params.addElement("CompressionRatio(" + _specs[0] + ")");
		params.addElement("FPS(" + _specs[1] + ")");
		params.addElement("SegmentLength(" + _specs[2] + ")");
		params.addPlot(runnerPlot);
		params.addPlot(s3Plot);
		params.setLabelX("Time Running (sec)");
		params.setLabelY("Delay (sec)");
		
		try{
			GNUScriptWriter writer = new GNUScriptWriter(scriptfile, params);
			writer.write();
			writer.close();
		}catch(IOException e){
			System.err.println(e);
		}
	}
	
	//-------------------------------------------------------------------------
	//Private non-static methods
	
	private void closeEverything(File video, VideoCapture grabber){
		double avg = Calculate.average(_records);
		try {
			_logger.log(String.format("#Avg Delay: " + avg + "\n"));
			_logger.log(String.format("#StdDev: " + Calculate.standardDeviation(avg, _records)) + "\n");
			_logger.close();
		} catch (IOException e) {
			System.err.println("VP: Failed to close log file!");;
		}
		try {
			grabber.release();
			video.delete();
		} catch (Exception e){
			System.err.println("VP: " + e);
		}
	}
	
	//log time sent vs. time received
	private void logDelay(VideoObject videoSegment){
		double timeOut = (System.currentTimeMillis() - _logger.getTime())/1000
				- videoSegment.getTimeStamp();
		try {
			_logger.logTime();
			_logger.log(" ");
			_logger.log((timeOut) + "\n");
		} catch (IOException e) {
			System.err.println("VP: Failed to log video segment '"
					+ videoSegment.getFileName() + "'");
		}
		_records.add(timeOut);
	}
}

//-----------------------------------------------------------------------------
//Shutdown Hook

class VideoPlayerShutdownHook extends Thread {
	private VideoPlayer _player;
	
	public VideoPlayerShutdownHook(VideoPlayer player){
		_player = player;
	}
	
	public void run(){
		_player.end();
		try {
			_player.interrupt();
			_player.join();
		} catch (InterruptedException e) {
			System.err.println(e);
		}
	}
}
