package videoUtility;

import java.nio.ByteBuffer;

/**
 * 
 * @author	Ryan Babcock
 * 
 * This class holds all the information stored in the header of each
 * video segment.
 * 
 * @param	_frameOrder	The index positions of each image
 * @param	_timeStamp	The time the video was recorded. This is done
 * 						automatically when a video segment is created.
 * @param	_startTime	The start time of the program (ICCRunner).
 * @see VideoSegment
 */
public class VideoSegmentHeader {

	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------
	private int[] _frameOrder;
	private long _timeStamp;

	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------
	public VideoSegmentHeader(){
		//DVC
	}
	public VideoSegmentHeader(int[] frameOrder){
		_frameOrder = frameOrder;
	}
	/**
	 * Reads in the header data to supply values to member variables.
	 * @param data	The video header data grabbed from the video file.
	 */
	public VideoSegmentHeader(byte[] data){
		init(data);
	}

	//-------------------------------------------------------------------------
	//GETS
	//-------------------------------------------------------------------------
	public byte[] data(){
		assert(_frameOrder != null);
		
		ByteBuffer buffer = ByteBuffer.allocate(this.size());
		buffer.putLong(_timeStamp);
		
		for(int i = 0; i < _frameOrder.length; i++){
			buffer.putInt(_frameOrder[i]);
		}
		
		return buffer.array();
	}
	public int[] getFrameOrder(){
		return _frameOrder;
	}
	public long getTimeStamp(){
		return _timeStamp;
	}
	public int size(){
//		return Long.BYTES + (_frameOrder.length * Integer.BYTES);
		return 8 + (_frameOrder.length * 4);
	}

	//-------------------------------------------------------------------------
	//SETS
	//-------------------------------------------------------------------------
	public void setTimeStamp(long timeStamp){
		_timeStamp = timeStamp;
	}
	public void setFrameOrder(int[] frameOrder){
		_frameOrder = frameOrder;
	}

	//-------------------------------------------------------------------------
	//TOSTRING
	//-------------------------------------------------------------------------
	public String toString(){
		StringBuilder sbuilder = new StringBuilder();

		sbuilder.append(printTimeStamp());
		sbuilder.append(printSegmentFrameData());
		
		return sbuilder.toString();
	}
	
	//-------------------------------------------------------------------------
	//EQUALS
	//-------------------------------------------------------------------------
	
	//-------------------------------------------------------------------------
	//COMPARETO
	//-------------------------------------------------------------------------
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	
	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------
	private void init(byte[] data){
//		int frameSize = (data.length - Long.BYTES)/Integer.BYTES;
		int frameSize = (data.length - 8)/4;
		ByteBuffer buffer = ByteBuffer.allocate(data.length);
		buffer.put(data);
		buffer.flip();
		_timeStamp = buffer.getLong();
		_frameOrder = new int[frameSize];
		
		for(int i = 0; i < _frameOrder.length; i++){
			_frameOrder[i] = buffer.getInt();
		}
		buffer = null;
	}
	
	private String printTimeStamp(){
		return Long.toString(_timeStamp);
	}
	
	private String printSegmentFrameData(){
		StringBuilder sb = new StringBuilder();
		
		int size = _frameOrder.length-2;

		for(int i = 0; i < size; i++){
			sb.append(Integer.toString(_frameOrder[i]) + " ");
		}
		sb.append(Integer.toString(_frameOrder[size-1]));
	
		return sb.toString();
	}
	
}
