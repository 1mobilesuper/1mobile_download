package me.onemobile.android.download;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.OutputStreamWriter;

public class Util {
	public static boolean runRootCommandForResult(String command) {
		Process process = null;
		DataInputStream is = null;
		try {
			String[] arrayOfString = new String[1];
			arrayOfString[0] = "su";
			process = new ProcessBuilder(arrayOfString).start();
			is = new DataInputStream(process.getInputStream());
			BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()), 2048);
			stdin.write(command);
			stdin.write("\n");
			stdin.write("exit\n");
			stdin.close();
			process.waitFor();
			String result = is.readLine();
			if (process.exitValue() != 0) {
				return false;
			}
			return result != null && result.equalsIgnoreCase("Success");
		} catch (Exception e) {
			return false;
		} finally {
			try {
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
		}
	}

	public static boolean autoInstall(String installatioPath) {
		String installString = "sh -c \"LD_LIBRARY_PATH=/vendor/lib:/system/lib pm install -r " + installatioPath + "\"";
		boolean r = runRootCommandForResult(installString);
		return r;
	}

	public static boolean autoUninstall(String pkg) {
		String dataString = "sh -c \"LD_LIBRARY_PATH=/vendor/lib:/system/lib pm uninstall " + pkg + "\"";
		boolean r = runRootCommandForResult(dataString);
		return r;
	}
}
