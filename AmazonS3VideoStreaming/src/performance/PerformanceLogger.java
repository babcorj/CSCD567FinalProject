package performance;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class PerformanceLogger {
//	static final long TIME_OFFSET = 0;
	
	private int _bufferEvents = 0;
	private double _connectTime = 0.0;
	private int _segmentDrops = 0;
	private int _segmentPlays = 0;
	private double _serverBitRate = 0.0;
	private long _startTime = 0;
	private double _timeBuffered = 0.0;
	private double _timePlayed = 0.0;
	private long _totalBytes = 0;

	private ArrayList<Double> _delay;
	private String _filename;
	private String _folder;
	private FileWriter _fw;
	
	public PerformanceLogger(String filename, String location) throws IOException {
		_delay = new ArrayList<>();
		_fw = new FileWriter((_folder=location) + (_filename=filename));
	}

//-------------------------------------------------------------------------------------------------
//MISC METHODS
//-------------------------------------------------------------------------------------------------
	
	public void close() throws IOException { _fw.close(); }

	public void reset(){
		_bufferEvents = 0;
		_connectTime = 0.0;
		_segmentDrops = 0;
		_segmentPlays = 0;
		_startTime = 0;
		_timeBuffered = 0.0;
		_timePlayed = 0.0;
		_totalBytes = 0;
		_delay = new ArrayList<>();
	}
	
	@Override
	public String toString(){
		DecimalFormat df = new DecimalFormat("#.##");
		StringBuilder sb = new StringBuilder();

		sb.append("Bit Rate: " + (df.format(((getBitRate()*8)/1000.0)) + " Kbps\n"));
		sb.append("Server Bit Rate: " + (df.format(_serverBitRate/1000.0) + " Kbps\n"));
		sb.append("Play Time: " + df.format(getTimePlayed()) + "\n");
		sb.append("Buffer Time: " + df.format(getTimeBuffered()) + "\n");
		sb.append("Connect Time: " + df.format(getConnectTime()) + "\n");
		sb.append("Total Buffer Time: " + df.format(getTimeTotalBuffer()) + "\n");
		sb.append("Lag Ratio: " + df.format(getLagRatio()) + "\n");
		sb.append("Average Delay: " + df.format(getDelayAverage()) + " sec\n");
		sb.append("Segments Played: " + getPlays() + "\n");
		sb.append("segments Dropped: " + getDrops() + "\n");
		sb.append("Buffer Events: " + getBufferEvents() + "\n");
		sb.append("Connection Success Rate: " + df.format(getCSR()*100) + "%\n");
		
		return sb.toString();
	}

	public String toCSV(){
		DecimalFormat df = new DecimalFormat("#.##");
		StringBuilder sb = new StringBuilder();
		
		sb.append(df.format(((getBitRate()*8)/1000)) + ", ");
		sb.append(df.format(_serverBitRate/1000) + ", ");
		sb.append(df.format(getTimePlayed()) + ", ");
		sb.append(df.format(getTimeBuffered()) + ", ");
		sb.append(df.format(getConnectTime()) + ", ");
		sb.append(df.format(getTimeTotalBuffer()) + ", ");
		sb.append(df.format(getLagRatio()) + ", ");
		sb.append(df.format(getDelayAverage()) + ", ");
		sb.append(getPlays() + ", ");
		sb.append(getDrops() + ", ");
		sb.append(getBufferEvents() + ", ");
		sb.append(df.format(getCSR()*100) + "\n");
		
		return sb.toString();
	}
	
	public String toCSVwHeader(){
		StringBuilder sb = new StringBuilder();
		
		sb.append("BitRate, ServerBitRate, PlayTime, BufferTime, ConnectTime, LagTime, LagRatio, AvgDelay, SegPlayed, SegDropped, BufferEvents, CSR\n");
		sb.append(toCSV());
		
		return sb.toString();
	}
	
//-------------------------------------------------------------------------------------------------
//GETS
//-------------------------------------------------------------------------------------------------
	
	public double getBitRate(){ return ((double)_totalBytes/_timePlayed); }
	
	public double getBitRateServer(){ return _serverBitRate; }
	
	public int getBufferEvents(){ return _bufferEvents; }
	
	//Connection Success Rate
	public double getCSR(){ return ((double)(_segmentPlays - _bufferEvents))/_segmentPlays; }
	
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

//-------------------------------------------------------------------------------------------------
//LOGS
//-------------------------------------------------------------------------------------------------
	
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
	
	public void logDelay(double delay){ _delay.add(Math.abs(delay)); }

	public void logPlay(double time){ _timePlayed += time; }
	
	public void logSegmentDrop(){ _segmentDrops++; }
	
	public void logSegmentPlay(){ _segmentPlays++; }
	
	public void logServerBitRate(double serverBitRate){ _serverBitRate = serverBitRate; }
	
	public void logTime(){
		double cur = System.currentTimeMillis(); //+ TIME_OFFSET;
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

//-------------------------------------------------------------------------------------------------
//SETS
//-------------------------------------------------------------------------------------------------
	
	public void setStartTime(long time){ _startTime = time; }
	
	public void setStartTime(){ _startTime = System.currentTimeMillis(); }
	
	
}
