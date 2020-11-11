package tibbeftp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 *
 * @author jesper
 */
public class Utils {
	/**
	 *
	 * @return the global (perhaps masqueraded) address of the local host.
	 */
	public static String getGlobalAddress(){
		String ret;
		try{
			BufferedReader in = new BufferedReader( new InputStreamReader(
					new URL("http://icanhazip.com").openStream() ) );
			ret = in.readLine().trim();
			in.close();
		}catch(Exception e){
			ret = null;
			throw new RuntimeException("Unable to determine the global IP of this computer. Please set the environment variable FTP_IP to your global IP", e);
		}
		return ret;
	}

	/**
	 * Convert a host address to an int array
	 * @param ip on the form "192.168.1.2"
	 * @return ip in the passive mode ftp format 192,168,1,2
	 */
	public static String ipAndPortToFTPformat(final String ip, int port){
		return ip.replace('.', ',') + ","+(port/256)+","+(port%256);
	}

	/**
	 * Is the address private according to RFC 1918 ?
	 * @param ia
	 * @return
	 */
	private static boolean isPrivateAddress(InetAddress ia){
		String s = ia.getHostAddress();
		return s.startsWith("192.168.") || s.startsWith("10.") || s.startsWith("172.") || s.equals("127.0.0.1");
	}

    /**
     * Return the IP to give to the clients when we are in passive mode
     */
	public static String getMyIpString(Socket s) {
        String ret = System.getenv("FTP_IP");
        if (ret != null) {
            return ret;
        }
		// check if remote socket is on the same network (is not a global IP)
		if(isPrivateAddress(s.getInetAddress())){
			return s.getLocalAddress().getHostAddress();
		}else{
			return getGlobalAddress();
		}
	}

    /**
     * Convert l to a string containing at least minLen letters
     *  (use leading zeroes otherwise)
     */
    public static String lz(long l, int minLen){
        String ret = "" + l;
        while(ret.length() < minLen)
            ret = "0" + ret;
        return ret;
    }

    /**
     * Convert d to a string containing at most maxDecimals decimals
     */
    public static String maxDec(double d, int maxDecimals){
        String s = "" + d;
        int iPnt = s.indexOf('.');
        if(iPnt == -1)
            return s;
        int maxLen = iPnt + maxDecimals + 1;

        if(s.length() > maxLen)
            return s.substring(0,maxLen);
        else
            return s;
    }

    /**
     * Convert millis milliseconds to m:ss format
     */
    public static String millisToMinSec(long millis){
        long seconds = millis/1000;
        return seconds/60 + ":" + lz(seconds%60,2);
    }

	private static final long SIX_MONTHS = 3600000L * 24 * 180;
	private static final String[] MONTH_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    public static String dateToFTPTimeString(Date d){
		String ret = MONTH_SHORT[d.getMonth()] + " " + lz(d.getDate(),2) + " ";
		// more than one years old
		if(System.currentTimeMillis()-d.getTime() > SIX_MONTHS){
			ret += d.getYear()+1900;
		}else{
			ret += lz(d.getHours(),2) + ":" + lz(d.getMinutes(), 2);
		}
		return ret;
	}
    public static String dateToTimeString(Date date){
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.setTimeZone(TimeZone.getDefault());
            return lz(cal.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                   lz(cal.get(Calendar.MINUTE), 2) +
                   ":" + lz(cal.get(Calendar.SECOND), 2);
    }

	/**
	 * Returns a string specifying the distance in meters or kilometers
	 * @param kilometers
	 * @return
	 */
	public static String kmToDistanceString(float kilometers){
		if(kilometers > 1){
			return Utils.maxDec(kilometers,2) + "km";
		}else{
			long distInMeters = (long) (kilometers * 1000);
			return distInMeters + "m";
		}
	}
}
