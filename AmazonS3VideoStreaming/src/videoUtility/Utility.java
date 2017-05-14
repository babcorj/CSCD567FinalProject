package videoUtility;

public class Utility {

	public Utility(){}
	
	public static void pause(long milliseconds){
		try{
			Thread.sleep(milliseconds);
		} catch(InterruptedException e){}
	}
}
