package tibbeftp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author jesper
 */
public class Logger {

    private static File LOG_DIR = null;
    private static final DateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss:SSS");
    private static final SimpleDateFormat fileNameDateFormat = new SimpleDateFormat("yyyyMMdd.HHmmss");

    private String id;
    private File mLogFile = null;
    private static int errorLoggingToFileCount = 0;

    /**
     * Set the log dir to something (otherwise logging is disabled)
     *
     * @param dir
     */
    static void setLogDir(File dir) {
        LOG_DIR = dir;
    }

    public Logger(String id) {
        this.id = id;
        /**
         * Create handle to the log file
         */
        if (LOG_DIR != null) {
            LOG_DIR.mkdirs();
            mLogFile = new File(LOG_DIR, fileNameDateFormat.format(new Date()) + "_" + id + ".txt");
        }
    }

    /**
     * @param user
     */
    public void setUser(String user) {
        id = user;
        if (mLogFile != null) {
            File userLogDir = new File(LOG_DIR, user);
            File newLogFile = new File(userLogDir, mLogFile.getName());
            newLogFile.getParentFile().mkdirs();
            mLogFile.renameTo(newLogFile);
            mLogFile = newLogFile;
        }
    }

    public void info(String msg) {
        log("INFO", msg);
    }

    public void warning(String msg) {
        log("WARNING", msg);
    }

    public void error(String msg) {
        log("ERROR", msg);
    }

    public void recvCommand(String msg) {
        log("RECV", msg);
    }

    public void sendCommand(String msg) {
        log("SEND", msg);
    }

    /**
     * Logging stuff
     */
    public void error(Throwable t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        t.printStackTrace(ps);
        ps.close();
        log("Error", baos.toString());
    }

    public static void logToConsole(String message) {
        String tmp = logDateFormat.format(new Date()) + "\t" + message.trim();
        System.out.println(tmp);
    }

    private void log(final String tag, final String message) {
        String line = logDateFormat.format(new Date()) + "\t" + tag + "\t" + message.trim() + "\r\n";
        if (mLogFile == null) {
            return;
        }

        /**
         * Create and open log file
         */
        try (FileOutputStream fos = new FileOutputStream(mLogFile.getPath(), true)) {
            fos.write(line.getBytes());
        } catch (Exception e) {
            if (errorLoggingToFileCount < 100) {
                errorLoggingToFileCount++;
                System.err.println(line);
                e.printStackTrace();
            }
        }
    }
}
