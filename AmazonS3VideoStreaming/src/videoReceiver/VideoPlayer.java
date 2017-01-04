package videoReceiver;

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
import java.util.LinkedList;

import performance.GNUPlotObject;
import performance.GNUScriptParameters;
import performance.GNUScriptWriter;
import performance.MetricsLogger;
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
	private static long _runnerStartTime;
	private static long _programStartTime;

	private static DisplayFrame 	   _display;
	private static S3Downloader 	   _downloader;
	private static FileWriter		   _fw;
	private static MetricsLogger	   _mLogger;
	private static PerformanceLogger   _pLogger;
	private static SharedQueue<String> _signalQueue;
	private static double[] 		   _specs = new double[3];
	//_specs: 0=Compression, 1=FPS, 2=SegmentLength; from setup file on S3
	private VideoStream _stream;

	//-------------------------------------------------------------------------
	//Constructor
	//-------------------------------------------------------------------------
	public VideoPlayer() {
		super();
		init();
	}

	//-------------------------------------------------------------------------
	//Main
	//-------------------------------------------------------------------------
	public static void main(String[] args) {
//		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		VideoPlayer player = new VideoPlayer();

		initDisplay();
		getSetupParameters();
		if(FileData.ISLOGGING) initLogger();

		player.start();
	}

	//-------------------------------------------------------------------------
	//Run method
	//-------------------------------------------------------------------------
	@Override
	public void run() {
		Runtime.getRuntime().addShutdownHook(new VideoPlayerShutdownHook(this));

while(_tests < NUM_OF_TESTS && !_isDone){
		
		if(_tests > 0) init();
		
		double endPlayTime, startPlayTime;
		double fps = _specs[1];
		long imgSize;
		LinkedList<BufferedImage> frameList;
		VideoSegment videoSegment = null;
		
		if(FileData.ISLOGGING){
			_mLogger.logConnectTime((System.currentTimeMillis() - _programStartTime)/1000.0);
			endPlayTime = System.currentTimeMillis()/1000.0;
		}

		System.out.println("Starting video player...");

		//isDone becomes false when "end()" function is called
		while (!_isDone) {
			if(FileData.ISLOGGING){//Quit if test is done
				if(_mLogger.getPlays() >= SEGS_T0_PLAY) break;
			}
			try {
				//Always get most current frame
				while(_stream.size() > 1){
					_stream.getVideoSegment();
					if(FileData.ISLOGGING) _mLogger.logSegmentDrop();
				}
				if(FileData.ISLOGGING){
					if(_stream.isEmpty()) _mLogger.logBufferEvent();
				}
				videoSegment = _stream.getVideoSegment();
				frameList = videoSegment.getImageList();
				
				if(FileData.ISLOGGING){
					_mLogger.logSegmentPlay();
					logVideoTransfer(videoSegment.getTimeStamp());
					imgSize = videoSegment.size()/frameList.size();
					startPlayTime = System.currentTimeMillis()/1000.0;
					_mLogger.logBuffer(startPlayTime - endPlayTime);
	//				_logger.logBytes(imgSize * (int)(_specs[1] * _specs[2]));//faster
				}
				System.out.println("Playing '" + videoSegment.toString() + "'");

				for(BufferedImage img : frameList){
					_display.setCurrentFrame(img);
					if(FileData.ISLOGGING) _mLogger.logBytes(imgSize);//more accurate
					if(_isDone) break;
					Utility.pause((long)(1000/fps));
				}
				
				if(FileData.ISLOGGING){
					endPlayTime = System.currentTimeMillis()/1000.0;
					_mLogger.logPlay(endPlayTime - startPlayTime);
				}
				
			} catch (Exception e) {
				if(_isDone) continue;
				System.err.println("VP: Problem reading video file: " + videoSegment.toString());
			}
		}//end video play
		
		closeEverything();
		
		if(FileData.ISLOGGING){
			_programStartTime = System.currentTimeMillis();
			_mLogger.reset();
			System.out.println("End test " + _tests);
			_tests++;
		}
}//end tests

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

		String  plog 		= FileData.PLAYER_LOG,
				logFolder 	= FileData.LOG_DIRECTORY,
				s3log		= FileData.S3DOWNLOADER_LOG,
				script		= FileData.GNU_PLAYER;

		writeGNUPlotScript(logFolder+plog, logFolder+s3log, script);
		
		try{
			_mLogger = new MetricsLogger();
			_pLogger = new PerformanceLogger(plog, logFolder);
			_pLogger.setStartTime(_runnerStartTime);
			
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
		int col[] = {1, 2};
		
		GNUPlotObject runnerPlot = new GNUPlotObject(
				new File(plog).getAbsolutePath(), "Overall Delay", col);
		GNUPlotObject s3Plot = new GNUPlotObject(
				new File(s3log).getAbsolutePath(), "Frame Delay", col);
		GNUScriptParameters params = new GNUScriptParameters("ICC Client");
		
		runnerPlot.setLine("lc rgb '#ff9900' lt 1 lw 2 pt 7 ps 1.5");
		s3Plot.setLine("lc rgb '#009900' lt 1 lw 2 pt 5 ps 1.5");
		
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
			_mLogger.logServerBitRate(getServerBitRate());
			if(_tests == 0) _fw.write(_mLogger.toCSVwHeader());
			else _fw.write(_mLogger.toCSV());
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
		if(_downloader != null) {
			_downloader.end();
			_downloader.interrupt();
			try {
				_downloader.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			_downloader = null;
		}
		if(!FileData.ISLOGGING) return;

		try {
			writeMetrics();
			if(_tests == NUM_OF_TESTS || _isDone) _fw.close();
			_pLogger.close();

		} catch (IOException e) {
			System.err.println("VP: Failed to close log file!");;
		}
	}
	
	private double getServerBitRate(){
		String sBitRate = _downloader.getServerBitRate();
		if(sBitRate == null) return -1.0;
		return Double.parseDouble(sBitRate);
	}
	
	private static void getSetupParameters(){
		_runnerStartTime = Long.parseLong(_signalQueue.dequeue());
		_specs[0] = Double.parseDouble(_signalQueue.dequeue());//compression
		_specs[1] = Double.parseDouble(_signalQueue.dequeue());//FPS
		_specs[2] = Double.parseDouble(_signalQueue.dequeue());//segmentLength
	}
	
	private void init(){
		_className = "ICC VideoPlayer";
		_stream = new VideoStream();
		_downloader = new S3Downloader(_stream);
		_signalQueue = new SharedQueue<>(10);
		_downloader.setSignal(_signalQueue);
		_downloader.start();
		
		if(!FileData.ISLOGGING) return;

		_programStartTime = System.currentTimeMillis();
		
		try {
			_fw = new FileWriter("videoStreamMetrics.csv");
			
		} catch(IOException e){
			System.err.println("Cannot initialize metrics file");
			System.exit(-1);
		}
	}
	
	/**
	 * Logs the time the video was sent versus the time the video was received.
	 * @param videoSegment	The video segment to be played.
	 */
	private void logVideoTransfer(long timeStamp){
		if(!FileData.ISLOGGING) return;

		long currentTime = System.currentTimeMillis();
		double timeElapsed = (currentTime - _pLogger.getStartTime())/1000.0;
		double videoStart = (timeStamp - _pLogger.getStartTime())/1000.0;
		double delay = timeElapsed - videoStart;
		
		try {
			_pLogger.logVideoTransfer(currentTime, delay);
		} catch (IOException e) {
			System.err.println("VP: Failed to log video segment...");
		}
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
		if(!_player.isAlive()) return;
		_player.end();
		_player.interrupt();
		try {
			_player.join();
		} catch (InterruptedException e) {
			System.err.println(e);
		}
	}
}
