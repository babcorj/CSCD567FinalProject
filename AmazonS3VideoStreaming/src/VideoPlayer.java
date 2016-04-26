import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import org.opencv.core.*;
import org.opencv.videoio.*;

public class VideoPlayer extends Thread {

	private static S3Downloader downloader;
	private VideoStream stream;
	private boolean isDone = false;
	Mat mat = new Mat();
	
	public VideoPlayer(String bucket, String prefix, String output) {

		stream = new VideoStream();
		downloader = new S3Downloader(bucket, prefix, output, stream);
	}

	@Override
	public void run() {

		double FPS;
		VideoCapture grabber = null;
		downloader.start();
		System.out.println("Starting video player thread");
		DisplayFrame canvas = new DisplayFrame("Web Cam", this);
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		File video = null;
		
		while (!isDone) {

			try {
				double secondsPlayed = 0.0;
				long startTime = System.currentTimeMillis();
				System.out.printf("Time played: %.2f\n", secondsPlayed);
				
				while (!stream.isEmpty()) {

					video = stream.getFrame();
					grabber = new VideoCapture(video.getAbsolutePath());
					FPS = grabber.get(Videoio.CAP_PROP_FPS);
					
					try {
						grabber.read(mat);

						while (mat != null) {
							secondsPlayed = ((double)System.currentTimeMillis() - startTime)/1000;
							System.out.printf("Time played: %.2f\n", secondsPlayed);
							grabber.read(mat);
							try{
								Thread.sleep((long) FPS *
										mat.channels());
							} catch(NullPointerException npe){
								//npe.printStackTrace();
								Thread.sleep((long) FPS *
										mat.channels());
							}
						}
						//grabber.stop();
						video.delete();
						
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				synchronized (stream) {
					try{
						System.out.println("Player waiting");
						stream.wait();
					} catch(InterruptedException ie){
						end();
						continue;
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try{
			grabber.release();
			video.delete();
		} catch (Exception e){
			e.printStackTrace();
		}
		System.out.println("VideoPlayer successfully closed");
	}

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
	
	public void end(){
		System.out.println("Attempting to close VideoPlayer...");
		isDone = true;
	}
	
	public static void main(String[] args) {

		final String OUTPUT = ".\\";
		final String BUCKET = "icc-videostream-00";
		VideoPlayer player = new VideoPlayer(BUCKET, "myvideo", OUTPUT);

		VideoPlayerShutDownHook shutdownInstructions =
				new VideoPlayerShutDownHook(downloader, player);
		Runtime.getRuntime().addShutdownHook(shutdownInstructions);
		player.start();
	}
}
