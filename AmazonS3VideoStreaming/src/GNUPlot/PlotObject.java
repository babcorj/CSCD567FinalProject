package GNUPlot;

public class PlotObject { //the plot in script writer
	private String _datafile;
	private String _title;
	private int[] _columns;

	public PlotObject(String file, String title, int[] columns){
		_datafile = file;
		_title = title;
		_columns = columns;
	}

	public String getFile(){
		return _datafile;
	}

	public String getTitle(){
		return _title;
	}
	
	public int[] getColumns(){
		return _columns;
	}
}
