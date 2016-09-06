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
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;

import GNUPlot.GNUScriptParameters;
import GNUPlot.GNUScriptWriter;
import GNUPlot.PlotObject;

public class ICCRunner extends VideoSource {

	private final static int MAX_VIDEO_INDEX = 100;
	private final static int MAX_SEGMENTS = 5;
	
	private static ICCSetup _setup = new ICCSetup()
			.setCompressionRatio(0.5)
			.setDevice(0)
			.setFourCC("MJPG")
			.setFPS(10)
			.setMaxIndex(MAX_VIDEO_INDEX)
			.setMaxSegmentsSaved(MAX_SEGMENTS)
			.setPreload(5)
			.setSegmentLength(5);


	private static DisplayFrame _display;
	private static S3Uploader _s3;
	private static SharedQueue<VideoSegment> _videoStream;
	private static SharedQueue<byte[]> _indexStream;
	private static SharedQueue<String> _signalQueue;
	private static PerformanceLogger _logger;

	private Point _timeStampLocation;

	public ICCRunner(){	
		super();
		className = "ICC Runner";
		metadata = new ICCMetadata(MAX_SEGMENTS, MAX_VIDEO_INDEX);
		_videoStream = new SharedQueue<>(_setup.getMaxSegments() + 1);
		_indexStream = new SharedQueue<>(10);
		_signalQueue = new SharedQueue<>(100);
	}

	public static void main(String[] args) {
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

	@Override
	public void run() {
		Runtime.getRuntime().addShutdownHook(new ICCRunnerShutdownHook(this));

		int frameCount = 0, oldestSegment = 0, currentSegment = 0;
		int segmentLength = (int)(_setup.getFPS() * _setup.getSegmentLength());
		long delay = (long)(1000/_setup.getFPS());
		boolean preloaded = false,
				startDeleting = false;
		double timeStarted;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ICCFrameWriter segmentWriter = new ICCFrameWriter(mat, output);
		VideoCapture grabber = null;
		VideoSegment segment = null;
		metadata.setStartTime(_logger.getStartTime());
		segmentWriter.setFrames(segmentLength);

		try{
			grabber = _setup.getVideoCapture();
//			_timeStampLocation = new Point(_setup.getWidth()-10,_setup.getHeight()-20);
			_timeStampLocation = new Point(10, 20);
		}
		catch(Exception e){
			System.err.println("ERROR 100");
			System.exit(-1);
		}

		timeStarted = (double)((System.currentTimeMillis() - _logger.getTime())/1000);

		//isDone becomes false when "end()" function is called
		while (!isDone) {
			try {
				//capture and record video
				if (!grabber.read(mat)) {
					Utility.pause(15);
					continue;
				}
				Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);
				Imgproc.putText(mat, getTime(),
						_timeStampLocation,
						Core.FONT_HERSHEY_PLAIN, 1, new Scalar(255));

				_display.setCurrentFrame(this.getCurrentFrame());
				segmentWriter.write();
				frameCount++;

				//loops until...				
				//end of current video segment
				if(frameCount >= segmentLength){
					segment = new VideoSegment(currentSegment, segmentWriter.getFrames(),
							output.toByteArray());

					sendSegmentToS3(segment);
					logSegment(timeStarted);
					metadata.update(segment);
					updateIndex();

					//DEGUB
//					segmentWriter.exportToFile(currentSegment);
//					metadata.exportToFile();

					/*setup preloaded segments:
					 *first video segment stays zero until a certain number of rounds
					 *specified in _setup-Preload
					 */
					if(preloaded){
						oldestSegment = ++oldestSegment % _setup.getMaxSegments();
					}
					else if(currentSegment == _setup.getPreload()){
						preloaded = true;
					}

					/*delete old video segments:
					 *removes the nth video behind based on _setup-MaxSegmentsSaved
					 */
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
				Utility.pause(delay);//typically works better than not doing so, but the time
									//difference vs FPS and actual require further research
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

	public void deleteOldSegments(int currentSegment){
		int deleteSegment = currentSegment - _setup.getMaxSegmentsSaved();
		if(deleteSegment < 0){//when current video segment id starts back at 0
			deleteSegment += _setup.getMaxSegments();
		}
		ICCCleaner cleaner = new ICCCleaner(_s3, VideoSegment.toString(deleteSegment));
		cleaner.start();
	}

	// end() used to end thread
	public void end(){
		isDone = true;
		System.out.println("Attempting to close runner...");
	}

	private Image getCurrentFrame() throws NullPointerException {
		int w = mat.cols(),
			h = mat.rows();
		byte[] dat = new byte[w * h * mat.channels()];

		BufferedImage img = new BufferedImage(w, h, 
				BufferedImage.TYPE_BYTE_GRAY);

		mat.get(0, 0, dat);
		img.getRaster().setDataElements(0, 0, 
				mat.cols(), mat.rows(), dat);
		return img;
	}

	//-------------------------------------------------------------------------
	//Private static methods
	
	private static void initDisplay(){
		try{
			_display = new DisplayFrame("Instant Cloud Camera");
			_display.setVisible(true);
		} catch (Exception e) {
			System.err.println("ICCR: Failed to open display!");;
		}
	}
	
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
	
	private static void initUploader(){
		_s3 = new S3Uploader(_videoStream);
		_s3.setIndexStream(_indexStream);
		_s3.setSignal(_signalQueue);
		_s3.start();
	}
	
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
	//Private non-static methods
	
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
	
	private String getTime(){
		DecimalFormat formatter = new DecimalFormat("#.00");
		return formatter.format((System.currentTimeMillis() - _logger.getTime())/1000);
	}

	private int incrementVideoSegment(int videoSegment){
		return ++videoSegment % _setup.getMaxSegments();
	}

	//logs how long it takes to record a segment
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

	private void sendSegmentToS3(VideoSegment video){
//		System.out.println("ICCR: Finished writing: " + fout);
		_videoStream.enqueue(video);
	}

	private void updateIndex(){
		new Thread() {
			public void run(){
				byte[] data = metadata.toString().getBytes();
				_indexStream.enqueue(data);
			}
		}.start();
		System.out.println("Playlist ready to be written...");
	}
}

//-----------------------------------------------------------------------------
//Shutdown Hook

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