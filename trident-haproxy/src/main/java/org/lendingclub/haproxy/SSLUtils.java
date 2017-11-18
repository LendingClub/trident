package org.lendingclub.haproxy;

import java.nio.file.Paths;

public class SSLUtils {


	public static String getPEMFileAbsolutePath() {
		return Paths.get("trident-haproxy/config/cert.pem").toString();
	}

}
