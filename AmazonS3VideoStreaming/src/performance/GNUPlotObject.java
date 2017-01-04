package performance;

public class GNUPlotObject { //the plot in script writer
	private String _datafile;
	private String _title;
	private int[] _columns;
	private String _line;

	public GNUPlotObject(String file, String title, int[] columns){
		_datafile = file;
		_title = title;
		_columns = columns;
	}

	public String getFile(){
		return _datafile;
	}
	
	public String getLine(){
		return _line;
	}

	public String getTitle(){
		return _title;
	}
	
	public int[] getColumns(){
		return _columns;
	}
	
	public boolean hasLine(){
		if(_line == null) return false;
		return true;
	}
	
	public void setLine(String line){
		_line = line;
	}
}
