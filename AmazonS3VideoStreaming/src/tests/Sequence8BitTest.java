package tests;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jcodec.api.FrameGrab8Bit;
import org.jcodec.api.JCodecException;
import org.jcodec.api.SequenceEncoder8Bit;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.common.model.Rational;
import org.jcodec.scale.AWTUtil;
import org.opencv.core.Mat;

public class Sequence8BitTest {
	
	private static boolean _isDone = false;
	
	public static void main(String[] args) {
		byte[] data = writeVideo();
		readVideo(data);
	}

	private static void encodeVideo(SequenceEncoder8Bit encoder, ByteBuffer buffer, int videoSize){
		try{//Close Encoder
			encoder.finish();
			buffer = ByteBuffer.wrap(Arrays.copyOf(buffer.array(), videoSize));
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	private static BufferedImage matToBufferedImage(Mat in) throws IOException {
		byte[] data = new byte[in.width() * in.height() * (int)in.elemSize()];
		int type;
		BufferedImage out;
		
		in.get(0, 0, data);
		
		if(in.channels() == 1){
			type = BufferedImage.TYPE_BYTE_GRAY;
		}
		else{
			type = BufferedImage.TYPE_3BYTE_BGR;
		}
		
		out = new BufferedImage(in.width(), in.height(), type);
		out.getRaster().setDataElements(0, 0, in.width(), in.height(), data);
		
		return out;
	}
	
	private static BufferedImage readImage(FrameGrab8Bit grabber) {
		Picture8Bit pic = null;
		
		try {
			pic = grabber.getNativeFrame();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		return AWTUtil.toBufferedImage8Bit(pic);
		
	}
	
	private static void readVideo(byte[] data){
		FrameGrab8Bit grabber = null;
		BufferedImage img;
		ByteBuffer buffer;
		SeekableByteChannel channel;

		buffer = ByteBuffer.wrap(data);
		channel = new ByteBufferSeekableByteChannel(buffer);
		
		try {
			grabber = FrameGrab8Bit.createFrameGrab8Bit(channel);

			while((img = readImage(grabber)) != null){
				//display Image
			}
			
			channel.close();
		
		} catch (IOException | JCodecException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	private static void writeImage(Mat mat, SequenceEncoder8Bit encoder){
		try{//Mat -> BufferedImage -> Picture8Bit
			BufferedImage img = matToBufferedImage(mat);
			Picture8Bit pic = AWTUtil.fromBufferedImageRGB8Bit(img);//this is the 
			encoder.encodeNativeFrame(pic);						   //only constructor that won't crash
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	public static byte[] writeVideo(){
		int fps = 25;
		int videoSize = 300_000;//allocated for buffer --> think max size
		ByteBuffer buffer = ByteBuffer.allocate(videoSize);
		Mat mat = new Mat();
		SeekableByteChannel channel = new ByteBufferSeekableByteChannel(buffer);
		SequenceEncoder8Bit enc = null;

		try {// new SequenceEncoder..
			enc = new SequenceEncoder8Bit(channel, new Rational(fps, 1));
			enc.getEncoder().setKeyInterval(fps);
			
			while(!_isDone){//Here, grabbing mat frames from camera using JavaCV
				writeImage(mat, enc);
			}	
			encodeVideo(enc, buffer, (int)channel.position());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		return buffer.array();
	}	
		
}
