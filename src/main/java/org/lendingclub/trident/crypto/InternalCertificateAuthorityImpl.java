package org.lendingclub.trident.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX500NameUtil;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;

public class InternalCertificateAuthorityImpl extends CertificateAuthority {

	Logger logger = LoggerFactory.getLogger(InternalCertificateAuthorityImpl.class);
	final int keysize = 2048;
	String id;
	NeoRxClient neo4j;
	JsonNode caData;
	CertificateAuthorityManager certificateAuthorityManager;
	KeyStore keyStore;

	char[] password;

	CryptoService cryptoService;

	public InternalCertificateAuthorityImpl(CertificateAuthorityManager cam, NeoRxClient neo4j, String id,
			CryptoService cs) {

		Preconditions.checkNotNull(cam);
		Preconditions.checkNotNull(neo4j);
		Preconditions.checkNotNull(id);
		Preconditions.checkNotNull(cs);
		this.certificateAuthorityManager = cam;
		this.neo4j = neo4j;
		this.id = id;
		this.cryptoService = cs;
	}

	class InternalBuilder extends CertificateAuthority.Builder {

		@Override
		public CertDetail build() {

			try {
				return generateCert(this);
			} catch (GeneralSecurityException e) {
				throw new CertificateAuthorityException("problem creating CA ", e);
			}

		}

	}

	X509Certificate getRootCACertificate() throws GeneralSecurityException {
		Preconditions.checkState(keyStore != null, "key store not initialized");
		return (X509Certificate) keyStore.getCertificate("root-ca");
	}

	PrivateKey getRootCASigningKey() throws GeneralSecurityException {
		Preconditions.checkState(keyStore != null, "key store not initialized");

		Preconditions.checkState(password != null && password.length > 0, "password not set");
		return (PrivateKey) keyStore.getKey("root-ca", password);
	}

	@Override
	public Builder createClientCert() {
		return new InternalBuilder().withTridentClusterId(id).withClientCert(true).withServerCert(false);
	}

	@Override
	public Builder createServerCert() {
		return new InternalBuilder().withTridentClusterId(id).withServerCert(true).withClientCert(true);
	}

	protected void generateCASigningKeyAndCert() {
		try {
			Preconditions.checkState(keyStore == null);
			Preconditions.checkState(!Strings.isNullOrEmpty(this.id), "trident cluster not set");

			Preconditions.checkState(neo4j.execCypher("match (t:TridentCA {id:{id}}) return t", "id", this.id).toList()
					.blockingGet().size() == 0, "CA already exists: " + this.id);
			JsonNode swarmNode = neo4j
					.execCypher("match (s:DockerSwarm {tridentClusterId:{id}}) return s", "id", this.id)
					.blockingFirst(null);
			if (swarmNode == null) {
				throw new TridentException("Cannot create a CA for a cluster that does not exist: " + id);
			}

			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(keysize);
			KeyPair keypair = keyGen.generateKeyPair();

			X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
			nameBuilder.addRDN(BCStyle.CN, "root-ca");
			nameBuilder.addRDN(BCStyle.OU, id);
			nameBuilder.addRDN(BCStyle.O, "trident");

			X500Name issuer = nameBuilder.build();

			BigInteger serial = new BigInteger(160, new SecureRandom());
			X500Name subject = issuer;
			PublicKey pubKey = keypair.getPublic();

			Calendar calendar = Calendar.getInstance();
			Date startDate = new Date(System.currentTimeMillis());
			calendar.setTime(startDate);
			calendar.add(Calendar.YEAR, 10);

			Date endDate = calendar.getTime();

			JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

			X509v3CertificateBuilder generator = new JcaX509v3CertificateBuilder(issuer, serial, startDate, endDate,
					subject, pubKey);

			generator.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(pubKey));
			generator.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

			KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature | KeyUsage.keyEncipherment
					| KeyUsage.dataEncipherment | KeyUsage.cRLSign);
			generator.addExtension(Extension.keyUsage, false, usage);

			ASN1EncodableVector purposes = new ASN1EncodableVector();
			purposes.add(KeyPurposeId.id_kp_serverAuth);
			purposes.add(KeyPurposeId.id_kp_clientAuth);
			purposes.add(KeyPurposeId.anyExtendedKeyUsage);
			generator.addExtension(Extension.extendedKeyUsage, false, new DERSequence(purposes));

			X509Certificate rootCA = new JcaX509CertificateConverter().getCertificate(generator
					.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keypair.getPrivate())));

			rootCA.checkValidity(new Date());
			rootCA.verify(keypair.getPublic());

			this.password = UUID.randomUUID().toString().toCharArray();

			KeyStore caKs = KeyStore.getInstance("PKCS12");
			caKs.load(null, null);
			caKs.setKeyEntry("root-ca", keypair.getPrivate(), this.password,
					new java.security.cert.Certificate[] { rootCA });

			this.keyStore = caKs;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			caKs.store(baos, this.password);
			baos.close();

			org.bouncycastle.asn1.x500.X500Name caSubjectName = JcaX500NameUtil.getSubject(rootCA);
			neo4j.execCypher(
					"create (c:TridentCA {id:{id}}) set c.caSubjectName={caSubjectName},c.caKeyStore={ks},c.password={pw},c.createTs=timestamp(),c.updateTs=timestamp()",
					"id", this.id, "ks", BaseEncoding.base64().encode(baos.toByteArray()), "pw",
					cryptoService.encrypt(new String(password)), "caSubjectName", caSubjectName.toString());

			neo4j.execCypher(
					"match  (s:DockerSwarm {tridentClusterId:{id}}),(c:TridentCA {id:{id}}) merge (s)-[x:SECURED_BY]->(c)",
					"id", this.id);
		} catch (GeneralSecurityException | OperatorCreationException | IOException e) {
			throw new CertificateAuthorityException("problem creating CA: " + this.id, e);
		}

	}

	protected void init()
			throws GeneralSecurityException, IOException, GeneralSecurityException, OperatorCreationException {
		try {
			load();
			return;
		} catch (NoSuchElementException e) {
			throw new CertificateAuthorityException("CA not found: " + this.id, e);
		}

	}

	protected CertDetail generateCert(Builder builder) throws GeneralSecurityException {
		try {

			// Give an opportunity for the certificate request to be modified by
			// Trident extensions
			certificateAuthorityManager.decoratorList.forEach(it -> {
				it.accept(builder);
			});

			Preconditions.checkNotNull(getRootCASigningKey());
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(keysize);
			KeyPair keypair = keyGen.generateKeyPair();

			org.bouncycastle.asn1.x500.X500Name caSubjectName = JcaX500NameUtil.getSubject(getRootCACertificate());

			Preconditions.checkArgument(!Strings.isNullOrEmpty(builder.cn), "cn must be set");
			String subjectDN = String.format(builder.getSubjectDNTemplate(), builder.cn);
			org.bouncycastle.asn1.x500.X500Name generatedCertSubjectName = new X500Name(subjectDN);

			BigInteger serialNumber = new BigInteger(128, new SecureRandom());
			X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(caSubjectName, serialNumber,
					new java.util.Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)),
					new java.util.Date(System.currentTimeMillis() + builder.validityMillis), generatedCertSubjectName,
					keypair.getPublic());

			// Purpose-specific certs
			ASN1EncodableVector purposes = new ASN1EncodableVector();
			if (builder.isClientCert()) {
				purposes.add(KeyPurposeId.id_kp_clientAuth);
			}
			if (builder.isServerCert()) {
				purposes.add(KeyPurposeId.id_kp_serverAuth);
			}
			purposes.add(KeyPurposeId.anyExtendedKeyUsage);
			boolean purposeSpecificCert = false; // this could break things,
													// leave off for now
			if (purposeSpecificCert) {
				certBuilder.addExtension(Extension.extendedKeyUsage, false, new DERSequence(purposes));
			}
			// End of purpose-specific cert capability

			certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

			if (!builder.subjectAlternateNames.isEmpty()) {

				certBuilder.addExtension(Extension.subjectAlternativeName, false,
						collectSubjectAlternateNames(builder.subjectAlternateNames));
			}
			Certificate caCert = getRootCACertificate();
			ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WITHRSA").setProvider("BC")
					.build(getRootCASigningKey());

			X509CertificateHolder holder = certBuilder.build(contentSigner);
			X509Certificate signedCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
			logger.info("signed cert: {}", signedCert);

			CertDetail ci = new CertDetail();
			ci.rootCA = caCert;
			ci.privateKey = keypair.getPrivate();
			ci.certificate = signedCert;

			return ci;
		} catch (GeneralSecurityException e) {
			throw new CertificateAuthorityException("problem creating CA: " + this.id, e);
		} catch (CertIOException | OperatorCreationException e) {
			throw new CertificateAuthorityException("problem creating CA: " + this.id, e);
		}
	}

	protected void load() throws CertificateException, IOException, GeneralSecurityException {
		JsonNode n = neo4j.execCypher("match (c:TridentCA {id:{id}}) return c", "id", id).blockingFirst();

	
		byte[] ks = BaseEncoding.base64().decode(n.path("caKeyStore").asText());
		KeyStore caKs = KeyStore.getInstance("PKCS12");
		password = cryptoService.decryptString(n.path("password").asText()).toCharArray();
		caKs.load(new ByteArrayInputStream(ks), password);
		this.keyStore = caKs;
	}

	private boolean isIpAddress(String s) {
		Pattern p = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");
		return p.matcher(s).matches();

	}

	private GeneralNames collectSubjectAlternateNames(List<String> subjectAlternateNames) {
		List<GeneralName> list = Lists.newArrayList();

		for (String name : subjectAlternateNames) {
			name = com.google.common.base.Strings.nullToEmpty(name).trim();

			if (!Strings.isNullOrEmpty(name)) {

				if (isIpAddress(name)) {
					logger.info("adding SAN IP: {}", name);
					list.add(new GeneralName(GeneralName.iPAddress, name));
				} else {
					logger.info("adding SAN DNS: {}", name);
					list.add(new GeneralName(GeneralName.dNSName, name));
				}
			}

		}

		GeneralNames generalNames = new GeneralNames(list.toArray(new GeneralName[0]));
		return generalNames;
	}
}
