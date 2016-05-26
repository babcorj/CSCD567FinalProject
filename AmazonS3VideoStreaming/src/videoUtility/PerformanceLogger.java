package videoUtility;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

public class PerformanceLogger {

	private double _startTime;
	private FileWriter _fw;
	
	public PerformanceLogger(String filename) throws IOException {
		_fw = new FileWriter(filename);
	}
	
	public double getTime(){
		return _startTime;
	}
	
	public synchronized void log(double number){
		DecimalFormat formatter = new DecimalFormat("#.00");
		
		try{
			_fw.write(formatter.format(number));
		}
		catch(IOException e){
			System.err.println("Logging error:" + number);
		}
	}
	
	public synchronized void log(String str) throws IOException {
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
	
	public synchronized void logTime(){
		DecimalFormat formatter = new DecimalFormat("#.00");
		
		try{
			_fw.write(formatter.format((System.currentTimeMillis() - _startTime)/1000));
		}
		catch(IOException e){
			System.err.println("Logging error:" + _startTime);
		}
	}
	
	public void close() throws IOException {
		_fw.close();
	}
	
	public void startTime(){
		_startTime = System.currentTimeMillis();
	}
}
