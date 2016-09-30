package videoUtility;

import java.io.FileWriter;
import java.io.IOException;
//import java.io.IOException;
import java.text.DecimalFormat;

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
	private long _startTime;

	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------
	public VideoSegmentHeader(long startTime){
		_startTime = startTime;
	}
	
	//-------------------------------------------------------------------------
	//GETS
	//-------------------------------------------------------------------------
	public int[] getFrameOrder(){
		return _frameOrder;
	}
	public int size(){
		return _frameOrder.length + Double.BYTES;
	}
	public double getTimeStamp(){
		return _timeStamp;
	}

	//-------------------------------------------------------------------------
	//SETS
	//-------------------------------------------------------------------------
	public void setStartTime(long startTime){
		_timeStamp = startTime;
	}
	public void setFrameOrder(int[] frameOrder){
		_frameOrder = frameOrder;
	}

	//-------------------------------------------------------------------------
	//TOSTRING
	//-------------------------------------------------------------------------
	public String toString(){
		StringBuilder sbuilder = new StringBuilder();

		sbuilder.append(printSegmentOrder());
		sbuilder.append(printSegmentData());
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
	/**
	 * Exports contents to byte array
	 * 
	 * @param data	The formatted VideoSegmentHeader
	 */
	public byte[] export(){
		return this.toString().getBytes();
	}	
	
	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------	

	private String printSegmentData(){
		DecimalFormat formatter = new DecimalFormat("#.000");
		String time = "";
		
		time = formatter.format(((_timeStamp - _startTime)/1000));
		sb.append(v.getIndex() + ":" + time + "\n");

		return sb.toString();
	}
	
	private String printSegmentFrameData(){
		StringBuilder sb = new StringBuilder();
		
		sb.append("<SegmentFrameData>\n");
		
		for(VideoSegment v : _videoDataList){
			int i, size;
			int[] x = v.getFrameOrder();
			sb.append(v.getIndex() + ":<");

			for(i = 0, size = x.length-1; i < size; i++){
				sb.append(Integer.toString(x[i]) + " ");
			}
			sb.append(Integer.toString(x[i]) + "/>\n");
		}
		sb.append("</SegmentFrameData>\n");
		return sb.toString();
	}
	
}
