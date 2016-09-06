package videoSender;

import videoUtility.FileData;
import videoUtility.FourCC;
//
//import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
//import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

public class ICCSetup {

	private int _device;
	private int _height = 480;
	private int _width = 640;
	private int _maxSegmentsSaved = 10; // delete x frames behind
	private static int _maxSegments = 100; //in reference to naming
	private int _preloadSegments = 5; // diff of min v. max in index file
	private double _compressionRatio = 10;
	private double _fps = 20;
	private double _segmentVideoLength = 4; //seconds
	private FourCC _fourCC = new FourCC("MJPG");
	private VideoCapture _videoCap;

	//-------------------------------------------------------------------------
	//Constructors
		
	public ICCSetup() {
		// Use set methods to build
	}

	//-------------------------------------------------------------------------
	//Get Methods
	//-------------------------------------------------------------------------
	public double getCompressionRatio(){
		return _compressionRatio;
	}
	public FourCC getFourCC(){
		return _fourCC;
	}
	public String getFileName(int segmentNumber){
		return FileData.VIDEO_PREFIX.print() + segmentNumber + FileData.VIDEO_SUFFIX.print();
	}
	public double getFPS(){
		return _fps;
	}
	public int getHeight(){
		return (int)(_height*_compressionRatio);
	}
	public int getMaxSegments(){
		return _maxSegments;
	}
	public int getMaxSegmentsSaved(){
		return _maxSegmentsSaved;
	}
	public int getPreload(){
		return _preloadSegments;
	}
//	public VideoWriter getVideoWriter (int segmentNumber) throws Exception {
//		double width = _videoCap.get(Videoio.CV_CAP_PROP_FRAME_WIDTH);
//		double height = _videoCap.get(Videoio.CV_CAP_PROP_FRAME_HEIGHT);
//		
//		Size frameSize = new Size(width,height);
//		
//		//open video recorder
//		VideoWriter recorder = new VideoWriter((FileData.VIDEO_FOLDER.name() + getFileName(segmentNumber)),
//				_fourCC.toInt(), _fps, frameSize, true);
//
//		if(!recorder.isOpened()){
//			throw new Exception("Failed to open recorder");
//		}
//		return recorder;
//	}
	public double getSegmentLength(){
		return _segmentVideoLength;
	}
	public VideoCapture getVideoCapture() throws Exception{
		initVideoCapture();
		return _videoCap;
	}
	public int getWidth(){
		return (int)(_width*_compressionRatio);
	}


	//-------------------------------------------------------------------------
	//Set Methods
	//-------------------------------------------------------------------------
	public ICCSetup setCompressionRatio(double compressionRatio){
		_compressionRatio = compressionRatio;
		return this;
	}
	public ICCSetup setDevice(int device){
		_device = device;
		return this;
	}
	public ICCSetup setFourCC(String characterCode){
		_fourCC = new FourCC(characterCode);
		return this;
	}
	public ICCSetup setFPS(double fps){
		_fps = fps;
		return this;
	}
	public ICCSetup setHeight(int height){
		_height = height;
		return this;
	}
	public ICCSetup setMaxIndex(int maxIndex){
		_maxSegments = maxIndex;
		return this;
	}
	public ICCSetup setMaxSegmentsSaved(int maxSegmentsInFolder){
		_maxSegmentsSaved = maxSegmentsInFolder;
		return this;
	}
	public ICCSetup setPreload(int preloadedSegments){
		_preloadSegments = preloadedSegments;
		return this;
	}
	public ICCSetup setSegmentLength(double seconds){
		_segmentVideoLength = seconds;
		return this;
	}
	public ICCSetup setWidth(int width){
		_width = width;
		return this;
	}
	
	
	//-------------------------------------------------------------------------
	//Whatever Methods
	//-------------------------------------------------------------------------
	public void initVideoCapture() throws Exception {
		_videoCap = new VideoCapture(_device);
		_videoCap.set(Videoio.CAP_PROP_FPS, _fps);

		double width = _width * _compressionRatio;
		double height = _height * _compressionRatio;

		_videoCap.set(Videoio.CV_CAP_PROP_FRAME_WIDTH, width);
		_videoCap.set(Videoio.CV_CAP_PROP_FRAME_HEIGHT, height);

		if(!_videoCap.isOpened()){
			throw new Exception("Failed to open video stream");
		}
	}

}
