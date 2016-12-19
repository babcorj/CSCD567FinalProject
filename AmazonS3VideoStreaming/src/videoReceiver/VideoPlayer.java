package videoReceiver;

import videoUtility.Calculate;
//local package
import videoUtility.DisplayFrame;
import videoUtility.FileData;
import videoUtility.ICCFrameReader;
import videoUtility.SharedQueue;
import videoUtility.Utility;
import videoUtility.VideoSegment;
import videoUtility.VideoSource;

import java.awt.image.BufferedImage;
//Java package
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.api.FrameGrab8Bit;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.scale.AWTUtil;

import GNUPlot.GNUScriptParameters;
import GNUPlot.GNUScriptWriter;
import GNUPlot.PlotObject;
import videoUtility.PerformanceLogger;

/**
 * 
 * @author Ryan Babcock
 * 
 * This class represents the client-side of the ICC. The S3Downloader grabs
 * the most recent video specified by the playlist stored in the S3 bucket.
 * The S3Downloader creates a video segment based on the video data and the
 * information within the playlist. The video segment is then sent to the
 * VideoPlayer where the video is displayed using the ICCFrameReader.
 * 
 * @version v.0.0.20
 * @see VideoSource, S3Downloader, PlaylistParser, ICCFrameReader
 *
 */
public class VideoPlayer extends VideoSource {

	//-------------------------------------------------------------------------
	//Member Variables
	//-------------------------------------------------------------------------
	static final long TIME_OFFSET = -25259;
	
	private static DisplayFrame _display;
	private static List<Double> _records;
	private static PerformanceLogger _logger;
	private static S3Downloader _downloader;
	private static SharedQueue<String> _signalQueue;
	private static String[] _specs = new String[3];
	//_specs: 0=Compression, 1=FPS, 2=SegmentLength; from setup file on S3
	private VideoStream stream;

	//-------------------------------------------------------------------------
	//Constructor
	//-------------------------------------------------------------------------
	public VideoPlayer() {
		super();
		_className = "ICC VideoPlayer";
		_records = new ArrayList<>();
		stream = new VideoStream();
		_downloader = new S3Downloader(stream);
		_signalQueue = new SharedQueue<>(10);
		_downloader.setSignal(_signalQueue);
		_downloader.start();
	}

	//-------------------------------------------------------------------------
	//Main
	//-------------------------------------------------------------------------
	public static void main(String[] args) {
		
//		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		VideoPlayer player = new VideoPlayer();

		initLogger();
		initDisplay();

		player.start();
	}

	//-------------------------------------------------------------------------
	//Run method
	//-------------------------------------------------------------------------
	@Override
	public void run() {
		Runtime.getRuntime().addShutdownHook(new VideoPlayerShutdownHook(this));

		double fps = Double.parseDouble(_specs[1]);
//		double timeStamp = -1.0;
		BufferedImage img;
		ICCFrameReader reader;
		VideoSegment videoSegment = null;

		System.out.println("Starting video player...");

		//isDone becomes false when "end()" function is called
		while (!_isDone) {
//			System.out.printf("Time played: %.2f\n", (float) (System.currentTimeMillis() - startTime)/1000);
//			double timeStarted = (double)((System.currentTimeMillis() - _logger.getStartTime())/1000);
			try {
				//Always get most current frame
				while(stream.size() > 1){
					stream.getSegment();
				}
				videoSegment = stream.getSegment();
				reader = new ICCFrameReader(videoSegment.data());
//				logDelay(timeStamp);
				System.out.println("Playing '" + videoSegment.toString() + "'");

				while((img = reader.readImage()) != null){
					_display.setCurrentFrame(img);
					Utility.pause((long)(1000/fps));
				}
				reader.close();
				reader = null;
				videoSegment = null;
				
			} catch (Exception e) {
				if(_isDone) continue;
				e.printStackTrace();
				System.err.println("VP: Problem reading video file: " + videoSegment.toString());
			}
		}
		closeEverything();
		
		System.out.println("VideoPlayer successfully closed");
	}

	//-------------------------------------------------------------------------
	//Private static methods
	//-------------------------------------------------------------------------
	/**
	 * Initializes the display.
	 * @see FrameDisplay
	 */
	private static void initDisplay(){
		_display = null;

		while(_display == null){
			try{
				System.out.println("Attempting to load display...");
				_display = new DisplayFrame("S3 Video Feed");
				_display.setVisible(true);
			}
			catch(Exception e){
				System.err.println("Failed to load display!");
				Utility.pause(200);
			}
		}
		System.out.println("Display initiated...");
	}

	/**
	 * Initializes logging used by GNUplot. Sends a PerformanceLogger to the
	 * S3Downloader since both require the same startup time.
	 * @see PerformanceLogger
	 */
	private static void initLogger(){

		String  plog 		= FileData.PLAYER_LOG.print(),
				logFolder 	= FileData.LOG_DIRECTORY.print(),
				s3log		= FileData.S3DOWNLOADER_LOG.print(),
				script		= FileData.SCRIPTFILE.print();

		long millis = Long.parseLong(_signalQueue.dequeue());
		_specs[0] = _signalQueue.dequeue();//compression
		_specs[1] = _signalQueue.dequeue();//FPS
		_specs[2] = _signalQueue.dequeue();//segmentLength
		System.out.println("PLAYER(segLen): " + _specs[2]);
		
		if(!FileData.ISLOGGING.isTrue()) return;

		writeGNUPlotScript(logFolder+plog, logFolder+s3log, script);
		try{
			_logger = new PerformanceLogger(plog, logFolder);
			_logger.setStartTime(millis);
		} catch (IOException ioe){
			System.err.println("Failed to open performance log");
		} catch (Exception e){
			System.err.println("Failed to create VP logger");
		}
		System.out.println("Setup file successfully loaded!");
	}
	
	/**
	 * Creates script based on logged data. Runnable by GNUplot.
	 * 
	 * @param scriptfile	The name of the script file.
	 * @param playerfile	The name of the video player log file.
	 * @param s3file		The name of the S3 downloader log file.
	 * @see PerformanceLogger, GNUScriptParameters, GNUScriptWriter
	 */
	private static void writeGNUPlotScript(String plog, String s3log, String script){
		int col1[] = {1, 2};
		PlotObject runnerPlot = new PlotObject(new File(plog).getAbsolutePath(), "Overall Delay", col1);
		runnerPlot.setLine("lc rgb '#ff9900' lt 1 lw 2 pt 7 ps 1.5");
		PlotObject s3Plot = new PlotObject(new File(s3log).getAbsolutePath(), "Frame Delay", col1);
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
			GNUScriptWriter writer = new GNUScriptWriter(script, params);
			writer.write();
			writer.close();
		}catch(IOException e){
			System.err.println(e);
		}
	}
	
	//-------------------------------------------------------------------------
	//Private non-static methods
	//-------------------------------------------------------------------------
	/**
	 * Closes all closeable instances created.
	 */
	private void closeEverything(){
		try {
			if(!FileData.ISLOGGING.isTrue()) return;
			
			double avg = Calculate.average(_records);
			_logger.log(String.format("#Avg Delay: " + avg + "\n"));
			_logger.log(String.format("#StdDev: " + Calculate.standardDeviation(avg, _records)) + "\n");
			_logger.close();
		} catch (IOException e) {
			System.err.println("VP: Failed to close log file!");;
		}
	}
	
	/**
	 * Logs the time the video was sent versus the time the video was received.
	 * @param videoSegment	The video segment to be played.
	 */
	private void logDelay(double timeStamp){
		if(!FileData.ISLOGGING.isTrue()) return;

//		double timeElapsed = (double)((System.currentTimeMillis() + TIME_OFFSET
//				- _logger.getStartTime())/1000);
//		System.out.println("Elapsed: " + timeElapsed);
//		double videoStart = ((double)(timeStamp - _logger.getStartTime())/1000);
//		System.out.println("VideoStart: " + videoStart);
//		double delay = timeElapsed - videoStart;
//		System.out.println("Player(Delay): " + (delay));
		try {
			_logger.logTime();
			_logger.log(" ");
			_logger.log((timeStamp));
			_logger.log("\n");
		} catch (IOException e) {
			System.err.println("VP: Failed to log video segment...");
		}
		_records.add(timeStamp);
	}
}

//-----------------------------------------------------------------------------
//Shutdown Hook
//-------------------------------------------------------------------------
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
