package videoReceiver;

import videoUtility.Calculate;
//local package
import videoUtility.DisplayFrame;
import videoUtility.FileData;
import videoUtility.SharedQueue;
import videoUtility.Utility;
import videoUtility.VideoSegment;
import videoUtility.VideoSource;

import java.awt.image.BufferedImage;
//Java package
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

//OpenCV package
import GNUPlot.GNUScriptParameters;
import GNUPlot.GNUScriptWriter;
import GNUPlot.PlotObject;
import performance.PerformanceLogger;

/**
 * 
 * @author Ryan Babcock
 * 
 * This class represents the client-side of the ICC. The S3Downloader grabs
 * the most recent video specified by the key name within the S3 bucket.
 * The S3Downloader creates a video segment based on the video data. The video 
 * segment is then sent to the VideoPlayer where the video is displayed
 * using the ICCFrameReader and DisplayFrame classes.
 * 
 * @version v.3
 * @see VideoSource, S3Downloader, PlaylistParser, ICCFrameReader
 *
 */
public class VideoPlayer extends VideoSource {

	//-------------------------------------------------------------------------
	//Member Variables
	//-------------------------------------------------------------------------
	//testing
	private static final int NUM_OF_TESTS = 1;
	private static final int SEGS_T0_PLAY = 10;	
	private int _tests = 0;

	//metrics
	private static long _programStartTime;
	private static List<Double> 	   _delayRecords;//record of delays

	private static DisplayFrame 	   _display;
	private static S3Downloader 	   _downloader;
	private static FileWriter		   _fw;
	private static PerformanceLogger   _logger;
	private static SharedQueue<String> _signalQueue;
	private static double[] 		   _specs = new double[3];
	//_specs: 0=Compression, 1=FPS, 2=SegmentLength; from setup file on S3
	private VideoStream _stream;

	//-------------------------------------------------------------------------
	//Constructor
	//-------------------------------------------------------------------------
	public VideoPlayer() {
		super();
		_className = "ICC VideoPlayer";
		_delayRecords = new ArrayList<>();
		_stream = new VideoStream();
		_downloader = new S3Downloader(_stream);
		_signalQueue = new SharedQueue<>(10);
		_downloader.setSignal(_signalQueue);
		_downloader.start();
		try {
			_fw = new FileWriter("videoStreamMetrics.csv");
		} catch(IOException e){
			System.err.println("Cannot initialize metrics file");
			System.exit(-1);
		}
	}

	//-------------------------------------------------------------------------
	//Main
	//-------------------------------------------------------------------------
	public static void main(String[] args) {
		_programStartTime = System.currentTimeMillis();
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

while(_tests < NUM_OF_TESTS){
		double endPlayTime, startPlayTime;
		double fps = _specs[1];
		long imgSize;
		
		LinkedList<BufferedImage> frameList;
		VideoSegment videoSegment = null;
		_logger.logConnectTime((System.currentTimeMillis() - _programStartTime)/1000.0);
		endPlayTime = System.currentTimeMillis()/1000.0;
		System.out.println("Starting video player...");

		//isDone becomes false when "end()" function is called
		while (!_isDone && _logger.getPlays() < SEGS_T0_PLAY) {
			try {
				//Always get most current frame
				while(_stream.size() > 1){
					_stream.getVideoSegment();
					_logger.logSegmentDrop();
				}
				if(_stream.isEmpty()){ _logger.logBufferEvent(); }
				videoSegment = _stream.getVideoSegment();
				_logger.logSegmentPlay();
				logVideoTransfer(videoSegment.getTimeStamp());
				frameList = videoSegment.getImageList();
				imgSize = videoSegment.size()/frameList.size();
				System.out.println("Playing '" + videoSegment.toString() + "'");
				startPlayTime = System.currentTimeMillis()/1000.0;
				_logger.logBuffer(startPlayTime - endPlayTime);
//				_logger.logBytes(imgSize * (int)(_specs[1] * _specs[2]));//faster

				for(BufferedImage img : frameList){
					_display.setCurrentFrame(img);
					_logger.logBytes(imgSize);//more accurate
					if(_isDone) break;
					Utility.pause((long)(1000/fps));
				}
				
				endPlayTime = System.currentTimeMillis()/1000.0;
				_logger.logPlay(endPlayTime - startPlayTime);

			} catch (Exception e) {
				if(_isDone) continue;
				System.err.println("VP: Problem reading video file: " + videoSegment.toString());
			}
		}
		writeMetrics();
		_programStartTime = System.currentTimeMillis();
		_logger.reset();
		System.out.println("End test " + _tests);
		_tests++;
}//end tests
		closeEverything();

		System.out.println("VideoPlayer successfully closed");
		_display.end();
		System.exit(0);
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
		_specs[0] = Double.parseDouble(_signalQueue.dequeue());//compression
		_specs[1] = Double.parseDouble(_signalQueue.dequeue());//FPS
		_specs[2] = Double.parseDouble(_signalQueue.dequeue());//segmentLength

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
	
	private void writeMetrics(){
		try {
			_logger.logServerBitRate(getServerBitRate());
			if(_tests == 0) _fw.write(_logger.toCSVwHeader());
			else _fw.write(_logger.toCSV());
//			_fw.write(_logger.toString());
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		System.out.println("Metrics recorded!");
	}
	
	//-------------------------------------------------------------------------
	//Private non-static methods
	//-------------------------------------------------------------------------
	/**
	 * Closes all closeable instances created.
	 */
	private void closeEverything(){
		if(_downloader != null) _downloader.end();
		try {
			if(!FileData.ISLOGGING.isTrue()) return;
			
			double avg = Calculate.average(_delayRecords);

			_fw.close();
			_logger.log(String.format("#Avg Delay: " + avg + "\n"));
			_logger.log(String.format("#StdDev: " + Calculate.standardDeviation(avg, _delayRecords)) + "\n");
			_logger.close();
			
		} catch (IOException e) {
			System.err.println("VP: Failed to close log file!");;
		}
	}
	
	private double getServerBitRate(){
		String sBitRate = _downloader.getServerBitRate();
		if(sBitRate == null) return -1.0;
		return Double.parseDouble(sBitRate);
	}
	
	/**
	 * Logs the time the video was sent versus the time the video was received.
	 * @param videoSegment	The video segment to be played.
	 */
	private void logVideoTransfer(long timeStamp){
		if(!FileData.ISLOGGING.isTrue()) return;

		double timeElapsed = (double)((System.currentTimeMillis() //+ TIME_OFFSET
				- _logger.getStartTime())/1000.0);
		double videoStart = ((double)(timeStamp - _logger.getStartTime())/1000.0);
		double delay = timeElapsed - videoStart;
		try {
			_logger.logTime();
			_logger.log(" ");
			_logger.logVideoTransfer((delay));
			_logger.log("\n");
		} catch (IOException e) {
			System.err.println("VP: Failed to log video segment...");
		}
		_delayRecords.add(delay);
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
