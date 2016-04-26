
import java.awt.Graphics;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

//import org.opencv.videoio.*;

@SuppressWarnings("serial")
public class DisplayFrame extends JFrame {
	private JPanel contentPane;
	private ICCRunner myVideo;
	private VideoPlayer videoIn;

	/**
	 * Create the frame.
	 */
	public DisplayFrame(String name, ICCRunner theVideo) {
		super(name);
		myVideo = theVideo;
		init();
	}

	public DisplayFrame(String name, VideoPlayer theVideo) {
		super(name);
		videoIn = theVideo;
		init();
	}

	public void init() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 650, 490);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);

		new MyThread().start();
	}

	public void paint(Graphics g){
		g = contentPane.getGraphics();
		try {
			if(myVideo != null){
				g.drawImage(myVideo.getCurrentFrame(), 0, 0, this);
			}
			else
				g.drawImage(videoIn.getCurrentFrame(), 0, 0, this);
			
		} catch(NullPointerException e){
			System.err.println("No video to capture, willCapture attempt again in 5 seconds");
			try{
				Thread.sleep(5000);
			} catch(InterruptedException ie){}
		}
	}

	class MyThread extends Thread{
		@Override
		public void run() {
			for (;;){
				try {
					repaint();
					Thread.sleep(0);
				} catch (InterruptedException e) { }
			}  
		} 
	}
}
