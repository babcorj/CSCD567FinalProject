package videoUtility;

import java.awt.Graphics;
import java.awt.Image;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

//import org.opencv.videoio.*;

@SuppressWarnings("serial")
public class DisplayFrame extends JFrame {
	private boolean _end;
	private JPanel contentPane;
	private VideoSource source;

	public DisplayFrame(String name, VideoSource theSource) {
		super(name);
		_end = false;
		source = theSource;
		init();
	}

	public void init() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 650, 490);
		setResizable(true);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);

		new MyThread().start();
	}

	public void paint(Graphics g){
		g = contentPane.getGraphics();
		try {
			Image img = source.getCurrentFrame()
					.getScaledInstance(getWidth(), getHeight(), Image.SCALE_FAST);
				g.drawImage(img, 0, 0, this);
		} catch(Exception e){
//			System.err.println("No video to capture, will attempt again...");
			try{
				Thread.sleep(200);
			} catch(InterruptedException ie){}
		}
	}

	public void end(){
		_end = true;
	}
	
	class MyThread extends Thread{
		@Override
		public void run() {
			while (!_end){
				try {
					repaint();
					Thread.sleep(0);
				} catch (InterruptedException e) { }
			}  
		} 
	}
}