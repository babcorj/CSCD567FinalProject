package GNUPlot;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;

public class GNUScriptWriter {
	private String _filename;
	private FileWriter _fileWriter;
	private GNUScriptParameters _params;
	private boolean _is3D = false;
	
	public GNUScriptWriter(String filename, GNUScriptParameters params) throws IOException {
		_filename = filename;
		_fileWriter = new FileWriter(filename);
		_params = params;
	}
	
	public GNUScriptWriter(String filename, GNUScriptParameters params, boolean threeDimensional) throws IOException {
		this(filename, params);
		_is3D = threeDimensional;
	}

	public void close() throws IOException {
		_fileWriter.close();
	}

	public void write() throws IOException {
		if(_is3D){
			write3D();
			return;
		}
		String xStart = "";
		String yStart = "";
		String xEnd = "";
		String yEnd = "";

		if(_params.hasRange()){
			xStart = new Integer(_params.getStartX()).toString();
			yStart = new Integer(_params.getStartY()).toString();
			xEnd = new Integer(_params.getEndX()).toString();
			yEnd = new Integer(_params.getEndY()).toString();
		}
		String xLabel = _params.getLabelX();
		String yLabel = _params.getLabelY();
		
		String title = _params.getTitle();
		String str = "";
		
		ListIterator<String> dataElement = _params.getDataElements().listIterator(0);
		ListIterator<PlotObject> plots = _params.getPlots().listIterator(0);
		
		StringBuilder scriptBuilder = new StringBuilder();
		
		scriptBuilder.append("set   autoscale\n");
		scriptBuilder.append("# scale axes automatically\n");
		scriptBuilder.append("unset log                              # remove any log-scaling\n");
		scriptBuilder.append("unset label                            # remove any previous labels\n");
		scriptBuilder.append("set xtic auto                          # set xtics automatically\n");
		scriptBuilder.append("set ytic auto                          # set ytics automatically\n");
		scriptBuilder.append("set title \""+title+": ");
		while(dataElement.hasNext()){
			str = dataElement.next();
			scriptBuilder.append(str);
			if(dataElement.hasNext() != false){
				scriptBuilder.append(", ");
			}
		}
		scriptBuilder.append("\nset xlabel \"" + xLabel + "\"\n");
		scriptBuilder.append("set ylabel \"" + yLabel + "\"\n");
		
		if(_params.hasRange()){
			scriptBuilder.append("set xr ["+xStart+":"+xEnd+"]\n");
			scriptBuilder.append("set yr ["+yStart+":"+yEnd+"]\n");
		}
		
		PlotObject gnu = null;
		int[] cols = null;

		List<PlotObject> plotList = _params.getPlots();
		if(plotList.get(0).hasLine()){
			scriptBuilder.append("set style line 1 ");
			scriptBuilder.append(plotList.get(0).getLine());
			scriptBuilder.append("\n");
		}else{
			scriptBuilder.append("set style line 1 lc rgb '#0060ad' lt 1 lw 2 pt 7 ps 1.5   # --- blue\n");			
		}
		if(plotList.get(1).hasLine()){
			scriptBuilder.append("set style line 2 ");
			scriptBuilder.append(plotList.get(1).getLine());
			scriptBuilder.append("\n");
		}else{
			scriptBuilder.append("set style line 2 lc rgb '#dd181f' lt 1 lw 2 pt 5 ps 1.5   # --- red\n");
		}

		scriptBuilder.append("plot \"");

		int plotCount = 1;
		while(plots.hasNext()){
			
			gnu = plots.next();
			cols = gnu.getColumns();

			scriptBuilder.append(gnuPort(gnu.getFile()));
			scriptBuilder.append("\" using ");

			for(int i = 0; i < cols.length; i++){
				scriptBuilder.append(cols[i]);
				if(i < cols.length -1) {
					scriptBuilder.append(":");
				}
			}
			
			scriptBuilder.append(" title \"");
			scriptBuilder.append(gnu.getTitle());
			scriptBuilder.append("\" with linespoints ls ");

			scriptBuilder.append(plotCount++);
			if(plots.hasNext()){
				scriptBuilder.append(", \\\n\"");
			}
		}
		_fileWriter.write(scriptBuilder.toString());
		
		System.out.println("Finished writing script: '"+ _filename +"'");
	}

	private void write3D() throws IOException {
		String xStart = "";
		String yStart = "";
		String zStart = "";
		String xEnd = "";
		String yEnd = "";
		String zEnd = "";

		if(_params.hasRange()){
			xStart = new Integer(_params.getStartX()).toString();
			yStart = new Integer(_params.getStartY()).toString();
			zStart = new Integer(_params.getStartZ()).toString();
			xEnd = new Integer(_params.getEndX()).toString();
			yEnd = new Integer(_params.getEndY()).toString();
			zEnd = new Integer(_params.getEndZ()).toString();
		}
		String xLabel = _params.getLabelX();
		String yLabel = _params.getLabelY();
		String zLabel = _params.getLabelZ();
		
		String title = _params.getTitle();
		String str = "";
		
		ListIterator<String> dataElement = _params.getDataElements().listIterator(0);
		ListIterator<PlotObject> plots = _params.getPlots().listIterator(0);
		
		StringBuilder scriptBuilder = new StringBuilder();

		scriptBuilder.append("set   autoscale\n");
		scriptBuilder.append("# scale axes automatically\n");
		scriptBuilder.append("unset log                              # remove any log-scaling\n");
		scriptBuilder.append("unset label                            # remove any previous labels\n");
		scriptBuilder.append("set xtic auto                          # set xtics automatically\n");
		scriptBuilder.append("set ytic auto                          # set ytics automatically\n");
		scriptBuilder.append("set title \""+title+": ");
		while(dataElement.hasNext()){
			str = dataElement.next();
			scriptBuilder.append(str);
			if(dataElement.hasNext() != false){
				scriptBuilder.append(", ");
			}
		}
		scriptBuilder.append("\nset xlabel \"" + xLabel + "\"\n");
		scriptBuilder.append("set ylabel \"" + yLabel + "\"\n");
		scriptBuilder.append("set zlabel \"" + zLabel + "\"\n");
		
		if(_params.hasRange()){
			scriptBuilder.append("set xr ["+xStart+":"+xEnd+"]\n");
			scriptBuilder.append("set yr ["+yStart+":"+yEnd+"]\n");
			scriptBuilder.append("set zr ["+zStart+":"+zEnd+"]\n");
		}
		
		PlotObject gnu = null;
		int[] cols = null;
		scriptBuilder.append("splot \"");

		while(plots.hasNext()){
			
			gnu = plots.next();
			cols = gnu.getColumns();

			scriptBuilder.append(gnuPort(gnu.getFile()));
			scriptBuilder.append("\" using ");

			for(int i = 0; i < cols.length; i++){
				scriptBuilder.append(cols[i]);
				if(i < cols.length -1) {
					scriptBuilder.append(":");
				}
			}
			
			scriptBuilder.append(" title \"");
			scriptBuilder.append(gnu.getTitle());
			scriptBuilder.append("\" with vectors head filled lt 1");
			if(plots.hasNext()){
				scriptBuilder.append(", \\\n\"");
			}
		}
		_fileWriter.write(scriptBuilder.toString());
		
		System.out.println("Finished writing script: '"+ _filename +"'");
	}

	
	/*
	 * Adds slashes to the slashes in a String
	 * Intended for file paths being used in gnuplot
	 * 
	 * @return Modified copy of original string with
	 *         an extra forward slash added to every 
	 *         forward slash
	 */
	public static String gnuPort(String filename){
		String gnuLikeFileStyle = "";
		for(int i = 0; i < filename.length(); i++){
			char character = filename.charAt(i);
			gnuLikeFileStyle += character;
			if(character == '\\'){
				gnuLikeFileStyle += '\\';
			}
		}
		return gnuLikeFileStyle;
	}
	
}
