package videoUtility;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedList;

public class VideoSegment {

	private byte[] _data;
	private int _index;
	private int[] _frameOrder;
	private double _timeStamp;
	private LinkedList<BufferedImage> _imglist;
	
	public VideoSegment(int index, int[] frameOrder, byte[] data) {
		_data = data;
		_frameOrder = frameOrder;
		_index = index;
		_timeStamp = System.currentTimeMillis();
	}

	//-------------------------------------------------------------------------
	//SET METHODS
	//-------------------------------------------------------------------------
	public VideoSegment setData(byte[] data){
		_imglist = null;
		_data = data;
		return this;
	}
	public VideoSegment setIndex(int index){
		_index = index;
		return this;
	}
	public VideoSegment setFrameOrder(int[] frameOrder){
		_imglist = null;
		_frameOrder = frameOrder;
		return this;
	}
	public VideoSegment setTimeStamp(double time){
		_timeStamp = time;
		return this;
	}

	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------
	public byte[] getData(){
		return _data;
	}
	public int[] getFrameOrder(){
		return _frameOrder;
	}
	public LinkedList<BufferedImage> getImageList(){
		if(_imglist == null){
			_imglist = convertToImageList();
		}
		return _imglist;
	}
	public int getIndex(){
		return _index;
	}
	public double getTimeStamp(){
		return _timeStamp;
	}
	public String getName(){
		return FileData.VIDEO_PREFIX.print() + _index + FileData.VIDEO_SUFFIX.print();
	}
	public long size(){
		return _data.length;
	}
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	public static String toString(int index){
		return FileData.VIDEO_PREFIX.print() + index + FileData.VIDEO_SUFFIX.print();
	}
	
	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------
	private LinkedList<BufferedImage> convertToImageList(){
		LinkedList<BufferedImage> imglist = null;

		try {
			imglist = ICCFrameReader.readAll(_frameOrder, _data);
		} catch (IllegalArgumentException | IOException e) {
			e.printStackTrace();
		}

		return imglist;
	}
}
