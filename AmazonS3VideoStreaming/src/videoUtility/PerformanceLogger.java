package videoUtility;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class PerformanceLogger {

	private BigDecimal _startTime;
	private FileWriter _fw;
	
	public PerformanceLogger(String filename) throws IOException {
		_fw = new FileWriter(filename);
	}
	
	public BigDecimal getStartTime(){
		return _startTime;
	}
	
	public double getTime(){
		return _startTime.doubleValue();
	}
	
	public synchronized void log(double number){
		DecimalFormat formatter = new DecimalFormat("#.000");
		
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
		double cur = System.currentTimeMillis();
		double startTime = _startTime.doubleValue();
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
	
	public void close() throws IOException {
		_fw.close();
	}
	
	public void setStartTime(BigDecimal time){
		_startTime = time;
	}
	
	public void startTime(){
		_startTime = new BigDecimal(System.currentTimeMillis());
	}
}
