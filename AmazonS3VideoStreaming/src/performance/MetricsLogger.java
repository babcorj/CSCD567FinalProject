package performance;

import java.text.DecimalFormat;
import java.util.ArrayList;

import com.fasterxml.jackson.core.json.ByteSourceJsonBootstrapper;

public class MetricsLogger {
	
	private int 	_bufferEvents = 0;
	private double  _connectTime = 0.0;
	private int		_framePlays = 0;
	private double	_pps = 0.0; //pixels per second
	private int 	_segmentDrops = 0;
	private int 	_segmentPlays = 0;
	private double 	_serverBitRate = 0.0;
	private long 	_startTime = 0;
	private double 	_timeBuffered = 0.0;
	private double 	_timePlayed = 0.0;
	private long 	_totalBytes = 0;

	private ArrayList<Double> _delay;
	
	public MetricsLogger() {
		_delay = new ArrayList<>();
	}

//-------------------------------------------------------------------------------------------------
//MISC METHODS
//-------------------------------------------------------------------------------------------------
	public void reset(){
		_bufferEvents = 0;
		_connectTime = 0.0;
		_framePlays = 0;
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
		sb.append("Bits Per Pixel: " + (df.format(getBitsPerPixel()) + "\n"));
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
		sb.append(df.format(getBitsPerPixel()) + ", ");
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
		
		sb.append("BitRate, ServerBitRate, BitsPerPixel, PlayTime, BufferTime, ConnectTime, LagTime, LagRatio, AvgDelay, SegPlayed, SegDropped, BufferEvents, CSR\n");
		sb.append(toCSV());
		
		return sb.toString();
	}
	
//-------------------------------------------------------------------------------------------------
//GETS
//-------------------------------------------------------------------------------------------------
	
	public double getBitsPerPixel(){
		return getBitRate()/_pps;
	}
	
	public double getBitRate(){ return ((double)_totalBytes/(_timePlayed+_timeBuffered+_connectTime)); }
	
	public double getBitRateServer(){ return _serverBitRate; }
	
	public int getBufferEvents(){ return _bufferEvents; }

	public long getBytesPlayed(){ return _totalBytes; }

	public double getConnectTime(){ return _connectTime; }

	//Connection Success Rate
	public double getCSR(){ return ((double)(_framePlays - _bufferEvents))/_framePlays; }

	public double getDelayAverage(){
		
		double average, sum = 0, count = _delay.size();
		
		if(count == 0) return 0;
		
		for(int i=0; i < count; i++){
			sum += _delay.get(i);
		}
		
		average = sum/count;
		
		return average;
	}
	
	public ArrayList<Double> getDelayList(){ return _delay; }
	
	public int getDrops(){ return _segmentDrops; }
	
	public int getFramePlays(){ return _framePlays; }
	
	public double getLagRatio(){
		return (_timeBuffered + _connectTime)/_timePlayed; }
	
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
	public void logBuffer(double time){ _timeBuffered += time; }
	
	public void logBufferEvent(){ _bufferEvents++; }
	
	public void logBytes(long bytes){ _totalBytes += bytes; }
	
	public void logConnectTime(double connectTime){ _connectTime = connectTime; }
	
	public void logDelay(double delay){ _delay.add(Math.abs(delay)); }

	public void logFrame(){ _framePlays++; }
	
	public void logPlay(double time){ _timePlayed += time; }
	
	public void logSegmentDrop(){ _segmentDrops++; }
	
	public void logSegmentPlay(){ _segmentPlays++; }
	
	public void logServerBitRate(double serverBitRate){ _serverBitRate = serverBitRate; }

//-------------------------------------------------------------------------------------------------
//SETS
//-------------------------------------------------------------------------------------------------

	public void setSegmentPixelSize(int width, int height, double fps){
		_pps = width * height * fps;
		
	}
	
	public void setStartTime(long time){ _startTime = time; }
	
	public void setStartTime(){ _startTime = System.currentTimeMillis(); }
	
	
}
