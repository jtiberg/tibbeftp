package tibbeftp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author jesper
 */
public class MyFTP {
    public static final String VERSION;
    public static int FTP_PORT = 21;
    public static int PASV_RANGE_MIN = 8123;
    public static int PASV_RANGE_MAX = 8129;
    public static final boolean PASV_PROMISCUOUS = "true".equals(System.getenv("PASV_PROMISCUOUS"));
    public static final int PASV_TIMEOUT;

    static {
        String tmp = MyFTP.class.getPackage().getImplementationVersion();
        if (tmp == null) {
            VERSION = "N/A";
        } else {
            VERSION = tmp;
        }
        String pasvTimeoutEnv = System.getenv("PASV_TIMEOUT");
        PASV_TIMEOUT = pasvTimeoutEnv == null ? 30000 : Integer.parseInt(pasvTimeoutEnv) * 1000;
    }

    private final ThreadGroup tg = new ThreadGroup("Connections");
    private int mTotalConnections = 0;

    /**
     * Creates a new instance of MyFTP
     */
    public MyFTP() {
    }

    public String getSysInfo() {
        String ret = "211-SysInfo:\r\n";
        ret += "--- TibbeFTP " + VERSION + " ---\r\n";
        ret += "  Total connections: " + mTotalConnections + "\r\n";
        ret += "  Active threads: " + tg.activeCount() + "\r\n";

        Thread[] threads = new Thread[tg.activeCount() + 10];
        int num = tg.enumerate(threads);
        for (int i = 0; i < num; i++) {
            ConnectionHandler ch = (ConnectionHandler) threads[i];
            ret += "    " + ch.oneLineInfo() + "\r\n";
        }

        ret += "211 End";
        return ret;
    }

    public void run() {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(FTP_PORT);
            System.out.println("Listening on port : " + FTP_PORT + " passive-data-ports: " + PASV_RANGE_MIN + "-" + PASV_RANGE_MAX);
            while (true) {
                try {
                    Socket s = ss.accept();
                    mTotalConnections++;
                    Logger.logToConsole("New connection from " + s.getInetAddress());
                    new ConnectionHandler(tg, this, s).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Unable to bind port " + FTP_PORT + " (" + e + ")");
        } finally {
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
