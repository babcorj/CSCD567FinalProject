
import java.awt.image.BufferedImage;

import org.bytedeco.javacv.CanvasFrame;
import org.opencv.core.Core;
import org.opencv.core.Mat;
//import org.opencv.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.opencv.core.Size;

public class ICCRunner extends Thread {
	private final FourCC fourCC = new FourCC("MJPG");
	private final String PREFIX = "myvideo";
	private final String FILE_EXT = ".avi";
	private final String INDEXFILE = "StreamIndex.txt";
	private final static String BUCKETNAME = "icc-videostream-00";
	private final static int MAX_SEGMENTS = 100;
	private final double SEGMENT_VIDEOLENGTH = 8; //seconds
	private final int PRELOADSEGMENTS = 5; // diff of min v. max in index file
	private final int DELETESEGMENTS = 10; // delete x frames behind
	private final double FPS = 10;
	private CanvasFrame canvas = new CanvasFrame("Instant Cloud Camera");
	private static S3Uploader s3;
	private static SharedQueue<String> que;
	private boolean isDone = false;
	
	//VISUAL SETTINGS
	private final boolean HASCOLOR = true;
	private final boolean RAINBOW = false;
//	private final ICCEditor.Color myColor = ICCEditor.Color.BLUE;
	private final ICCEditor.Color myColor = null;
	
	public ICCRunner() {
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
	}
	@Override
	public void run() {
		int segmentNum = 0,
			width = 0,
			height = 0,
			oldestSegment = 0;
		String outputFileName = PREFIX + segmentNum + FILE_EXT;
		VideoCapture video = null;
		Size frameSize = null;
		VideoWriter recorder = null;
		IndexWriter iwriter = null;

		try{
			video = new VideoCapture();
			video.open(0);
			video.set(Videoio.CAP_PROP_FPS, FPS);
			
			if(!video.isOpened()){
				System.err.println("Failed to open video stream");
			}
			
			System.out.println("FPS: " + video.get(Videoio.CAP_PROP_FPS));
			System.out.println("Width: " + video.get(Videoio.CAP_PROP_FRAME_WIDTH));
			height = (int) video.get(Videoio.CAP_PROP_FRAME_HEIGHT);
			width = (int) video.get(Videoio.CAP_PROP_FRAME_WIDTH);
//			int fourCC = (int) video.get(Videoio.CAP_PROP_FOURCC);
			frameSize = new Size(width,height);
			recorder = new VideoWriter(outputFileName,
					fourCC.toInt(), video.get(Videoio.CAP_PROP_FPS), frameSize, HASCOLOR);
//			recorder.open(outputFileName,
//					fourCC.toInt(), video.get(Videoio.CAP_PROP_FPS), frameSize, HASCOLOR);
			if(!recorder.isOpened()){
				System.err.println("Failed to open recorder");
			}
		}
		catch(Exception e){
			System.err.println(e + ": Unable to record video...");
			e.printStackTrace();
		}
		try {
			int colorCounter = 0;
			int frameCount = 0;
			int del = 0;
			int segmentLength = (int)(FPS * SEGMENT_VIDEOLENGTH);
			boolean initiated = false;
			boolean startDeleting = false;
			String toDelete = "";
			BufferedImage img = new BufferedImage(width, height, 
                    BufferedImage.TYPE_3BYTE_BGR);
			Mat mat = new Mat();
			Thread ithread;
			ICCCleaner cleaner = null;

			while (!isDone) {
				if (video.read(mat)) {

					//edit image
//					if(RAINBOW){
//						img = setColor(ICCEditor.Color.ALL, img, colorCounter++);
//						colorCounter = colorCounter % ICCEditor.allColors().length;
//					}
//					else if(myColor != null){
//						img = setColor(myColor, img, colorCounter++);
//					}
					
					//show & record image
					recorder.write(mat);
					repaint(mat, img);
					canvas.showImage(img);
					frameCount++;

					//check end of video
					if(frameCount >= segmentLength){
						recorder.release();

						System.out.println("Finished writing: "+ outputFileName);
						que.enqueue(outputFileName);

						//wait until segments == PRELOADSEGMENTS before
						//repeatedly uploading the index file
						if(initiated){
							oldestSegment = ++oldestSegment % MAX_SEGMENTS;
							iwriter = new IndexWriter(que, oldestSegment,
									segmentNum, MAX_SEGMENTS, INDEXFILE);
							ithread = new Thread(iwriter);
							ithread.start();
						}
						else{
							iwriter = new IndexWriter(que, 0,
									segmentNum, MAX_SEGMENTS, INDEXFILE);
							ithread = new Thread(iwriter);
							ithread.start();
							if(segmentNum == PRELOADSEGMENTS){
								initiated = true;
							}
						}
						if(startDeleting){
							del = segmentNum - DELETESEGMENTS;
							if(del < 0){
								del += 100;
							}
							toDelete = PREFIX + del + FILE_EXT;
							cleaner = new ICCCleaner(s3, toDelete);
							cleaner.start();
						}
						else if(segmentNum == DELETESEGMENTS){
							startDeleting = true;
						}
						outputFileName = PREFIX + (++segmentNum) + FILE_EXT;
						recorder = new VideoWriter();
						recorder.open(outputFileName,
								fourCC.toInt(), video.get(Videoio.CAP_PROP_FPS), frameSize, HASCOLOR);
						segmentNum = segmentNum % MAX_SEGMENTS;
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
	
//	private Frame setColor(ICCEditor.Color color, Frame img, int colorCounter){
//		ICCEditor editor = new ICCEditor();
//		OpenCVFrameConverter.ToIplImage converter =
//				new OpenCVFrameConverter.ToIplImage();
//		Mat ipl;
////		ipl = converter.convertToIplImage(img);
////		editor.set(ipl);
//		switch(color){
//		case ALL :
//			ICCEditor.Color[] colors = ICCEditor.allColors();
//			editor.setPixelValue(colors[colorCounter]);
//			break;
//		default :
//			editor.setPixelValue(color);
//			break;
//		}
////		img = converter.convert(ipl);
//		return img;
//	}
	
	public void repaint(Mat mat, BufferedImage img){
		int w = mat.cols(),
			h = mat.rows();
		byte[] dat = new byte[w * h * 3];
//        img = new BufferedImage(w, h, 
//                    BufferedImage.TYPE_3BYTE_BGR);
        mat.get(0, 0, dat);
        img.getRaster().setDataElements(0, 0, 
                               mat.cols(), mat.rows(), dat);
	}
	
	public void end(){
		isDone = true;
		System.out.println("Attempting to close runner...");
	}

	public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		ICCRunner iccr = new ICCRunner();
		que = new SharedQueue<>(MAX_SEGMENTS + 1);
		s3 = new S3Uploader(BUCKETNAME, que);
		Runtime.getRuntime().addShutdownHook(new ICCRunnerShutdownHook(s3, iccr));
		s3.start();
		iccr.start();
	}
}
