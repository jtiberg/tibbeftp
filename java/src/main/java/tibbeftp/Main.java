package tibbeftp;

import java.io.File;

/**
 * Main class of TibbeFTP. Parses parameters and makes sure accounts.txt exists
 *
 * @author vcjesper
 */
public class Main {

    private static File ftpHome = null;

    public static File getFtpHome() {
        return ftpHome;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        System.out.println("TibbeFTP version: " + MyFTP.VERSION);
        String ftpIp = System.getenv("FTP_IP");
        if (ftpIp != null) {
            System.out.println("Passive server IP set to " + ftpIp);
        } else {
            System.out.println("External IP: " + Utils.getGlobalAddress());
        }
        if (args.length == 0) {
            System.err.println("SYNTAX: TibbeFTP.jar <FTP_HOME> |-disable-logging| |-port=CMDport|:DATAportmin-DATAportmax||");
            System.exit(1);
        }

        boolean disableLogging = false;
        ftpHome = new File(args[0]);
        for (int i = 1; i < args.length; i++) {
            String tmp = args[i];
            if ("-disable-logging".equals(tmp)) {
                disableLogging = true;
                System.out.println("Logging disabled");
            }
            if (tmp.startsWith("-port=")) {
                String ports[] = tmp.substring(6).split(":");
                MyFTP.FTP_PORT = Integer.parseInt(ports[0]);
                if (ports.length > 1) {
                    String dataPortRange[] = ports[1].split("-");
                    MyFTP.PASV_RANGE_MIN = Integer.parseInt(dataPortRange[0]);
                    MyFTP.PASV_RANGE_MAX = Integer.parseInt(dataPortRange[1]);
                }
            }
        }

        if (!disableLogging) {
            File logDir = new File(ftpHome, "logs");
            Logger.setLogDir(logDir);
            System.out.println("Logging to " + logDir);
        }

        new MyFTP().run();
    }
}
