package videoSender;

import videoUtility.VideoSource;
import videoUtility.DisplayFrame;
import videoUtility.DisplayFrameShutdownHook;
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
import java.util.LinkedList;

import org.opencv.core.Core;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;

import GNUPlot.GNUScriptParameters;
import GNUPlot.GNUScriptWriter;
import GNUPlot.PlotObject;

public class ICCRunner extends VideoSource {

	private static ICCSetup _setup = new ICCSetup()
			.setCompressionRatio(0.1)
			.setFourCC("MJPG")
			.setFPS(20)
			.setMaxSegmentsSaved(10)
			.setPreload(5)
			.setSegmentLength(5);
	
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
	}

	public static void main(String[] args) {
//		/home/pi/Libraries/opencv-3.1.0/build/lib
//		System.out.println(System.getProperty("java.library.path"));
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        String loggerFilename = "RunnerLog_COMP-" + _setup.getCompressionRatio()
        	+ "_FPS-" + _setup.getFPS() + "SEC-" + _setup.getSegmentLength() + ".txt";
        String s3loggerFilename = "S3Log_COMP-" + _setup.getCompressionRatio()
        	+ "_FPS-" + _setup.getFPS() + "SEC-" + _setup.getSegmentLength() + ".txt";

        writeGNUPlotScript("script.p", loggerFilename, s3loggerFilename);

        ICCRunner iccr = new ICCRunner();
        PerformanceLogger s3logger = null;

		try{
			s3logger = new PerformanceLogger(s3loggerFilename);
			_logger = new PerformanceLogger(loggerFilename);
			_display = new DisplayFrame("Instant Cloud Camera", iccr);
			_display.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}

		_stream = new SharedQueue<>(_setup.getMaxSegments() + 1);
		_signalQueue = new SharedQueue<>(100);

		_s3 = new S3Uploader(_setup.BUCKETNAME, _stream, _setup.VIDEO_FOLDER);
		_s3.setIndexFile(_setup.INDEXFILE);
		_s3.setSignal(_signalQueue);
		
		Runtime.getRuntime().addShutdownHook(new DisplayFrameShutdownHook(_s3, iccr));

		_s3.start();
		
		System.out.println(_signalQueue.dequeue());//used to wait for s3 to load

		_logger.startTime();
		s3logger.setStartTime(_logger.getStartTime());
		_s3.setLogger(s3logger);

		sendSetupFile(_logger.getStartTime());
		
		iccr.start();

		try{
			_s3.join();
			iccr.join();
		} catch(InterruptedException e){
			System.err.println(e);
		}
	}

	@Override
	public void run() {
		VideoCapture grabber = null;
		VideoWriter recorder = null;
		
		try{//open video _stream and _logger
			_setup.initVideoCapture(0);
			grabber = _setup.getVideoCapture();
			recorder = _setup.getVideoWriter(0);
		}
		catch(Exception e){
			System.err.println(e);
			end();
			_s3.end();
			_display.end();
		}

		int frameCount = 0, oldestSegment = 0, currentSegment = 0;
		int segmentLength = (int)(_setup.getFPS() * _setup.getSegmentLength());
		boolean initiated = false,
			startDeleting = false;
		double timeStarted;
		String outputfilename = _setup.getFileName(currentSegment);
		
		try {
			_logger.log("#Video segment length: " + _setup.getSegmentLength() + " sec\n");
			_logger.log("#Compression ratio: " + _setup.getCompressionRatio() + "\n");
			_logger.log("#FPS(Frames Per Second): " + _setup.getFPS() + "\n");

			timeStarted = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
			//false when "end()" function is called
			while (!isDone) {
				//capture and record video
				if (grabber.read(mat)) {
					recorder.write(mat);
					frameCount++;
					//check end of current video segment
					if(frameCount >= segmentLength){
						sendSegmentToS3(recorder, outputfilename);
						//log time segment was recorded
						double curRunTime = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
						double value = curRunTime - timeStarted;
						_logger.logTime();
						_logger.log(" ");
						_logger.log(value);
						_logger.log("\n");

						timeStarted = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
						
						//setup preloaded segments
						if(initiated){
							oldestSegment = ++oldestSegment % _setup.getMaxSegments();
						}
						else if(currentSegment == _setup.getPreload()){
							initiated = true;
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
				}
				Utility.pause(45);
			}
			_logger.close();
			grabber.release();
			recorder.release();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Runner successfully closed");
	}

	public void deleteOldSegments(int currentSegment){
		int deleteSegment = currentSegment - _setup.getMaxSegmentsSaved();
		if(deleteSegment < 0){//when current video segment id starts back at 0
			deleteSegment += _setup.getMaxSegments();
		}
		String toDelete = _setup.getFileName(deleteSegment);
		ICCCleaner cleaner = new ICCCleaner(_s3, toDelete, _setup.VIDEO_FOLDER);
		cleaner.start();
	}
	
	/*
	 * end() used to end the current video _stream
	 * - also used by DisplayFrameShutDownHook
	 */
	public void end(){
		isDone = true;
		System.out.println("Attempting to close runner...");
	}
	
	/*
	 * getCurrentFrame() used by FrameDisplay to get current video image
	 */
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

	private void incrementSegmentCollection(int currentSegment){
		_segmentCollection.addFirst(new VideoObject(Integer.toString(currentSegment)));
		if(_segmentCollection.size() > _setup.getMaxSegmentsSaved()){
			_segmentCollection.removeLast();
		}
	}

	private int incrementVideoSegment(int videoSegment){
		return ++videoSegment % _setup.getMaxSegments();
	}

	//Closes recording _stream and places filename in the queue for s3
	private void sendSegmentToS3(VideoWriter recorder, String outputfilename){
		recorder.release();
		System.out.println("Finished writing: " + outputfilename);
		_stream.enqueue(outputfilename);
	}

	private static void sendSetupFile(BigDecimal time){
		FileWriter fw = null;
		try{
			fw = new FileWriter(_setupFileName);
//			SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");    
//			Date resultdate = new Date(time);
//			fw.write(sdf.format(resultdate) + "\n");
			fw.write(time.toString());
			fw.close();
		} catch(IOException e){
			System.err.println("Unable to create setup file!");
		}
		_signalQueue.enqueue(_setupFileName);
	}

	private static void writeGNUPlotScript(String scriptfile, String runnerfile, String s3file){
		int col1[] = {1, 2};
		PlotObject runnerPlot = new PlotObject(new File(runnerfile).getAbsolutePath(), "Video recorded", col1);
		PlotObject s3Plot = new PlotObject(new File(s3file).getAbsolutePath(), "Stream to S3", col1);
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

	private void writePlaylist(int oldestSegment, int currentSegment){
		IndexWriter iwriter = new IndexWriter(_stream, oldestSegment,
				currentSegment, _setup.getMaxSegments(), _setup.VIDEO_FOLDER, _setup.INDEXFILE);
		iwriter.setCollection(_segmentCollection);
		iwriter.setTime(_logger.getTime());
		Thread ithread = new Thread(iwriter);

		ithread.start();
	}
}
