package performance;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

/**
 * 
 * @author ryanj
 *
 *	Logs performance of Runner
 *
 */
public class PerformanceLogger {

	private long 		_startTime;
	
	private String 		_filename;
	private String 		_folder;
	private FileWriter 	_fw;
	
	public PerformanceLogger(String filename, String location) throws IOException {
		_fw = new FileWriter((_folder=location) + (_filename=filename));
	}

//-------------------------------------------------------------------------------------------------
//MISC METHODS
//-------------------------------------------------------------------------------------------------
	
	public void close() throws IOException { _fw.close(); }

	public void reset(){
		File out;
		
		try{
			_fw.close();
			_fw = null;
			
			for(int i=0; i < 100; i++){
				out = new File(_folder + _filename + "_" + i);

				if(!out.exists()){
					_fw = new FileWriter(out);
				}
			}
			if(_fw == null){
				_fw = new FileWriter(_folder + _filename);
			}
			
		} catch(IOException e){
			System.err.println("Unable to record network performance");
			e.printStackTrace();
		}
	}	
	
//-------------------------------------------------------------------------------------------------
//GETS
//-------------------------------------------------------------------------------------------------
	
	public String getFileName(){ return _filename; }
	
	public String getFilePath(){ return new File(_folder + _filename).getAbsolutePath(); }
	
	public long getStartTime(){ return _startTime; }

	//-------------------------------------------------------------------------------------------------
//LOGS
//-------------------------------------------------------------------------------------------------

	public void logVideoTransfer(long currentTime, double number) throws IOException{
		DecimalFormat formatter = new DecimalFormat("#.###");
		String vTransferLog = currentTime + " " + number + "\n";
		
		_fw.write(formatter.format(vTransferLog));
	}

	
//-------------------------------------------------------------------------------------------------
//SETS
//-------------------------------------------------------------------------------------------------
		public void setStartTime(){ _startTime = System.currentTimeMillis(); }
		
		public void setStartTime(long time){ _startTime = time; }
	
	
}
