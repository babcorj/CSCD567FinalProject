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
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

//OpenCV package
import org.opencv.core.*;

import GNUPlot.GNUScriptParameters;
import GNUPlot.GNUScriptWriter;
import GNUPlot.PlotObject;
import videoUtility.PerformanceLogger;

public class VideoPlayer extends VideoSource {

	private static DisplayFrame _display;
	private static List<Double> _records;
	private static PerformanceLogger _logger;
	private static S3Downloader downloader;
	private static SharedQueue<String> _signalQueue;
	private static String[] _specs = new String[3];//for GNUPlot:
	//1=Compression, 2=FPS, 3= SegmentLength; taken from setup file on s3
	
	private VideoStream stream;

	//-------------------------------------------------------------------------
	//Constructor method
	
	public VideoPlayer() {
		super();
		className = "ICC VideoPlayer";
		_records = new ArrayList<>();
		stream = new VideoStream();
		downloader = new S3Downloader(stream);
		_signalQueue = new SharedQueue<>(10);
		downloader.setSignal(_signalQueue);
		downloader.start();
	}

	//-------------------------------------------------------------------------
	//Main
	
	public static void main(String[] args) {
		
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		VideoPlayer player = new VideoPlayer();

		initLogger();
		initDisplay();

		player.start();
	}

	//-------------------------------------------------------------------------
	//Run method
	
	@Override
	public void run() {
		Runtime.getRuntime().addShutdownHook(new VideoPlayerShutdownHook(this, downloader));
		
		double fps = Double.parseDouble(_specs[1]);
		long startTime = System.currentTimeMillis();
		LinkedList<BufferedImage> frameList;
		VideoSegment videoSegment = null;

		System.out.println("Starting video player...");

		while (!isDone) {
			System.out.printf("Time played: %.2f\n", (float) (System.currentTimeMillis() - startTime)/1000);

			try {
				videoSegment = stream.getFrame();
//				System.out.println("FrameList: " + videoSegment.size());
				logDelay(videoSegment);
				frameList = videoSegment.getImageList();

				for(BufferedImage img : frameList){
					_display.setCurrentFrame(img);
					Utility.pause((long)(1000/fps));
				}
			} catch (Exception e) {
				if(isDone) continue;
				System.err.println("VP: Problem reading video file: " + videoSegment.getName());
			}
		}
		closeEverything();
		
		System.out.println("VideoPlayer successfully closed");
	}

	//-------------------------------------------------------------------------
	//Private static methods
	
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

	private static void initLogger(){
		String  plog 		= FileData.PLAYER_LOG.print(),
				logFolder 	= FileData.LOG_DIRECTORY.print(),
				s3dlog		= FileData.S3DOWNLOADER_LOG.print(),
				script		= FileData.SCRIPTFILE.print();
		
		String millis = _signalQueue.dequeue();
		PerformanceLogger s3logger = null;

		try{
			_logger = new PerformanceLogger(plog, logFolder);
			s3logger = new PerformanceLogger(s3dlog, logFolder);

			_logger.setStartTime(Long.parseLong(millis));
			s3logger.setStartTime(Long.parseLong(millis));

			writeGNUPlotScript(script, logFolder + plog, logFolder + s3dlog);
		}
		catch (IOException ioe){
			System.err.println("Failed to open performance log");
		}
		downloader.setPerformanceLog(s3logger);
		_signalQueue.enqueue("VP: Log files initialized...");
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
	
	private void closeEverything(){
		double avg = Calculate.average(_records);
		try {
			_logger.log(String.format("#Avg Delay: " + avg + "\n"));
			_logger.log(String.format("#StdDev: " + Calculate.standardDeviation(avg, _records)) + "\n");
			_logger.close();
		} catch (IOException e) {
			System.err.println("VP: Failed to close log file!");;
		}
	}
	
	//log time sent vs. time received
	private void logDelay(VideoSegment videoSegment){
		double timeOut = (System.currentTimeMillis() - _logger.getTime())/1000
				- videoSegment.getTimeStamp();
		try {
			_logger.logTime();
			_logger.log(" ");
			_logger.log((timeOut) + "\n");
		} catch (IOException e) {
			System.err.println("VP: Failed to log video segment '"
					+ videoSegment.getName() + "'");
		}
		_records.add(timeOut);
	}
}

//-----------------------------------------------------------------------------
//Shutdown Hook

class VideoPlayerShutdownHook extends Thread {
	private VideoPlayer _player;
	private S3Downloader _downloader;
	
	public VideoPlayerShutdownHook(VideoPlayer player, S3Downloader s3downloader){
		_player = player;
		_downloader = s3downloader;
	}
	
	public void run(){
		_player.end();
		_downloader.end();
		try {
			_player.interrupt();
			_player.join();
			_downloader.interrupt();
			_downloader.join();
		} catch (InterruptedException e) {
			System.err.println(e);
		}
	}
}
