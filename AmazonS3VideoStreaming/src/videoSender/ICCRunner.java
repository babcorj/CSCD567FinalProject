package videoSender;

import videoUtility.VideoSource;
import videoUtility.DisplayFrame;
import videoUtility.FileData;
import videoUtility.ICCMetadata;
import videoUtility.ICCFrameWriter;
import videoUtility.PerformanceLogger;
import videoUtility.SharedQueue;
import videoUtility.Utility;
import videoUtility.VideoSegment;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;

import GNUPlot.GNUScriptParameters;
import GNUPlot.GNUScriptWriter;
import GNUPlot.PlotObject;

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
 * This version uses a playlist to save all necessary information about the
 * current videos being saved to Amazon S3. The benefit is that the 
 * VideoPlayer knows which video is most current, how many videos there are,
 * and where each frame of each video is located based on an index value. The
 * drawback is that the playlist has to be sent after each video is loaded,
 * which essentially doubles the cost of using Amazon S3.
 *
 * @version v.0.0.20
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
	private final static int MAX_VIDEO_INDEX = 100;
	private final static int MAX_SEGMENTS = 10;
	
	/*
	 * ICCSetup is used to configure the video recorder settings
	 */
	private static ICCSetup _setup = new ICCSetup()
			.setCompressionRatio(0.4)
			.setDevice(0)
			.setFourCC("MJPG")
			.setFPS(6)
			.setMaxIndex(MAX_VIDEO_INDEX)
			.setMaxSegmentsSaved(MAX_SEGMENTS)
			.setPreload(5)
			.setSegmentLength(5);

	//-------------------------------------------------------------------------
	//Private static variables
	//-------------------------------------------------------------------------
	private static DisplayFrame _display;
	private static S3Uploader _s3;
	private static SharedQueue<VideoSegment> _videoStream;
	private static SharedQueue<byte[]> _indexStream;
	private static SharedQueue<String> _signalQueue;
	private static PerformanceLogger _logger;

	//-------------------------------------------------------------------------
	//Private variables
	//-------------------------------------------------------------------------
	private Mat _mat;
	private Point _timeStampLocation;

	//-------------------------------------------------------------------------
	//DVC
	//-------------------------------------------------------------------------
	public ICCRunner(){	
		super();
		_className = "ICC Runner";
		_metadata = new ICCMetadata(_setup.getPreload(), MAX_VIDEO_INDEX);
		_videoStream = new SharedQueue<>(_setup.getMaxSegments() + 1);
		_indexStream = new SharedQueue<>(10);
		_signalQueue = new SharedQueue<>(100);
	}

	//-------------------------------------------------------------------------
	//Main
	//-------------------------------------------------------------------------
	public static void main(String[] args) {
		/* Used in Run configuration settings */
//		Djava.library.path=/home/pi/Libraries/opencv-3.1.0/build/lib
//		System.out.println(System.getProperty("java.library.path"));
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		ICCRunner iccr = new ICCRunner();

		initDisplay();
		initUploader();
		initLogging();

		//prints message when S3 has been initialized
		System.out.println(_signalQueue.dequeue());

		sendSetupFile(_logger.getStartTime());

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
		boolean preloaded = false,
				startDeleting = false;
		double timeStarted;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ICCFrameWriter segmentWriter = new ICCFrameWriter(_mat, output);
		VideoCapture grabber = null;
		VideoSegment segment = null;
		
		_metadata.setStartTime(_logger.getStartTime());
		segmentWriter.setFrames(segmentLength);

		try{
			grabber = _setup.getVideoCapture();
			_timeStampLocation = new Point(10, 20);
		}
		catch(Exception e){
			System.err.println("ERROR 100");
			System.exit(-1);
		}

		timeStarted = (double)((System.currentTimeMillis() - _logger.getTime())/1000);

		//isDone becomes false when "end()" function is called
		while (!_isDone) {
			try {
				//capture and record video
				if (!grabber.read(_mat)) {
					Utility.pause(15);
					continue;
				}
				Imgproc.cvtColor(_mat, _mat, Imgproc.COLOR_BGR2GRAY);
				Imgproc.putText(_mat, getTime(),
						_timeStampLocation,
						Core.FONT_HERSHEY_PLAIN, 1, new Scalar(255));

				_display.setCurrentFrame(this.getCurrentFrame());
				segmentWriter.write();
				frameCount++;

				//loops until end of current video segment
				if(frameCount >= segmentLength){
					segment = new VideoSegment(currentSegment, segmentWriter.getFrames(),
							output.toByteArray());

					sendSegmentToS3(segment);
					logSegment(timeStarted);
					_metadata.update(segment);
					updateIndex();

					/* setup preloaded segments:
					 * earliest video segment # stays zero until a certain
					 * number of rounds specified in _setup._preloadSegments */
					if(preloaded){
						oldestSegment = ++oldestSegment % _setup.getMaxSegments();
					}
					else if(currentSegment == _setup.getPreload()){
						preloaded = true;
					}

					/* delete old video segments:
					 * removes the nth video behind based on _setup._maxSegmentsSaved */
					if(startDeleting){
						deleteOldSegments(currentSegment);
					}
					else if(currentSegment == _setup.getMaxSegmentsSaved()-1){
						startDeleting = true;
					}

					//start new recording
					currentSegment = incrementVideoSegment(currentSegment);
					segmentWriter.reset();
					frameCount = 0;
					timeStarted = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
				}
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
	 * Deletes oldest segment based on currentSegment.
	 * 
	 * @param currentSegment	The most current video segment recorded.
	 * @see ICCSetup.setMaxSegmentsSaved
	 */
	public void deleteOldSegments(int currentSegment){
		int deleteSegment = currentSegment - _setup.getMaxSegmentsSaved();
		if(deleteSegment < 0){//when current video segment id starts back at 0
			deleteSegment += _setup.getMaxSegments();
		}
		ICCCleaner cleaner = new ICCCleaner(_s3, VideoSegment.toString(deleteSegment));
		cleaner.start();
	}

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
	private static void initLogging(){
		String loggerFilename = "RunnerLog_COMP-" + _setup.getCompressionRatio()
		+ "_FPS-" + _setup.getFPS() + "SEC-" + _setup.getSegmentLength() + ".txt";
		String s3loggerFilename = "S3Log_COMP-" + _setup.getCompressionRatio()
		+ "_FPS-" + _setup.getFPS() + "SEC-" + _setup.getSegmentLength() + ".txt";
		
		writeGNUPlotScript("script.p", loggerFilename, s3loggerFilename);
		
		PerformanceLogger s3logger = null;
		
		try{
			s3logger = new PerformanceLogger(s3loggerFilename, FileData.LOG_DIRECTORY.print());
			_logger = new PerformanceLogger(loggerFilename, FileData.LOG_DIRECTORY.print());
		} catch (Exception e) {
			e.printStackTrace();
		}
		_logger.startTime();
		s3logger.setStartTime(_logger.getStartTime());
		_s3.setLogger(s3logger);
	}
	
	/**
	 * Initializes the Amazon S3.
	 * @see S3Uploader
	 */
	private static void initUploader(){
		_s3 = new S3Uploader(_videoStream);
		_s3.setIndexStream(_indexStream);
		_s3.setSignal(_signalQueue);
		_s3.start();
	}
	
	/**
	 * Sends a setup file to Amazon S3. Saves file to disk, but does not impact
	 * performance since this happens prior to any recording.
	 * 
	 * @param time	The initial start time of the program (used to measure delay).
	 * @see S3Uploader, ICCSetup
	 */
	private static void sendSetupFile(long time){
		FileWriter fw = null;
		String filename = FileData.SETUP_FILE.print();
		try{
			fw = new FileWriter(filename);
			fw.write(Long.toString(time) + "\n");
			fw.write(_setup.getCompressionRatio() + " ");
			fw.write(_setup.getFPS() + " ");
			fw.write(_setup.getSegmentLength() + "\n");
			fw.close();
		} catch(IOException e){
			System.err.println("ICCR: Unable to create setup file!");
		}
		_signalQueue.enqueue(filename);
		_s3.interrupt();//s3 is waiting for setup file...
	}
	
	/**
	 * Writes data usable by Gnuplot for examining delay.
	 * 
	 * @param scriptfile 	The script runnable by gnuplot.
	 * @param runnerfile	The data gathered from the ICCRunner.
	 * @param s3file		The data gathered from the S3Uploader.
	 * @see GNUScriptWriter, PlotObject, GNUScriptParameters
	 */
	private static void writeGNUPlotScript(String scriptfile, String runnerfile, String s3file){
		int col1[] = {1, 2};
		PlotObject runnerPlot = new PlotObject(new File(FileData.LOG_DIRECTORY.print() + runnerfile).getAbsolutePath(), "Video recorded", col1);
		PlotObject s3Plot = new PlotObject(new File(FileData.LOG_DIRECTORY.print() + s3file).getAbsolutePath(), "Stream to S3", col1);
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
			System.err.println(e);
		}
	}

	//-------------------------------------------------------------------------
	//Private methods
	//-------------------------------------------------------------------------
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
	 * Closes all closeable objects used by the ICCRunner.
	 * 
	 * @param grabber			The VideoCapture used to open camera device.
	 * @param segmentWriter		The ICCFrameWriter used to write video.
	 */
	private void closeEverything(VideoCapture grabber, ICCFrameWriter segmentWriter){
		try{
			_logger.close();
			_signalQueue.enqueue(_logger.getFileName());
			_signalQueue.enqueue(_logger.getFilePath());
			grabber.release();
			segmentWriter.close();
		} catch(Exception e){
			System.err.println(e);
		}
	}
	
	/**
	 * Retrieves the length of time between now and when the program first started.
	 * 
	 * @return The time passed since program began.
	 */
	private String getTime(){
		DecimalFormat formatter = new DecimalFormat("#.00");
		return formatter.format((System.currentTimeMillis() - _logger.getTime())/1000);
	}

	/**
	 * Increments the current video segment based on setup parameters.
	 * 
	 * @param videoSegment 		The most current video segment recorded.
	 * @return The next video segment index.
	 */
	private int incrementVideoSegment(int videoSegment){
		return ++videoSegment % _setup.getMaxSegments();
	}

	/**
	 * Logs how long it takes to record a segment.
	 * @param timeStarted		When the video first began recording.
	 * @see getTime
	 */
	private void logSegment(double timeStarted){
		double curRunTime = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
		double value = curRunTime - timeStarted;
		_logger.logTime();
		try {
			_logger.log(" ");
			_logger.log(value);
			_logger.log("\n");
		} catch (IOException e) {
			System.err.println("ICCR: Failed to log video segment");
		}
	}

	/**
	 * Sends video segment to S3Uploader
	 * @param video		The video that will be uploaded to Amazon S3.
	 */
	private void sendSegmentToS3(VideoSegment video){
		_videoStream.enqueue(video);
	}

	/**
	 * Sends playlist to S3Uploader.
	 */
	private void updateIndex(){
		new Thread() {
			public void run(){
				byte[] data = _metadata.toString().getBytes();
				_indexStream.enqueue(data);
			}
		}.start();
		System.out.println("Playlist ready to be written...");
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
		_iccr.end();
		try {
			_iccr.join();
		} catch (InterruptedException e) {
			System.err.println("ICCRunner end interrupted!");
		}
	}
}