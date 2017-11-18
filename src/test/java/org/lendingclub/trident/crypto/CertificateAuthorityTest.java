package org.lendingclub.trident.crypto;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.After;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.crypto.CertificateAuthority.CertDetail;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public class CertificateAuthorityTest extends TridentIntegrationTest {

	@Autowired
	CryptoService cryptoService;

	@Autowired
	CertificateAuthorityManager certificateAuthorityManager;

	@Autowired
	SwarmClusterManager swarmClusterManager;

	@Autowired
	public NeoRxClient neo4j;

	@After
	public void cleanup() {
		if (isIntegrationTestEnabled()) {
			neo4j.execCypher("match (a:DockerSwarm) where a.tridentClusterId=~'junit.*' detach delete a");
			neo4j.execCypher("match (a:TridentCA) where a.id=~'junit.*' detach delete a");
		}
	}

	@Test
	public void testIt() throws GeneralSecurityException, IOException, OperatorCreationException, ZipException {

		String id = "junit-" + UUID.randomUUID().toString();

		neo4j.execCypher("merge (a:DockerSwarm {tridentClusterId:{id}}) return a", "id", id);
		InternalCertificateAuthorityImpl ca = new InternalCertificateAuthorityImpl(certificateAuthorityManager,
				getNeoRxClient(), id, cryptoService);

		try {
			ca.getRootCACertificate();
			Assertions.failBecauseExceptionWasNotThrown(IllegalStateException.class);
		} catch (RuntimeException e) {
			Assertions.assertThat(e).isExactlyInstanceOf(IllegalStateException.class);
		}

		try {
			ca.getRootCASigningKey();
			Assertions.failBecauseExceptionWasNotThrown(IllegalStateException.class);
		} catch (RuntimeException e) {
			Assertions.assertThat(e).isExactlyInstanceOf(IllegalStateException.class);
		}

		try {
			certificateAuthorityManager.getCertificateAuthority(id);
			Assertions.failBecauseExceptionWasNotThrown(Exception.class);
		} catch (RuntimeException e) {
			Assertions.assertThat(e).hasMessageContaining("CA not found");
		}

		try {
			ca.init();
			Assertions.failBecauseExceptionWasNotThrown(Exception.class);
		} catch (Exception e) {
			Assertions.assertThat(e).hasMessageContaining("CA not found");
		}
		ca.generateCASigningKeyAndCert();

		getNeoRxClient().execCypher("match (c:TridentCA {id:{id}}) return c", "id", id).forEach(it -> {
			System.out.println(JsonUtil.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(it));
		});

		CertDetail ci = ca.createClientCert().withCN("foo").build();

		File f = new File(com.google.common.io.Files.createTempDir(), "test.zip");
		ci.writeCertBundle(f,"tcp://somewhere:2376");

		ZipFile zf = new ZipFile(f);

		Assertions.assertThat(zf.getFileHeader("ca.pem").getUncompressedSize()).isGreaterThan(10);
		Assertions.assertThat(zf.getFileHeader("cert.pem").getUncompressedSize()).isGreaterThan(10);
		Assertions.assertThat(zf.getFileHeader("key.pem").getUncompressedSize()).isGreaterThan(10);
		Assertions.assertThat(zf.getFileHeader("env.sh").getUncompressedSize()).isGreaterThan(10);
		Assertions.assertThat(zf.getFileHeader("env.cmd").getUncompressedSize()).isGreaterThan(10);
		CertificateAuthority ca1 = certificateAuthorityManager.getCertificateAuthority(id);

		CertDetail cd2 = ca1.createServerCert().withCN("foo").build();

		Assertions.assertThat(CertificateAuthority.toPEM(ci.getCACertificate()))
				.isEqualTo(CertificateAuthority.toPEM(cd2.getCACertificate()));

		try {
			swarmClusterManager.getSwarmCertDetail("foo");
			Assertions.failBecauseExceptionWasNotThrown(RuntimeException.class);
		} catch (RuntimeException e) {

		}

		// We have some race conditions in here with cleanup
		/*
		CertDetail cd = swarmClusterManager.getSwarmCertDetail(id);
		Assertions.assertThat(cd).isNotNull();
		Assertions.assertThat(swarmClusterManager.getSwarmCertDetail(id)).isSameAs(cd);
		*/

	}

	@Test
	public void testCreate() {
		String id = "junit-" + UUID.randomUUID().toString();
		
		Assertions.assertThat(neo4j.execCypher("match (a:TridentCA {id:{id}}) return a","id",id).toList().blockingGet().size()).isEqualTo(0);
		neo4j.execCypher("merge (a:DockerSwarm {tridentClusterId:{id}}) return a", "id", id);

		certificateAuthorityManager.createCertificateAuthority(id);
		Assertions.assertThat(neo4j.execCypher("match (a:TridentCA {id:{id}}) return a","id",id).toList().blockingGet().size()).isEqualTo(1);
		
		try {
			certificateAuthorityManager.createCertificateAuthority(id);
			Assertions.failBecauseExceptionWasNotThrown(RuntimeException.class);
		} catch (RuntimeException e) {
			Assertions.assertThat(e).hasMessageContaining("CA already exists");
		}
	}
}
