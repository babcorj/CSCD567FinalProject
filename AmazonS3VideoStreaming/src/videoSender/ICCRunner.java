package videoSender;

import videoUtility.VideoSource;
import videoUtility.DisplayFrame;
import videoUtility.DisplayFrameShutdownHook;
import videoUtility.PerformanceLogger;
import videoUtility.SharedQueue;
import videoUtility.Utility;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.opencv.core.Core;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;

public class ICCRunner extends VideoSource {

	private static ICCSetup _setup = new ICCSetup()
			.setCompressionRatio(0.1)
			.setFourCC("MJPG")
			.setFPS(20)
			.setMaxSegmentsSaved(10)
			.setPreload(5)
			.setSegmentLength(2);
	
	private static DisplayFrame _display;
	private static S3Uploader _s3;
	private static SharedQueue<String> _stream;
	private static PerformanceLogger _logger;

	public ICCRunner(){	
		super();
		className = "ICC Runner";
	}

	public static void main(String[] args) {
		System.out.println(System.getProperty("java.library.path"));
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		ICCRunner iccr = new ICCRunner();
		
		try{
			_logger = new PerformanceLogger("ICC_Performance.txt");
			_display = new DisplayFrame("Instant Cloud Camera", iccr);
			_display.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		_stream = new SharedQueue<>(_setup.getMaxSegments() + 1);
		
		_s3 = new S3Uploader(_setup.BUCKETNAME, _stream, _setup.VIDEO_FOLDER);
		_s3.setIndexFile(_setup.INDEXFILE);
		_s3.setLogger(_logger);
		
		Runtime.getRuntime().addShutdownHook(new DisplayFrameShutdownHook(_s3, iccr));

		_s3.start();
		iccr.start();

		try{
			_s3.join();
			iccr.join();
		} catch(InterruptedException e){
			System.err.println("There was an error with _s3 or ICCR");
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
		String outputfilename = _setup.getFileName(currentSegment);

		_logger.startTime();

		try {
			_logger.log("#Video segment length: " + _setup.getSegmentLength() + " sec\n");
			_logger.log("#Compression ratio: " + _setup.getCompressionRatio());
			//false when "end()" function is called
			while (!isDone) {
				//capture and record video
				if (grabber.read(mat)) {
					recorder.write(mat);
					frameCount++;

					//check end of current video segment
					if(frameCount >= segmentLength){
						sendSegmentToS3(recorder, outputfilename);

//						_logger.logTime();
//						_logger.log("\n");

						//setup preloaded segments
						if(initiated){
							oldestSegment = ++oldestSegment % _setup.getMaxSegments();
						}
						else if(currentSegment == _setup.getPreload()){
							initiated = true;
						}
						
						//write to index file
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
	
	private int incrementVideoSegment(int videoSegment){
		return ++videoSegment % _setup.getMaxSegments();
	}

	//Closes recording _stream and places filename in the queue for s3
	private void sendSegmentToS3(VideoWriter recorder, String outputfilename){
		recorder.release();
		System.out.println("Finished writing: " + outputfilename);
		_stream.enqueue(outputfilename);
	}
	
	private void writePlaylist(int oldestSegment, int currentSegment){
		IndexWriter iwriter = new IndexWriter(_stream, oldestSegment,
				currentSegment, _setup.getMaxSegments(), _setup.VIDEO_FOLDER, _setup.INDEXFILE);
		Thread ithread = new Thread(iwriter);

		ithread.start();
	}
}
