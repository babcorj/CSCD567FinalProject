package videoSender;

import videoUtility.VideoSource;
import videoUtility.DisplayFrame;
import videoUtility.PerformanceLogger;
import videoUtility.SharedQueue;
import videoUtility.Utility;
import videoUtility.VideoObject;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.LinkedList;

import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.imgproc.Imgproc;

import GNUPlot.GNUScriptParameters;
import GNUPlot.GNUScriptWriter;
import GNUPlot.PlotObject;

public class ICCRunner extends VideoSource {

	private static ICCSetup _setup = new ICCSetup()
			.setCompressionRatio(0.25)
			.setDevice(0)
			.setFourCC("MJPG")
			.setFPS(10)
			.setMaxSegmentsSaved(10)
			.setPreload(5)
			.setSegmentLength(5);

	private final static String _logDirectory = "log/";
	private final static String _setupFileName = "setup.txt";
	private static DisplayFrame _display;
	private static S3Uploader _s3;
	private static SharedQueue<String> _stream;
	private static SharedQueue<String> _signalQueue;
	private static PerformanceLogger _logger;
	private LinkedList<VideoObject> _segmentCollection = new LinkedList<>();

	public ICCRunner(){	
		super();
		className = "ICC Runner";
		_stream = new SharedQueue<>(_setup.getMaxSegments() + 1);
		_signalQueue = new SharedQueue<>(100);
	}

	public static void main(String[] args) {
//		Djava.library.path=/home/pi/Libraries/opencv-3.1.0/build/lib
//		System.out.println(System.getProperty("java.library.path"));
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		ICCRunner iccr = new ICCRunner();

		initDisplay(iccr);
		initUploader();
		initLogging();

		System.out.println(_signalQueue.dequeue());//wait for s3 to load

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
		String outputfilename = _setup.getFileName(currentSegment);		
		VideoCapture grabber = null;
		VideoWriter recorder = null;

		try{
			grabber = _setup.getVideoCapture();
			recorder = _setup.getVideoWriter(0);
		}
		catch(Exception e){
			System.err.println(e);
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
				Imgproc.putText(mat, getTime(),
						new Point(mat.cols()-30,mat.rows()-12),
						Core.FONT_HERSHEY_PLAIN, 0.5, new Scalar(255));
				recorder.write(mat);
				frameCount++;

				//check end of current video segment
				if(frameCount >= segmentLength){
					sendSegmentToS3(recorder, outputfilename);
					logSegment(timeStarted);

					timeStarted = (double)((System.currentTimeMillis() - _logger.getTime())/1000);

					//setup preloaded segments
					if(preloaded){
						oldestSegment = ++oldestSegment % _setup.getMaxSegments();
					}
					else if(currentSegment == _setup.getPreload()){
						preloaded = true;
					}

					//write to index file
					incrementSegmentCollection(currentSegment);
					writePlaylist(oldestSegment, currentSegment);

					//delete old video segments
					if(startDeleting){
						deleteOldSegments(currentSegment);
					}
					else if(currentSegment == _setup.getMaxSegmentsSaved()-1){
						startDeleting = true;
					}

					//start new recording
					currentSegment = incrementVideoSegment(currentSegment);
					outputfilename = _setup.getFileName(currentSegment);
					recorder = _setup.getVideoWriter(currentSegment);
					//reset frame count
					frameCount = 0;
				}
				Utility.pause(delay);

			}//end try
			catch (Exception e) {
				e.printStackTrace();
			}
		}//end while
		closeEverything(grabber, recorder);
		System.out.println("Runner successfully closed");
	}

	//-------------------------------------------------------------------------
	//Public methods

	public void deleteOldSegments(int currentSegment){
		int deleteSegment = currentSegment - _setup.getMaxSegmentsSaved();
		if(deleteSegment < 0){//when current video segment id starts back at 0
			deleteSegment += _setup.getMaxSegments();
		}
		String toDelete = _setup.getFileName(deleteSegment);
		ICCCleaner cleaner = new ICCCleaner(_s3, toDelete, _setup.VIDEO_FOLDER);
		cleaner.start();
	}
	
	// end() used to end thread
	public void end(){
		isDone = true;
		System.out.println("Attempting to close runner...");
	}
	
	// used by FrameDisplay to get current video image
	public Image getCurrentFrame() throws NullPointerException {
		int w = mat.cols(),
				h = mat.rows();
		byte[] dat = new byte[w * h * mat.channels()];
		
		BufferedImage img = new BufferedImage(w, h, 
				BufferedImage.TYPE_3BYTE_BGR);
		
		mat.get(0, 0, dat);
		img.getRaster().setDataElements(0, 0, 
				mat.cols(), mat.rows(), dat);
		return img;
	}
	
	//-------------------------------------------------------------------------
	//Private static methods
	
	private static void initDisplay(ICCRunner iccr){
		try{
			_display = new DisplayFrame("Instant Cloud Camera", iccr);
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
			s3logger = new PerformanceLogger(s3loggerFilename, _logDirectory);
			_logger = new PerformanceLogger(loggerFilename, _logDirectory);
		} catch (Exception e) {
			e.printStackTrace();
		}
		_logger.startTime();
		s3logger.setStartTime(_logger.getStartTime());
		_s3.setLogger(s3logger);
	}
	
	private static void initUploader(){
		_s3 = new S3Uploader(_setup.BUCKETNAME, _stream, _setup.VIDEO_FOLDER);
		_s3.setIndexFile(_setup.INDEXFILE);
		_s3.setSignal(_signalQueue);
		_s3.start();
	}
	
	private static void sendSetupFile(BigDecimal time){
		FileWriter fw = null;
		try{
			fw = new FileWriter(_setupFileName);
			fw.write(time.toString() + "\n");
			fw.write(_setup.getCompressionRatio() + " ");
			fw.write(_setup.getFPS() + " ");
			fw.write(_setup.getSegmentLength() + "\n");
			fw.close();
		} catch(IOException e){
			System.err.println("ICCR: Unable to create setup file!");
		}
		_signalQueue.enqueue(_setupFileName);
		_s3.interrupt();//s3 is waiting for setup file...
	}
	
	private static void writeGNUPlotScript(String scriptfile, String runnerfile, String s3file){
		int col1[] = {1, 2};
		PlotObject runnerPlot = new PlotObject(new File(_logDirectory + runnerfile).getAbsolutePath(), "Video recorded", col1);
		PlotObject s3Plot = new PlotObject(new File(_logDirectory + s3file).getAbsolutePath(), "Stream to S3", col1);
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
	
	private void closeEverything(VideoCapture grabber, VideoWriter recorder){
		try{
			_logger.close();
			_signalQueue.enqueue(_logger.getFileName());
			_signalQueue.enqueue(_logger.getFilePath());
			grabber.release();
			recorder.release();
		} catch(Exception e){
			System.err.println(e);
		}
	}
	
	private String getTime(){
		DecimalFormat formatter = new DecimalFormat("#.00");
		return formatter.format((System.currentTimeMillis() - _logger.getTime())/1000);
	}
	
	private void incrementSegmentCollection(int currentSegment){
		_segmentCollection.addFirst(new VideoObject(Integer.toString(currentSegment)));
		if(_segmentCollection.size() > _setup.getMaxSegmentsSaved()){
			_segmentCollection.removeLast();
		}
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

	//Closes recording _stream and places filename in the queue for s3
	private void sendSegmentToS3(VideoWriter recorder, String outputfilename){
		recorder.release();
		System.out.println("ICCR: Finished writing: " + outputfilename);
		_stream.enqueue(outputfilename);
	}

	private void writePlaylist(int oldestSegment, int currentSegment){
		IndexWriter iwriter = new IndexWriter(_stream, oldestSegment,
				currentSegment, _setup.getMaxSegments(), _setup.VIDEO_FOLDER, _setup.INDEXFILE);
		iwriter.setCollection(_segmentCollection);
		iwriter.setTime(_logger.getTime());
		Thread ithread = new Thread(iwriter);

		ithread.start();
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