package videoUtility;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedList;

public class VideoSegment {

	private byte[] _data;
	private int _index;
	private LinkedList<BufferedImage> _imglist;
	private VideoSegmentHeader _header;
	
	public VideoSegment(int index, byte[] data, VideoSegmentHeader header) {
		_data = data;
		_index = index;
		_header = header;
		_header.setStartTime(System.currentTimeMillis());
	}

	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------
	public byte[] getData(){
		byte[] data = assembleData();
		return data;
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
		return _header.getTimeStamp();
	}
	public String getName(){
		return FileData.VIDEO_PREFIX.print() + _index + FileData.VIDEO_SUFFIX.print();
	}
	public long size(){
		return _data.length + _video.size();
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
	private byte[] assembleData(){
		byte[] headerData = _header.export();
		byte[] data = new byte[headerData.length + _data.length];
		int i = 0, j = 0, size = headerData.length;
		//store header data
		for(; i < size; i++){
			data[i] = headerData[i];
		}
		
		size = data.length;
		//store video data
		for(; i < size; i++, j++){
			data[i] = _data[j];
		}
		
		return data;
	}

	private LinkedList<BufferedImage> convertToImageList(){
		LinkedList<BufferedImage> imglist = null;

		try {
			imglist = ICCFrameReader.readAll(_header.getFrameOrder(), _data);
		} catch (IllegalArgumentException | IOException e) {
			e.printStackTrace();
		}

		return imglist;
	}
}
