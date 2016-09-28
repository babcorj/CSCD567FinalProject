package videoReceiver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

/**
 * Should take curr out of class
 * @author ryanj
 *
 */
public class PlaylistParser {
	
	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------
	private final String SEG_ORD_TAG = "SegmentOrder";
	private final String SEG_INF_TAG = "SegmentInfo";
	private final String FRA_DAT_TAG = "SegmentFrameData";
	
	private byte[] _data;
	private int _curr;
	private int _maxIndex;
	private int _maxSegments;
	private int _minIndex;
	private int _segmentRange;//number of updated videos
	private int[] _currFrameData;
	private double _currTimeStamp;

	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------

	public PlaylistParser(String filename) throws IOException{
		parseFile(filename);
		init();
	}
	
	public PlaylistParser(byte[] data) throws IOException {
		_data = data;
		init();
	}


	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------	
	public double getCurrentTimeStamp(){
		return _currTimeStamp;
	}

	public int[] getCurrentFrameData(){
		return _currFrameData;
	}
	
	public int getCurrentIndex(){
		return _curr;
	}


	//-------------------------------------------------------------------------
	//SET METHODS
	//-------------------------------------------------------------------------	
	public void setCurrentIndex(int currIndex){
		_curr = currIndex;
		switch(validateIndex()) {
			case  0: break;
			case  1: _curr = _maxIndex;
					 break;
			case -1: _curr = _minIndex;
					 break;
		}
		try{
			parseTimeStamp();
			parseFrameData();
		}catch(IOException ioe){
			ioe.printStackTrace();
		}
	}

	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	public void update(byte[] data) throws IOException{
		_data = data;
		update();
//		_curr = _maxIndex;
	}

	public void update(String filename) throws IOException{
		parseFile(filename);
		update();
//		_curr = _maxIndex;
	}
	
	
	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------
	private int[] extractFrameData(String[] strData){
		int size = strData.length;
		int[] numData = new int[size];
		for(int i = 0; i < size; i++){
			numData[i] = Integer.parseInt(strData[i]);
		}
		return numData;
	}

	private void init() throws IOException{
		parseSegmentOrder();
		_curr = _maxIndex;
		parseTimeStamp();
		parseFrameData();	
	}
	
	private void parseFile(String filepath) throws IOException{
		int read;
		File file = new File(filepath);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		DataInputStream inputStream = new DataInputStream(new FileInputStream(file));
		_data = new byte[inputStream.available()];
		
		while ((read = inputStream.read(_data)) > 0) {
			outputStream.write(_data, 0, read);
		}
		inputStream.close();
	}
	
	private void parseSegmentOrder() throws IOException{
		Scanner plScanner = new Scanner(new ByteArrayInputStream(_data));

		while(!plScanner.hasNext("</" + SEG_ORD_TAG + ">")){
			if(!plScanner.hasNext()){
				plScanner.close();
				throw new IOException("Incorrect segment order format");
			}
			if(plScanner.hasNext("<" + SEG_ORD_TAG + ">")){
				plScanner.nextLine();
				_segmentRange = plScanner.nextInt();
				_maxSegments = plScanner.nextInt();
				_minIndex = plScanner.nextInt();
				_maxIndex = plScanner.nextInt();
			}
			plScanner.nextLine();
		}
		plScanner.close();
	}

	private void parseTimeStamp() throws IOException{
		boolean currIndex = false;
		Scanner plScanner = new Scanner(new ByteArrayInputStream(_data));
		String[] arr = null;
		
		while((!plScanner.hasNext("</" + SEG_INF_TAG + ">")) && (!currIndex)){
			if(!plScanner.hasNext()){
				break;
			}
			if(plScanner.hasNext("<" + SEG_INF_TAG + ">")){
				plScanner.nextLine();
				while(!currIndex){
					String str = plScanner.nextLine();
					arr = str.split(":");
					if(Integer.parseInt(arr[0]) == _curr){
						currIndex = true;
					}
				}
				_currTimeStamp = Double.parseDouble(arr[1]);
			}
			plScanner.nextLine();
			
		}
		plScanner.close();
	}

	private void parseFrameData() throws IOException{
		boolean currIndex = false;
		Scanner plScanner = new Scanner(new ByteArrayInputStream(_data));
		String str, arr[] = null, frameData[];
		
		while((!plScanner.hasNext("</" + FRA_DAT_TAG + ">")) && (!currIndex)){
			if(!plScanner.hasNext()){
				break;
			}
			if(plScanner.hasNext("<" + FRA_DAT_TAG + ">")){
				plScanner.nextLine();
				while(!currIndex){
					str = plScanner.nextLine();
					arr = str.split(":");
					if(Integer.parseInt(arr[0]) == _curr){
						currIndex = true;
					}
				}
				frameData = arr[1].split("<|/>");
				_currFrameData = extractFrameData(frameData[1].split(" "));
			}
			plScanner.nextLine();
		}
		plScanner.close();
	}	
	
	private void update() throws IOException{
		parseSegmentOrder();

		//corrects current index if necessary
		switch(validateIndex()) {
			case  0: incrementCurrentFrame();
					 break;
			case  1: _curr = _maxIndex;
					 break;
			case -1: _curr = _minIndex;
					 break;
		}
		parseTimeStamp();
		parseFrameData();
	}
	
	/**
	 * Checks the validity of the current index number with the current range
	 * of video segments available.
	 * 
	 * @return	0 if current index is within a valid range, 1 if the current
	 * 			index is past the latest video, and -1 if the current video
	 * 			is behind the oldest video segment.
	 */
	private int validateIndex(){		
		if(_curr < _minIndex) {
			if(_minIndex + _segmentRange >= _maxSegments){
				if((_curr > _maxIndex) && (_curr <= _maxIndex + _segmentRange)){
					return 1;
				} else if(_curr > _maxIndex + _segmentRange){
					return -1;
				}
			} else {
				return -1;
			}
		}
		else if(_curr >= _maxIndex) {
			if(_minIndex + _segmentRange >= _maxSegments){
				if((_curr < _minIndex) && (_curr >= _minIndex - _segmentRange)){
					return -1;
				} else if(_curr < _minIndex - _segmentRange){
					return 1;
				}
			} else {
				return 1;
			}
		}
		return 0;
	}
	
	private void incrementCurrentFrame(){
		_curr = ++_curr % _maxSegments;
	}
}

