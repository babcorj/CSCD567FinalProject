package performance;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class PerformanceLogger {
	static final long TIME_OFFSET = -25259;
	
	private int _bufferEvents = 0;
	private double _connectTime = 0;
	private int _segmentDrops = 0;
	private int _segmentPlays = 0;
	private long _startTime = 0;
	private double _timeBuffered = 0;
	private double _timePlayed = 0;
	private long _totalBytes = 0;

	private ArrayList<Double> _delay;
	private String _filename;
	private String _folder;
	private FileWriter _fw;
	
	public PerformanceLogger(String filename, String location) throws IOException {
		_delay = new ArrayList<>();
		_fw = new FileWriter((_folder=location) + (_filename=filename));
	}
	
	public void close() throws IOException { _fw.close(); }
	
	public double getBitRate(){ return (_totalBytes * 8)/_timePlayed; }
	
	public int getBufferEvents(){ return _bufferEvents; }
	
	//Connection Success Rate
	public double getCSR(){ return (_segmentPlays - _bufferEvents)/_segmentPlays; }
	
	public long getBytesPlayed(){ return _totalBytes; }
	
	public double getConnectTime(){ return _connectTime; }
	
	public double getDelayAverage(){
		if(_delay == null){
			throw new RuntimeException("DelayList has not been initialized");
		}
		
		double average, sum = 0, count = _delay.size();
		
		for(int i=0; i < count; i++){
			sum += _delay.get(i);
		}
		
		average = sum/count;
		
		return average;
	}
	
	public ArrayList<Double> getDelayList(){
		return _delay;
	}
	
	public int getDrops(){ return _segmentDrops; }
	
	public String getFileName(){ return _filename; }
	
	public String getFilePath(){ return new File(_folder + _filename).getAbsolutePath(); }
	
	public double getLagRatio(){ return _timePlayed/(_timePlayed + _timeBuffered + _connectTime); }
	
	public int getPlays(){ return _segmentPlays; }
	
	public long getStartTime(){ return _startTime; }

	public double getTimeBuffered(){ return _timeBuffered; }
	
	public double getTimePlayed(){ return _timePlayed; }
	
	public double getTimeTotalBuffer(){
		return _timeBuffered + _connectTime;
	}

	public void log(String str) throws IOException {
		try{
			_fw.write(str);
		}
		catch(IOException e){
			String s = str;
			if(str != null){
				if(str.length() >= 16){
					s = str.substring(0, 16);
				}
			} else {
				s = "";
			}
			System.err.println("Logging error:" + s + "...");
		}
	}
	
	public void logBuffer(double time){ _timeBuffered += time; }
	
	public void logBufferEvent(){ _bufferEvents++; }
	
	public void logBytes(long bytes){ _totalBytes += bytes; }
	
	public void logConnectTime(double connectTime){ _connectTime = connectTime; }
	
	public void logDelay(double delay){ _delay.add(delay); }

	public void logPlay(double time){ _timePlayed += time; }
	
	public void logSegmentDrop(){ _segmentDrops++; }
	
	public void logSegmentPlay(){ _segmentPlays++; }
	
	public void logTime(){
		double cur = System.currentTimeMillis() + TIME_OFFSET;
		double startTime = _startTime;
		double timelapse = (cur - startTime)/1000;
		DecimalFormat formatter = new DecimalFormat("#.000");
		
		try{
			_fw.write(formatter.format(timelapse));
		}
		catch(IOException e){
			e.printStackTrace();
			System.err.println("Logging error:" + (cur - startTime)/1000);
		}
	}
	
	/**
	 * @param number
	 */
	public void logVideoTransfer(double number){
		DecimalFormat formatter = new DecimalFormat("#.###");
		
		try{
			_fw.write(formatter.format(number));
			logDelay(number);
		}
		catch(IOException e){
			System.err.println("Logging error:" + number);
		}
	}
	
	public void setStartTime(long time){ _startTime = time; }
	
	public void startTime(){ _startTime = System.currentTimeMillis(); }
}
