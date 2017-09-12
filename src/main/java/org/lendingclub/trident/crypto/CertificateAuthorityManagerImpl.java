package org.lendingclub.trident.crypto;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.bouncycastle.operator.OperatorCreationException;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.event.TridentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class CertificateAuthorityManagerImpl extends CertificateAuthorityManager {

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	CryptoService cryptoService;

	Logger logger = LoggerFactory.getLogger(CertificateAuthorityManagerImpl.class);

	@Override
	public CertificateAuthority getCertificateAuthority(String id) {

		Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
		try {
			InternalCertificateAuthorityImpl ca = new InternalCertificateAuthorityImpl(this, neo4j, id, cryptoService);
			ca.init();
			return ca;
		} catch (OperatorCreationException | GeneralSecurityException | IOException e) {
			throw new TridentException(e);
		}
	}

	@Override
	public CertificateAuthority createCertificateAuthority(String id) {
		try {
			CertificateAuthority ca = getCertificateAuthority(id);
			if (ca != null) {
				throw new IllegalStateException("certificate authority already exists: " + id);
			}
		} catch (RuntimeException e) {
			// this is expected
		}
		InternalCertificateAuthorityImpl ca = new InternalCertificateAuthorityImpl(this, neo4j, id, cryptoService);
		ca.generateCASigningKeyAndCert();

		try {
			new TridentEvent().withTridentClusterId(id).withMessage("certificate authority created").withAttribute("ca",
					ca.getRootCACertificate().getSubjectDN().toString());
		} catch (Exception e) {
			logger.warn("unexpected eception logging TridentEventLog", e);
		}
		return ca;
	}

}
