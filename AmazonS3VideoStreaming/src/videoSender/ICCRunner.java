package videoSender;

import videoUtility.VideoSource;
import videoUtility.DisplayFrame;
import videoUtility.FileData;
import videoUtility.VideoSegmentHeader;
import videoUtility.ICCFrameWriter;
import performance.GNUPlotObject;
import performance.GNUScriptParameters;
import performance.GNUScriptWriter;
import performance.PerformanceLogger;
import videoUtility.SharedQueue;
import videoUtility.Utility;
import videoUtility.VideoSegment;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;

/**
 * 
 * @author Ryan Babcock
 * 
 * The ICCRunner is the main starting program for the ICC Camera Unit. This
 * version compresses each Mat object (recorded using OpenCV library) into a
 * jpg image, and then saves that image to a byte array. After a certain
 * amount of images are saved, the byte array (now resembling a video) is
 * stored into an input stream and then uploaded to Amazon S3.
 * <p>
 * This version uses a video naming convention that lets the client know which 
 * video is most current. This improves on the last version by removing the
 * need for a playlist, which would have doubled the cost of using Amazon S3
 * (since the number of uploads would double with each playlist update).
 *
 * Used in Run configuration settings:
 * 	Djava.library.path=/home/pi/Libraries/opencv-3.1.0/build/lib
 * 	System.out.println(System.getProperty("java.library.path"));

 * @version v.3.1
 * @see VideoSource
 */
public class ICCRunner extends VideoSource {

	/*
	 * @param MAX_VIDEO_INDEX	Refers to the number used in the file name for
	 * 							each video.
	 * @param MAX_SEGMENTS		Refers to the number of video segments that
	 * 							can be saved to Amazon S3 (anything older is
	 * 							deleted from the bucket).
	 */
	private final static int MAX_VIDEO_INDEX = 500;
	private final static int MAX_SEGMENTS = 20;
	private final static boolean PREVIEW = true;
	
	/*
	 * ICCSetup is used to configure the video recorder settings
	 */
	private static ICCSetup _setup = new ICCSetup()
			.setCompressionRatio(1)
			.setDevice(0)
			.setFourCC("MJPG")
			.setFPS(6)
			.setMaxIndex(MAX_VIDEO_INDEX)
			.setMaxSegmentsSaved(MAX_SEGMENTS)
			.setSegmentLength(2);//in seconds

	//-------------------------------------------------------------------------
	//Private static variables
	//-------------------------------------------------------------------------
	private static ICCCleaner 					_cleaner;
	private static DisplayFrame 				_display;
	private static PerformanceLogger 			_logger;
	private static S3Uploader 					_s3;
	private static SharedQueue<String> 			_signalQueue;
	private static long 						_startTime;
	private static SharedQueue<VideoSegment> 	_videoStream;

	//-------------------------------------------------------------------------
	//Private variables
	//-------------------------------------------------------------------------
	private Mat 	_mat;
	private Point 	_timeStampLocation;

	//-------------------------------------------------------------------------
	//DVC
	//-------------------------------------------------------------------------
	public ICCRunner(){	
		super();
		_startTime = System.currentTimeMillis();
		_className = "ICC Runner";
		_mat = new Mat();
		_signalQueue = new SharedQueue<>(10);
		_videoStream = new SharedQueue<>(MAX_SEGMENTS + 1);
	}

	//-------------------------------------------------------------------------
	//Main
	//-------------------------------------------------------------------------
	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		ICCRunner iccr = new ICCRunner();
		String loggerFilename = "RunnerLog_COMP-" + _setup.getCompressionRatio()
		+ "_FPS-" + _setup.getFPS() + "SEC-" + _setup.getSegmentLength() + ".txt";
		String s3loggerFilename = "S3Log_COMP-" + _setup.getCompressionRatio()
		+ "_FPS-" + _setup.getFPS() + "SEC-" + _setup.getSegmentLength() + ".txt";
		
		if(PREVIEW)				initDisplay();
		if(FileData.ISLOGGING) 	initLogging(loggerFilename, s3loggerFilename);
		initUploader(s3loggerFilename);
		initCleaner();
		//prints message when S3 has been initialized
		System.out.println(_signalQueue.dequeue());

		sendSetupFile(_startTime);

		iccr.start();
	}

	//-------------------------------------------------------------------------
	//Run Method
	//-------------------------------------------------------------------------
	/**
	 * The main process utilized by the camera. Records and sends video until
	 * display window is exited.
	 */
	@Override
	public void run() {
		Runtime.getRuntime().addShutdownHook(new ICCRunnerShutdownHook(this));

		int frameCount = 0, oldestSegment = 0, currentSegment = 0;
		int segmentLength = (int)(_setup.getFPS() * _setup.getSegmentLength());
		boolean preloaded = false;
		double timeStarted;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ICCFrameWriter segmentWriter = new ICCFrameWriter(_mat, output);
		VideoCapture grabber = null;
		VideoSegment segment = new VideoSegment();
		VideoSegmentHeader header = new VideoSegmentHeader();

		segmentWriter.setFrames(segmentLength);

		try{
			grabber = _setup.getVideoCapture();
			_timeStampLocation = new Point(10, 20);
		}
		catch(Exception e){
			System.err.println("ERROR 100: Failed to open recording device");
			System.exit(-1);
		}

		header.setTimeStamp(System.currentTimeMillis());
		timeStarted = (header.getTimeStamp() - _startTime)/1000.0;
		
		//isDone becomes false when "end()" function is called
		while (!_isDone) {
			
			try {
				//capture and record video
				if (!grabber.read(_mat)) {
					Utility.pause(15);
					continue;
				}
				if(PREVIEW){
					Imgproc.cvtColor(_mat, _mat, Imgproc.COLOR_BGR2GRAY);
					Imgproc.putText(_mat, getTime(),
							_timeStampLocation,
							Core.FONT_HERSHEY_PLAIN, 1, new Scalar(0));
	
					_display.setCurrentFrame(this.getCurrentFrame());
				}
				segmentWriter.write();
				frameCount++;

				//loops until end of current video segment
				if(frameCount >= segmentLength){
					//set parameters instead of 'new' to avoid memory usage
					header.setFrameOrder(segmentWriter.getFrames());
					segment.setIndex(currentSegment);
					segment.setData(output.toByteArray());
					segment.setHeader(header);

					sendSegmentToS3(segment);
					
					if(FileData.ISLOGGING){
						logSegment(timeStarted);
					}

					if(preloaded){
						oldestSegment = ++oldestSegment % MAX_VIDEO_INDEX;
						deleteOldSegments(currentSegment);
					}
					else if(currentSegment == MAX_SEGMENTS){
						preloaded = true;
					}

					//start new recording
					currentSegment = incrementVideoSegment(currentSegment);
					segmentWriter.reset();
					frameCount = 0;
					
					header.setTimeStamp(System.currentTimeMillis());
					timeStarted = (header.getTimeStamp() - _startTime)/1000.0;
				}
				Utility.pause((long) (1000/_setup.getFPS()));
			}//end try
			catch (Exception e) {
				if(e.getMessage().equals("closed")){
					break;
				}
				e.printStackTrace();
			}
		}//end while
		closeEverything(grabber, segmentWriter);
		System.out.println("Runner successfully closed");
	}

	//-------------------------------------------------------------------------
	//Public methods
	//-------------------------------------------------------------------------
	/**
	 * Used to end current ICCRunner thread.
	 */
	public void end(){
		if(_isDone) return;
		_isDone = true;
		System.out.println("Attempting to close runner...");
	}

	//-------------------------------------------------------------------------
	//Private static methods
	//-------------------------------------------------------------------------	
	private static void initCleaner(){
		_cleaner = new ICCCleaner(_s3);
		_cleaner.start();
	}

	/**
	 * Initializes the display.
	 * @see DisplayFrame
	 */
	private static void initDisplay(){
		try{
			_display = new DisplayFrame("Instant Cloud Camera");
			_display.setVisible(true);
		} catch (Exception e) {
			System.err.println("ICCR: Failed to open display!");;
		}
	}
	
	/**
	 * Initializes the loggers.
	 * @see PerformanceLogger
	 */
	private static void initLogging(String loggerFilename, String s3loggerFilename){
		writeGNUPlotScript(FileData.GNU_RUNNER, loggerFilename, s3loggerFilename);
		
		try{
			_logger = new PerformanceLogger(loggerFilename, FileData.LOG_DIRECTORY);
		} catch (Exception e) {
			e.printStackTrace();
		}
		_logger.setStartTime(_startTime);
	}
	
	/**
	 * Initializes the Amazon S3.
	 * @see S3Uploader
	 */
	private static void initUploader(String s3loggerFilename){
		_s3 = new S3Uploader(_videoStream);
		_s3.setSignal(_signalQueue);
		
		_s3.start();
		
		if(!FileData.ISLOGGING) return;
		
		PerformanceLogger s3logger = null;
		
		try{
			s3logger = new PerformanceLogger(s3loggerFilename, FileData.LOG_DIRECTORY);
		} catch (Exception e) {
			e.printStackTrace();
		}
		_logger.setStartTime(_startTime);
		s3logger.setStartTime(_startTime);
		_s3.setLogger(s3logger);
	}
	
	/**
	 * Sends a setup file to Amazon S3. Saves file to disk, but does not impact
	 * performance since this happens prior to any recording.
	 * 
	 * @param time	The initial start time of the program (used to measure delay).
	 * @see S3Uploader, ICCSetup
	 */
	private static void sendSetupFile(long time){
		int frames = (int)(_setup.getFPS() * _setup.getSegmentLength());
//		int headerSize = (frames * Integer.BYTES) + Long.BYTES;
		int headerSize = (frames * 4) + 8;
		FileWriter fw = null;
		String filename = FileData.SETUP_FILE;
		try{
			StringBuilder sb = new StringBuilder();
			fw = new FileWriter(filename);
			sb.append(Long.toString(time) + "\n");
			sb.append(MAX_VIDEO_INDEX + " ");
			sb.append(MAX_SEGMENTS + "\n");
			sb.append(headerSize + "\n");
			sb.append(_setup.getCompressionRatio() + " ");
			sb.append(_setup.getFPS() + " ");
			sb.append(_setup.getSegmentLength() + "\n");
			fw.write(sb.toString());
			fw.close();
		} catch(IOException e){
			System.err.println("ICCR: Unable to create setup file!");
		}
		synchronized(_signalQueue){
			_signalQueue.enqueue(filename);
			try{
				_signalQueue.wait();				
			}catch(InterruptedException e){
				//empty
			}
		}
	}
	
	/**
	 * Writes data usable by Gnuplot for examining delay.
	 * 
	 * @param scriptfile 	The script runnable by gnuplot.
	 * @param runnerfile	The data gathered from the ICCRunner.
	 * @param s3file		The data gathered from the S3Uploader.
	 * @see GNUScriptWriter, GNUPlotObject, GNUScriptParameters
	 */
	private static void writeGNUPlotScript(String scriptfile, String runnerfile, String s3file){
		int col1[] = {1, 2};
		GNUPlotObject runnerPlot = new GNUPlotObject(new File(FileData.LOG_DIRECTORY + runnerfile).getAbsolutePath(), "Video recorded", col1);
		GNUPlotObject s3Plot = new GNUPlotObject(new File(FileData.LOG_DIRECTORY + s3file).getAbsolutePath(), "Stream to S3", col1);
		GNUScriptParameters params = new GNUScriptParameters("ICC");
		params.addElement("CompressionRatio(" + _setup.getCompressionRatio() + ")");
		params.addElement("FPS(" + _setup.getFPS() + ")");
		params.addElement("SegmentLength(" + _setup.getSegmentLength() + ")");
		params.addPlot(runnerPlot);
		params.addPlot(s3Plot);
		params.setLabelX("Time Running (sec)");
		params.setLabelY("Time to Record/Upload (sec)");
		
		try{
			GNUScriptWriter writer = new GNUScriptWriter(scriptfile, params);
			writer.write();
			writer.close();
		}catch(IOException e){
//			System.err.println(e);
			e.printStackTrace();
		}
	}

	//-------------------------------------------------------------------------
	//Private methods
	//-------------------------------------------------------------------------
	/**
	 * Closes all closeable objects used by the ICCRunner.
	 * 
	 * @param grabber			The VideoCapture used to open camera device.
	 * @param segmentWriter		The ICCFrameWriter used to write video.
	 */
	private void closeEverything(VideoCapture grabber, ICCFrameWriter segmentWriter){
		try{
			_cleaner.end();
			grabber.release();
			segmentWriter.close();
			
			if(!FileData.ISLOGGING) return;
			//upload log files to S3
			_logger.close();
			_signalQueue.enqueue(_logger.getFileName());
			_signalQueue.enqueue(_logger.getFilePath());
		
		} catch(Exception e){
			System.err.println(e);
			e.printStackTrace();
		}
	}
	
	/**
	 * Deletes oldest segment based on currentSegment.
	 * 
	 * @param currentSegment	The most current video segment recorded.
	 * @see ICCSetup.setMaxSegmentsSaved
	 */
	private void deleteOldSegments(int currentSegment){
		int deleteSegment = currentSegment - MAX_SEGMENTS;
		if(deleteSegment < 0){//when current video segment id starts back at 0
			deleteSegment += MAX_VIDEO_INDEX;
		}
		_cleaner.add(VideoSegment.toString(deleteSegment));
	}
	
	/**
	 * Grabs the image from the most current frame recorded.
	 * 
	 * @return the image used by DisplayFrame
	 * @throws NullPointerException
	 * 
	 * @see DisplayFrame
	 */
	private Image getCurrentFrame() throws NullPointerException {
		int w = _mat.cols(),
			h = _mat.rows();
		byte[] dat = new byte[w * h * _mat.channels()];

		BufferedImage img = new BufferedImage(w, h, 
				BufferedImage.TYPE_BYTE_GRAY);

		_mat.get(0, 0, dat);
		img.getRaster().setDataElements(0, 0, 
				_mat.cols(), _mat.rows(), dat);
		return img;
	}
	
	/**
	 * Retrieves the length of time between now and when the program first started.
	 * 
	 * @return The time passed since program began.
	 */
	private String getTime(){
		
		DecimalFormat formatter = new DecimalFormat("#.##");
		formatter.setRoundingMode(RoundingMode.CEILING);
		return formatter.format((double)(System.currentTimeMillis() - _startTime)/1000);
	}

	/**
	 * Increments the current video segment based on setup parameters.
	 * 
	 * @param videoSegment 		The most current video segment recorded.
	 * @return The next video segment index.
	 */
	private int incrementVideoSegment(int videoSegment){
		return ++videoSegment % MAX_VIDEO_INDEX;
	}

	/**
	 * Logs how long it takes to record a segment.
	 * @param timeStarted		When the video first began recording.
	 * @see getTime
	 */
	private void logSegment(double timeStarted){
		
		long currentTime = System.currentTimeMillis();
		double curRunTime = (currentTime - _startTime)/1000.0;
		double delay = curRunTime - timeStarted;
		
		try {
			_logger.logVideoTransfer(currentTime, delay);
			
		} catch (IOException e) {
			System.err.println("ICCR: Failed to log video segment");
			e.printStackTrace();
		}
	}

	/**
	 * Sends video segment to S3Uploader
	 * @param video		The video that will be uploaded to Amazon S3.
	 */
	private void sendSegmentToS3(VideoSegment video){
		_videoStream.enqueue(video);
	}
}

//-----------------------------------------------------------------------------
//Shutdown Hook
//-----------------------------------------------------------------------------
/**
 * Calls ICCRunner.end() when the class shuts down.
 */
class ICCRunnerShutdownHook extends Thread {
	private ICCRunner _iccr;

	public ICCRunnerShutdownHook(ICCRunner iccr){
		_iccr = iccr;
	}

	public void run(){
		if(!_iccr.isAlive()) return;
		_iccr.end();
		_iccr.interrupt();
		try {
			_iccr.join();
		} catch (InterruptedException e) {
			System.err.println("ICCRunner end interrupted!");
		}
	}
}