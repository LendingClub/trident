package org.lendingclub.trident.crypto;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.lendingclub.trident.crypto.CertificateAuthority.CertificateDecorator;

import com.google.common.collect.Lists;

public abstract class CertificateAuthorityManager {

	List<CertificateDecorator> decoratorList = Lists.newCopyOnWriteArrayList();
	public abstract CertificateAuthority getCertificateAuthority(String id)  ;
	public abstract CertificateAuthority createCertificateAuthority(String id);
	public List<CertificateDecorator> getCertificateDecoratorList() {
		return decoratorList;
	}

}
