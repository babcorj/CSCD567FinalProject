//import java.io.File;
//import java.net.URL;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacv.*;
//import org.bytedeco.javacpp.*;
//import org.bytedeco.javacpp.indexer.*;
//import static org.bytedeco.javacpp.opencv_core.*;
//import static org.bytedeco.javacpp.opencv_imgproc.*;
//import static org.bytedeco.javacpp.opencv_calib3d.*;
//import static org.bytedeco.javacpp.opencv_objdetect.*;

//Taken from: https://github.com/bytedeco/javacv#sample-usage

public class ICCRunner implements Runnable {
	private final String PREFIX = "myvideo";
	private final String INDEXFILE = "StreamIndex.txt";
	private final static String BUCKETNAME = "icc-videostream-00";
	private final static int MAX_SEGMENTS = 10;
	private final double SEGMENT_VIDEOLENGTH = 8; //seconds
	private final int PRELOADSEGMENTS = 5;
	private final double FPS = 15;
	private CanvasFrame canvas = new CanvasFrame("Instant Cloud Camera");
	private static S3Uploader s3;
	private static SharedQueue<String> que;

	//VISUAL SETTINGS
	private final boolean RAINBOW = false;
	private final boolean GRAYSCALE = false;

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
//		ICCRecorder myRecorder = null;
//		https://stackoverflow.com/questions/28494648/video-compression-using-javacv
		try{
			grabber = FrameGrabber.createDefault(0); // 1 for next camera
			grabber.setFrameRate(FPS);
			grabber.start();
			height = grabber.getImageHeight();
			width = grabber.getImageWidth();
			recorder = new FFmpegFrameRecorder(outputFileName,
					width, height);
			recorder.setFrameRate(FPS);
//			recorder.setAudioChannels(img.arrayChannels());
//			recorder.setVideoBitrate(img.arrayDepth() * img.width() * img.height() * 3);
			recorder.setFormat("flv");
		}
		catch(Exception e){
			System.err.println(e + ": Unable to record video...");
			e.printStackTrace();
		}
		try {
			int colnum = 0;
			int frameCount = 0;
			int segmentLength = (int)(FPS * SEGMENT_VIDEOLENGTH);
			boolean initiated = false;
			Frame img;
			IplImage ipl;
			Thread ithread;
//			Frame[] myImage = createEmptyVideo(segmentLength);
			OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
			ICCEditor editor = new ICCEditor();
			ICCEditor.Color[] colors = ICCEditor.allColors();
			recorder.start();
			while (true) {
				if ((img = grabber.grab()) != null) {
//					//DEBUG: COUNT FRAMES
//					System.out.println("FrameCount: "+frameCount+
//							"\nFrameLength: "+segmentLength);

					//edit image
					if(RAINBOW){
						ipl = converter.convertToIplImage(img);
						editor.set(ipl);
	//					editor.setPixelValue(ICCEditor.Color.RED);
						editor.setPixelValue(colors[colnum++]);
						colnum = colnum % colors.length;
						img = converter.convert(ipl);
					} else if(GRAYSCALE){
						ipl = converter.convertToIplImage(img);
						editor.set(ipl);
						editor.setPixelValue(ICCEditor.Color.GREY);
						img = converter.convert(ipl);
					}
					//show & record image
					canvas.showImage(img);
					recorder.record(img);
					frameCount++;
					//check end of video
					if(frameCount >= segmentLength){
						recorder.stop();
//						grabber.stop();

						System.out.println("Finished writing: "+ outputFileName);
						que.enqueue(outputFileName);

						if(initiated){
							oldestSegment = ++oldestSegment % MAX_SEGMENTS;
							iwriter = new IndexWriter(que, oldestSegment,
									segmentNum, MAX_SEGMENTS, INDEXFILE);
							ithread = new Thread(iwriter);
							ithread.start();
//							que.enqueue(INDEXFILE);
							System.out.println("Finished writing index file");
							System.out.println("OLD: "+oldestSegment+
									"\nNEW: "+segmentNum);
						} else{
//							System.out.println("SegLen: "+segmentNum+
//									"\nPreload: "+PRELOADSEGMENTS);
							if(segmentNum == PRELOADSEGMENTS){
								initiated = true;
							}
						}
						outputFileName = PREFIX + (++segmentNum) + ".flv";
						recorder = new FFmpegFrameRecorder(outputFileName,
								width, height);
						recorder.setFrameRate(FPS);
//						recorder.setAudioChannels(img.arrayChannels());
//						recorder.setVideoBitrate(img.arrayDepth() * img.width() * img.height() * 3);
						recorder.setFormat("flv");
						segmentNum = segmentNum % MAX_SEGMENTS;
						frameCount = 0;
//						grabber.start();
						recorder.start();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try{
			s3.end();
			s3.interrupt();
		} catch(Exception e){
			System.err.println(e + ": Unable to save video!");
			e.printStackTrace();
		}
	}
	
//	private Frame[] createEmptyVideo(int nframes){
//		return new Frame[nframes];
//	}

//	private void writeIndexFile(int oldSegment, int newSegment){
//		FileWriter fw = null;
//		try{
//			fw = new FileWriter(new File(INDEXFILE));
//			fw.write(MAX_SEGMENTS + "\n" + oldSegment + " " + newSegment);
//			fw.close();
//		} catch(IOException e){
//			e.printStackTrace();
//		}
//	}
	
	public static void main(String[] args) {
		ICCRunner iccr = new ICCRunner();
		que = new SharedQueue<>(MAX_SEGMENTS + 1);
		s3 = new S3Uploader(BUCKETNAME, que);
		Thread th = new Thread(iccr);
		s3.start();
		th.start();
	}
}


