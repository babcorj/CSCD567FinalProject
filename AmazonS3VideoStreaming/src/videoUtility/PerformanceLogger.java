package videoUtility;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class PerformanceLogger {
	static final long TIME_OFFSET = -25259;
	
	private long _startTime;
	private FileWriter _fw;
	private String _filename;
	private String _folder;
	
	public PerformanceLogger(String filename, String location) throws IOException {
		_fw = new FileWriter((_folder=location) + (_filename=filename));
	}
	
	public long getStartTime(){
		return _startTime;
	}
	
	public String getFileName(){
		return _filename;
	}
	
	public String getFilePath(){
		return new File(_folder + _filename).getAbsolutePath();
	}
	
<<<<<<< HEAD
	public void log(double number){
		DecimalFormat formatter = new DecimalFormat("#.000");
=======
	public synchronized void log(double number){
		DecimalFormat formatter = new DecimalFormat("#.###");
>>>>>>> ef2ee8c9cf74eb0178f54d41aa566b8f0bc99673
		
		try{
			_fw.write(formatter.format(number));
		}
		catch(IOException e){
			System.err.println("Logging error:" + number);
		}
	}
	
	public void log(String str) throws IOException {
		try{
			_fw.write(str);
		}
		catch(IOException e){
			e.printStackTrace();
		}
	}
	
<<<<<<< HEAD
	public void logTime(){
		long cur = System.currentTimeMillis();
		double timelapse = (double)(cur - _startTime)/1000;
=======
	public synchronized void logTime(){
		double cur = System.currentTimeMillis() + TIME_OFFSET;
		double startTime = _startTime;
		double timelapse = (cur - startTime)/1000;
>>>>>>> ef2ee8c9cf74eb0178f54d41aa566b8f0bc99673
		DecimalFormat formatter = new DecimalFormat("#.000");
		
		try{
			_fw.write(formatter.format(timelapse));
		}
		catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public void close() throws IOException {
		_fw.close();
	}
	
	public void setStartTime(long time){
		_startTime = time;
	}
	
	public void startTime(){
		_startTime = System.currentTimeMillis();
	}
}
