
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacv.*;

public class ICCRunner extends Thread {
	private final String PREFIX = "myvideo";
	private final String INDEXFILE = "StreamIndex.txt";
	private final static String BUCKETNAME = "icc-videostream-00";
	private final static int MAX_SEGMENTS = 100;
	private final double SEGMENT_VIDEOLENGTH = 8; //seconds
	private final int PRELOADSEGMENTS = 5; // diff of min v. max in index file
	private final int DELETESEGMENTS = 10; // delete x frames behind
	private final double FPS = 15;
	private CanvasFrame canvas = new CanvasFrame("Instant Cloud Camera");
	private static S3Uploader s3;
	private static SharedQueue<String> que;
	private boolean isDone = false;
	
	//VISUAL SETTINGS
	private final boolean RAINBOW = false;
	private final ICCEditor.Color myColor = ICCEditor.Color.BLUE;
//	private final ICCEditor.Color myColor = null;
	
	public ICCRunner() {
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
	}
	@Override
	public void run() {
		int segmentNum = 0,
				width = 0,
				height = 0,
				oldestSegment = 0;
		String outputFileName = PREFIX + segmentNum + ".flv";
		FrameGrabber grabber = null;
		FFmpegFrameRecorder recorder = null;
		IndexWriter iwriter = null;

		try{
			grabber = FrameGrabber.createDefault(0); // 1 for next camera
			grabber.setFrameRate(FPS);
			grabber.start();
			
			height = grabber.getImageHeight();
			width = grabber.getImageWidth();
			
			recorder = new FFmpegFrameRecorder(outputFileName,
					width, height);
			recorder.setFrameRate(FPS);
			recorder.setFormat("flv");
		}
		catch(Exception e){
			System.err.println(e + ": Unable to record video...");
			e.printStackTrace();
		}
		try {
			int colnum = 0;
			int frameCount = 0;
			int del = 0;
			int segmentLength = (int)(FPS * SEGMENT_VIDEOLENGTH);
			boolean initiated = false;
			boolean startDeleting = false;
			String toDelete = "";
			Frame img;
			Thread ithread;
			ICCCleaner cleaner = new ICCCleaner(s3);

			recorder.start();

			while (!isDone) {
				if ((img = grabber.grab()) != null) {

					//edit image
					if(RAINBOW){
						img = setColor(ICCEditor.Color.ALL, img, colnum++);
						colnum = colnum % ICCEditor.allColors().length;
					}
					else if(myColor != null){
						img = setColor(myColor, img, colnum++);
					}
					
					//show & record image
					canvas.showImage(img);
					recorder.record(img);
					frameCount++;

					//check end of video
					if(frameCount >= segmentLength){
						recorder.stop();

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
							if(segmentNum == PRELOADSEGMENTS){
								initiated = true;
							}
						}
						if(segmentNum == DELETESEGMENTS){
							startDeleting = true;
						}
						if(startDeleting){
							del = segmentNum - DELETESEGMENTS;
							if(del < 0){
								del += 100;
							}
							toDelete = PREFIX + del + ".flv";
							cleaner.set(toDelete);
							cleaner.start();
						}
						outputFileName = PREFIX + (++segmentNum) + ".flv";
						recorder = new FFmpegFrameRecorder(outputFileName,
								width, height);
						recorder.setFrameRate(FPS);
						recorder.setFormat("flv");
						segmentNum = segmentNum % MAX_SEGMENTS;
						frameCount = 0;
						recorder.start();
					}
				}
			}
			grabber.stop();
			recorder.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Runner successfully closed");
	}
	
	private Frame setColor(ICCEditor.Color color, Frame img, int colnum){
		ICCEditor editor = new ICCEditor();
		OpenCVFrameConverter.ToIplImage converter =
				new OpenCVFrameConverter.ToIplImage();
		IplImage ipl;
		ipl = converter.convertToIplImage(img);
		editor.set(ipl);
		switch(color){
		case ALL :
			ICCEditor.Color[] colors = ICCEditor.allColors();
			editor.setPixelValue(colors[colnum]);
			break;
		default :
			editor.setPixelValue(color);
			break;
		}
		img = converter.convert(ipl);
		return img;
	}
	
	public void end(){
		isDone = true;
		System.out.println("Attempting to close runner...");
	}

	public static void main(String[] args) {
		ICCRunner iccr = new ICCRunner();
		que = new SharedQueue<>(MAX_SEGMENTS + 1);
		s3 = new S3Uploader(BUCKETNAME, que);
		Runtime.getRuntime().addShutdownHook(new ICCRunnerShutdownHook(s3, iccr));
		s3.start();
		iccr.start();
	}
}
