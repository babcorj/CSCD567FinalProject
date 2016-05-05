//import java.util.function.Function;

public class Utility {

	public Utility(){}
	
	public static void pause(long milliseconds){
		try{
			Thread.sleep(milliseconds);
		} catch(InterruptedException e){}
	}
	
//	public static void forLoop(int start, boolean cond, int increment, Function<Integer> function){
//		for(int i = start; cond; i += increment){
//			function.apply(new Integer(i));
//		}
//	}
	
}
