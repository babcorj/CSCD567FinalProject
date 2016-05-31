package GNUPlot;
import java.util.LinkedList;

public class GNUScriptParameters {
	
	//With title
	private String _datafile;
	private String _title = "";
	private LinkedList<String> _dataElements;
	private LinkedList<PlotObject> _plots;
	//With range
	private int _startX, _startY, _startZ, _endX, _endY, _endZ;
	private boolean _hasRange = false;
	//With labels
	private String _labelX = "";
	private String _labelY = "";
	private String _labelZ = "";
	
	//-------------------------------------------------------------------------
	//Constructors
	
	public GNUScriptParameters(){
		_dataElements = new LinkedList<>();
		_plots = new LinkedList<>();
	}

	public GNUScriptParameters(String title){
		this();
		_title = title;
	}
	
	public GNUScriptParameters(String title,
			int startX, int startY, int endX, int endY){
		
		this(title);
		_startX = startX;
		_startY = startY;
		_endX = endX;
		_endY = endY;
		_hasRange = true;
	}
	
	public GNUScriptParameters(String title,
			int startX, int startY, int startZ, int endX, int endY, int endZ){

		this(title, startX, startY, endX, endY);
		_startZ = startZ;
		_endZ = endZ;
	}
	
	//-------------------------------------------------------------------------
	//Get methods
	
	public String getDataFile(){
		return _datafile;
	}
	
	public String getTitle(){
		return _title;
	}
	
	public LinkedList<String> getDataElements(){
		return _dataElements;
	}

	public LinkedList<PlotObject> getPlots(){
		return _plots;
	}
	
	public int getStartX(){
		return _startX;
	}

	public int getStartY(){
		return _startY;
	}

	public int getStartZ(){
		return _startZ;
	}
	
	public int getEndX(){
		return _endX;
	}
	
	public int getEndY(){
		return _endY;
	}

	public int getEndZ(){
		return _endZ;
	}
	
	public String getLabelX(){
		return _labelX;
	}
	
	public String getLabelY(){
		return _labelY;
	}
	
	public String getLabelZ(){
		return _labelZ;
	}
	
	//-------------------------------------------------------------------------
	//Set Methods
	
	public void setLabelX(String labelX){
		_labelX = labelX;
	}
	
	public void setLabelY(String labelY){
		_labelY = labelY;
	}
	
	public void setLabelZ(String labelZ){
		_labelZ = labelZ;
	}

//	public void setYRange(int y0, int y1){
//		_startY = y0;
//		_endY = y1;
//	}
//	
//	public void setXRange(int x0, int x1){
//		_startY = x0;
//		_endY = x1;
//	}
	
	//-------------------------------------------------------------------------
	//Other Methods
	
	public void addElement(String ele){
		_dataElements.addLast(ele);
	}
	
	public void addPlot(PlotObject gnu){
		_plots.addLast(gnu);
	}
	
	public boolean hasRange(){
		return false;
	}
}
