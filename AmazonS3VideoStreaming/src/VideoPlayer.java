import java.io.File;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;

public class VideoPlayer implements Runnable {

	private S3Downloader downloader;
	private VideoStream stream;

	public VideoPlayer(String bucket, String prefix, String output) {

		stream = new VideoStream();
		downloader = new S3Downloader(bucket, prefix, output, stream);
	}

	@Override
	public void run() {

		downloader.start();
		Frame image = null;
		System.out.println("Starting video player thread");
		CanvasFrame canvas = new CanvasFrame("Web Cam");
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);

		while (true) {

			try {

				System.out.println("test");

				while (!stream.isEmpty()) {

					File video = stream.getFrame();
					video.deleteOnExit();
					FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video.getAbsolutePath());

					try {
						grabber.start();

						image = grabber.grabFrame();
						System.out.println("Player got frame " + image.toString());

						while (image != null) {

							System.out.println("Player got frame " + image.getClass().getName());
							canvas.showImage(image);
							image = grabber.grabFrame();
							Thread.sleep((long)(grabber.getFrameRate()*3));
						}

					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				synchronized (stream) {

					System.out.println("Player waiting");
					stream.wait();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {

		final String OUTPUT = ".\\";
		final String BUCKET = "icc-videostream-00";
		Thread player = new Thread(new VideoPlayer(BUCKET, "myvideo", OUTPUT));

		player.start();
	}
}
