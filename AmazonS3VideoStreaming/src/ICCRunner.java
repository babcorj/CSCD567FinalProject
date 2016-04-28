/* Look at:
 * http://stackoverflow.com/questions/6546193/how-to-catch-an-exception-from-a-thread
 * 
 * For s3 shutdown error checking from main thread
 */
import java.awt.Image;
import java.awt.image.BufferedImage;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.opencv.core.Size;

public class ICCRunner extends Thread {
	private final static int MAX_SEGMENTS = 100; //in reference to naming
	private final static String BUCKETNAME = "icc-videostream-00";
	private final static String VIDEO_FOLDER = "./videos/";

	private final int PRELOADSEGMENTS = 5; // diff of min v. max in index file
	private final int DELETESEGMENTS = 10; // delete x frames behind
	private final double FPS = 20;
	private final double SEGMENT_VIDEOLENGTH = 8; //seconds
	private final FourCC fourCC = new FourCC("MJPG");
	private final String PREFIX = "myvideo";
	private final String FILE_EXT = ".avi";
	private final String INDEXFILE = "StreamIndex.txt";

	private static S3Uploader s3;
	private static SharedQueue<String> que;
	
	private boolean isDone = false;
	private Mat mat = new Mat();
	private Mat grayMat = null;
	
	//VISUAL SETTINGS
	private final boolean HASCOLOR = true;

	@Override
	public void run() {
		int width = 0,
			height = 0,
			compressionRatio = 10;
		String outputFileName = PREFIX + 0 + FILE_EXT;
		VideoCapture video = null;
		Size frameSize = null;
		VideoWriter recorder = null;
		IndexWriter iwriter = null;

		try{
			//open video stream
			video = new VideoCapture(0);
			video.set(Videoio.CAP_PROP_FPS, FPS);
			width = (int) video.get(Videoio.CV_CAP_PROP_FRAME_WIDTH)/compressionRatio;
			height = (int) video.get(Videoio.CV_CAP_PROP_FRAME_HEIGHT)/compressionRatio;
			video.set(Videoio.CV_CAP_PROP_FRAME_WIDTH, width);
			video.set(Videoio.CV_CAP_PROP_FRAME_HEIGHT, height);

			if(!HASCOLOR) {
//				video.set(Videoio.CAP_OPENNI_GRAY_IMAGE, 1);
//				video.set(Videoio.CAP_MODE_GRAY, 1);
//				video.set(Videoio.CAP_PROP_CONVERT_RGB, 1);
//				video.set(Videoio.CAP_PROP_MODE, 2);
			}
			
			if(!video.isOpened()){
				throw new Exception("Failed to open video stream");
			}
			
			//define size based on video stream
//			height = (int) video.get(Videoio.CAP_PROP_FRAME_HEIGHT);
//			width = (int) video.get(Videoio.CAP_PROP_FRAME_WIDTH);
			frameSize = new Size(width,height);
			
			//open video recorder
			recorder = new VideoWriter(VIDEO_FOLDER + outputFileName,
					fourCC.toInt(), FPS, frameSize, HASCOLOR);

			if(!recorder.isOpened()){
				throw new Exception("Failed to open recorder");
			}
		}
		catch(Exception e){
			e.printStackTrace();
			s3.end();
			end();
		}
		try {
			int frameCount = 0,
				oldestSegment = 0,
				currentSegment = 0,
				deleteSegment = 0,
				segmentLength = (int)(FPS * SEGMENT_VIDEOLENGTH);
			boolean initiated = false,
					startDeleting = false;
			String toDelete = "";
			Thread ithread;
			ICCCleaner cleaner;
			double startTime = System.currentTimeMillis();

			//false when "end()" function is called
			while (!isDone) {
				if (video.read(mat)) {//reads in video
					System.out.printf("%.2f\n", ( System.currentTimeMillis() - startTime )/1000 );
					//record image
//					grayMat.convertTo(mat, 1);
//					recorder.write(grayMat);
					recorder.write(mat);
					frameCount++;

					//check end of current video segment
					if(frameCount >= segmentLength){
						recorder.release();
						System.out.println("Finished writing: " + outputFileName);
						que.enqueue(outputFileName);

						//upload the index file
						if(initiated){
							oldestSegment = ++oldestSegment % MAX_SEGMENTS;
						}
						else if(currentSegment == PRELOADSEGMENTS){
							initiated = true;
						}
						iwriter = new IndexWriter(que, oldestSegment,
								currentSegment, MAX_SEGMENTS, INDEXFILE);
						ithread = new Thread(iwriter);
						ithread.start();

						//delete old video segments
						if(startDeleting){
							deleteSegment = currentSegment - DELETESEGMENTS -1;
							if(deleteSegment < 0){//when current video segment id starts back at 0
								deleteSegment += MAX_SEGMENTS;
							}
							toDelete = PREFIX + deleteSegment + FILE_EXT;
							cleaner = new ICCCleaner(s3, toDelete, VIDEO_FOLDER);
							cleaner.start();
						}
						else if(currentSegment == DELETESEGMENTS){
							startDeleting = true;
						}

						//start new recording
						outputFileName = PREFIX + (++currentSegment) + FILE_EXT;
						recorder = new VideoWriter();
						recorder.open(VIDEO_FOLDER + outputFileName,
								fourCC.toInt(), FPS, frameSize, HASCOLOR);
						currentSegment = currentSegment % MAX_SEGMENTS;
						
						//reset frame count
						frameCount = 0;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		video.release();
		recorder.release();
		System.out.println("Runner successfully closed");
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

	/*
	 * end() used to end the current video stream
	 * - also used by ICCRunnerShutDownHook
	 */
	public void end(){
		isDone = true;
		System.out.println("Attempting to close runner...");
	}

	public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		ICCRunner iccr = new ICCRunner();
		DisplayFrame display;
		try{
			display = new DisplayFrame("Instant Cloud Camera", iccr);
			display.setVisible(true);	
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		que = new SharedQueue<>(MAX_SEGMENTS + 1);
		s3 = new S3Uploader(BUCKETNAME, que, VIDEO_FOLDER);
		Runtime.getRuntime().addShutdownHook(new ICCRunnerShutdownHook(s3, iccr));
		s3.start();
		que.enqueue("StreamIndex.txt"); //test
		iccr.start();
	}
}
