package videoUtility;

public class VideoObject {

	private String _filename;
	private double _timeStamp;
	
	public VideoObject(String filename) {
		_filename = filename;
		_timeStamp = System.currentTimeMillis();
	}

	public VideoObject setFile(String filename){
		_filename = filename;
		return this;
	}
	
	public VideoObject setTimeStamp(double time){
		_timeStamp = time;
		return this;
	}

	public String getFileName(){
		return _filename;
	}
	
	public double getTimeStamp(){
		return _timeStamp;
	}
	
	
}
