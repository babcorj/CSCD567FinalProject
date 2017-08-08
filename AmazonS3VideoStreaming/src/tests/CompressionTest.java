package tests;

import videoUtility.FourCC;
import videoUtility.ICCFrameWriter;
import videoSender.ICCSetup;
import java.io.ByteArrayOutputStream;
import java.util.LinkedList;

import javax.imageio.ImageIO;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;

public class CompressionTest {
	//-------------------------------------------------------------------------
	//Main
	//-------------------------------------------------------------------------
	public static void main(String[] args) {
		String cvlib = "C:\\Libraries\\opencv\\build\\java\\x64\\opencv_java320.dll";
		System.load(cvlib);
//		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		
		Mat _mat = new Mat();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		LinkedList<Long> matlist = new LinkedList<>();
		LinkedList<Long> imglist = new LinkedList<>();
		ICCFrameWriter segmentWriter = new ICCFrameWriter(_mat, output);
		VideoCapture grabber = null;
		try{
			grabber = getVideoCapture();
		}catch(Exception e){
			e.printStackTrace();
		}

		for(int i=0;i<100;i++){
			try {
				//capture and record video
				if (!grabber.read(_mat)) {
					System.err.println("Did not record!");
				}
				Imgproc.cvtColor(_mat, _mat, Imgproc.COLOR_BGR2GRAY);
				
				long matsize = _mat.total() * _mat.elemSize();
				
				System.out.println("Original size: " + matsize);
				matlist.add(matsize);
				
				ByteArrayOutputStream tmp = new ByteArrayOutputStream();
			    ImageIO.write(segmentWriter.getBufferedImage(_mat), "jpeg", tmp);
			    tmp.close();
			    
				System.out.println("New: "+tmp.size());
				imglist.add((long)tmp.size());
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println("Average mat size: " + avg(matlist));
		System.out.println("Average img size: " + avg(imglist));
		
		System.out.println("Compression test successfully closed");
	}
	
	private static long avg(LinkedList<Long> list){
		int sz = list.size();
		long total = 0;
		for(int i=0;i<sz;i++){
			total+=list.get(i);
		}
		return total/sz;
	}
	private static VideoCapture getVideoCapture() throws Exception {
		VideoCapture _videoCap = new VideoCapture(0);
//		_videoCap.set(Videoio.CAP_PROP_FOURCC, new FourCC("MJPG").toInt());
		_videoCap.set(Videoio.CAP_PROP_FPS, 20);
		_videoCap.set(Videoio.CV_CAP_PROP_FRAME_WIDTH, 640);
		_videoCap.set(Videoio.CV_CAP_PROP_FRAME_HEIGHT, 480);

		System.out.println("Prop: " + Videoio.CAP_PROP_FPS
		+ "\nVideoGetFPS: " + _videoCap.get(Videoio.CV_CAP_PROP_FRAME_HEIGHT));
		
		if(!_videoCap.isOpened()){
			throw new Exception("Failed to open video stream");
		}
		
		return _videoCap;
	}
}
