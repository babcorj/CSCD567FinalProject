package videoUtility;

public class VideoSource extends Thread {

	protected boolean _isDone;
	protected VideoSegmentHeader _vHeader;
	protected String _className;
	
	public VideoSource(){
		_isDone = false;
	}

	public void end(){
		System.out.println("Attempting to close " + _className + "...");
		_isDone = true;
	}
}
