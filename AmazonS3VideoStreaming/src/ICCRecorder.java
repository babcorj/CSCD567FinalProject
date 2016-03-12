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
		IplImage img = (IplImage)video[0].opaque;
		FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFileName,
				img.width(), img.height());
		OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
		recorder.setFrameRate(fps);
//		recorder.setVideoBitrate(20 * video[0].width() * video[0].height() * 3);
		recorder.setFormat("flv");
		try{
			recorder.start();
			for(int i = 0; i < video.length; i++){
				recorder.record(converter.convert(video[i]));
				System.out.println(i);
			}
			recorder.stop();
		} catch(Exception e){
			e.printStackTrace();
		}
		System.out.println("Finished Recording: "+ outputFileName);
		que.enqueue(outputFileName);
	}
}
