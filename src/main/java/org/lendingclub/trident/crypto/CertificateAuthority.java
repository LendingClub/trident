package org.lendingclub.trident.crypto;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;

public abstract class CertificateAuthority {

	public static interface CertificateDecorator extends Consumer<Builder> {

	}

	public abstract class Builder {

		boolean clientCert = true;
		boolean serverCert = true;

		String tridentClusterId;
		long validityMillis = TimeUnit.DAYS.toMillis(365 * 2);
		String cn;
		String subjectDNTemplate = "O=trident,CN=%s";
		List<String> subjectAlternateNames = Lists.newArrayList();

		public <T extends Builder> T withCN(String cn) {
			this.cn = cn;
			return (T) this;
		}

		public boolean isServerCert() {
			return serverCert;
		}

		public boolean isClientCert() {
			return clientCert;
		}

		public <T extends Builder> T withServerCert(boolean b) {
			this.clientCert = b;
			return (T) this;
		}

		public <T extends Builder> T withClientCert(boolean b) {
			this.clientCert = b;
			return (T) this;
		}

		public <T extends Builder> T withTridentClusterId(String id) {
			this.tridentClusterId = id;
			return (T) this;
		}

		public <T extends Builder> T withSubjectAlternateNames(List<String> san) {
			subjectAlternateNames = Lists.newArrayList(san);
			return (T) this;
		}

		public <T extends Builder> T withSubjectAlternateNames(String... san) {
			subjectAlternateNames = Lists.newArrayList(san);
			return (T) this;
		}

		public <T extends Builder> T withValidityDays(int days) {
			return withValidity(days,TimeUnit.DAYS);
		}

		public <T extends Builder> T withSubjectDNTemplate(String s) {
			this.subjectDNTemplate = s;
			return (T) this;
		}

		public <T extends Builder> T withValidity(long n, TimeUnit unit) {
			this.validityMillis = unit.toMillis(n);
			return (T) this;
		}
		public <T extends Builder> T withValidityMinutes(int mins) {
			return withValidity(mins,TimeUnit.MINUTES);
		}

		public String getTridentClusterId() {
			return tridentClusterId;
		}

		public String getCN() {
			return cn;
		}

		public List<String> getSubjectAlternativeNames() {
			return subjectAlternateNames;
		}

		public String getSubjectDNTemplate() {
			return subjectDNTemplate;
		}

		public abstract CertDetail build();
	}

	public class CertDetail {
		PrivateKey privateKey;
		java.security.cert.Certificate certificate;
		java.security.cert.Certificate rootCA;

		public PrivateKey getPrivateKey() {
			return privateKey;
		}

		public Certificate getCertificate() {
			return certificate;
		}

		public Certificate getCACertificate() {
			return rootCA;
		}

		public void writeCertPath(File f) throws IOException {
			if (!f.exists()) {
				f.mkdirs();
			}
			com.google.common.io.Files.write(CertificateAuthority.toPEM(getCACertificate()), new File(f, "ca.pem"),
					Charsets.UTF_8);
			com.google.common.io.Files.write(CertificateAuthority.toPEM(getCertificate()), new File(f, "cert.pem"),
					Charsets.UTF_8);
			com.google.common.io.Files.write(CertificateAuthority.toPEM(getPrivateKey()), new File(f, "key.pem"),
					Charsets.UTF_8);
		}

		public void writeCertBundle(File f, String dockerHost) throws IOException {

			try {

				f.delete();

				ZipFile zf = new ZipFile(f);

				String ca = CertificateAuthority.toPEM(getCACertificate());
				String cert = CertificateAuthority.toPEM(getCertificate());
				String key = CertificateAuthority.toPEM(getPrivateKey());

				ZipParameters zp = null;

				zp = new ZipParameters();
				zp.setFileNameInZip("ca.pem");
				zp.setSourceExternalStream(true);
				zf.addStream(new ByteArrayInputStream(ca.getBytes()), zp);

				zp = new ZipParameters();
				zp.setFileNameInZip("cert.pem");
				zp.setSourceExternalStream(true);
				zf.addStream(new ByteArrayInputStream(cert.getBytes()), zp);

				zp = new ZipParameters();
				zp.setFileNameInZip("key.pem");
				zp.setSourceExternalStream(true);
				zf.addStream(new ByteArrayInputStream(key.getBytes()), zp);

				if (!Strings.isNullOrEmpty(dockerHost)) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					pw.println("export DOCKER_HOST=" + dockerHost);
					pw.println("export DOCKER_TLS_VERIFY=1");
					pw.println("export DOCKER_CERT_PATH=\"$(pwd)\"");
					pw.println("#");
					pw.println("# Run this command to configure your shell: eval $(<env.sh)");
					pw.close();
					zp = new ZipParameters();
					zp.setFileNameInZip("env.sh");
					zp.setSourceExternalStream(true);
					zf.addStream(new ByteArrayInputStream(sw.toString().getBytes()), zp);

					sw = new StringWriter();
					pw = new PrintWriter(sw);
					pw.println("@echo off");
					pw.println("set DOCKER_HOST=" + dockerHost);
					pw.println("set DOCKER_TLS_VERIFY=1");
					pw.println("set DOCKER_CERT_PATH=%~dp0");
					pw.println("REM ");
					pw.println("REM Run this command to configure your shell: .\\env.cmd");
					pw.close();
					zp = new ZipParameters();
					zp.setFileNameInZip("env.cmd");
					zp.setSourceExternalStream(true);
					zf.addStream(new ByteArrayInputStream(sw.toString().getBytes()), zp);
				}

			} catch (ZipException e) {
				throw new IOException(e);
			}
		}

	}

	public abstract Builder createClientCert();

	public abstract Builder createServerCert();

	public static String toPEM(Object obj) throws IOException {

		StringWriter sw = new StringWriter();
		JcaPEMWriter pemw = new JcaPEMWriter(sw);

		pemw.writeObject(obj);

		pemw.close();

		return sw.toString();

	}

}
