import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;

public class ICCRecorder implements Runnable {
	private Frame[] video;
	private String outputFileName;
	private SharedQueue<String> que;
	private double fps;
	
	public ICCRecorder(Frame[] thatvideo, String videoname,
			SharedQueue<String> queue, double frameRate){
		video = thatvideo;
		outputFileName = videoname;
		que = queue;
		fps = frameRate;
	}
	
	public void run(){
		OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
		IplImage img = converter.convertToIplImage(video[0]);
		FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFileName,
				img.width(), img.height());
		recorder.setFrameRate(fps);
//		recorder.setAudioChannels(img.arrayChannels());
//		recorder.setVideoBitrate(img.arrayDepth() * img.width() * img.height() * 3);
		recorder.setFormat("flv");
		try{
			recorder.start();
			for(int i = 0; i < video.length; i++){
				img = converter.convertToIplImage(video[i]);
				recorder.record(converter.convert(img));
//				System.out.println(i);
				Thread.sleep((long)fps * 3);
			}
			recorder.stop();
		} catch(Exception e){
			e.printStackTrace();
		}
		System.out.println("Finished Recording: "+ outputFileName);
		que.enqueue(outputFileName);
	}
}
