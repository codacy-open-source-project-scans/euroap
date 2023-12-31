/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.other;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

/**
 * Common methods for keystore generation.
 *
 * @author Jan Stourac
 */
public final class KeyStoreUtils {
    /**
     * Generates keystore of JKS type with specified key and cert entries. Thus using this method one can create
     * both keystore and truststore.
     *
     * @param keys             array of key-pair entries
     * @param trustCerts       array of trusted certificates entries
     * @param keystorePassword keystore password
     * @return generated keystore instance with given entries as a content
     * @throws KeyStoreException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static KeyStore generateKeystore(KeyEntry[] keys, CertEntry[] trustCerts, String keystorePassword) throws
            KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        return generateKeystore(KeyStoreType.JKS, keys, trustCerts, keystorePassword);
    }

    /**
     * Generates keystore of given type with specified key and cert entries. Thus using this method one can create
     * both keystore and truststore.
     *
     * @param keyStoreType     type of keystore to be created
     * @param keys             array of key-pair entries
     * @param trustCerts       array of trusted certificates entries
     * @param keystorePassword keystore password
     * @return generated keystore instance with given entries as a content
     * @throws KeyStoreException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static KeyStore generateKeystore(KeyStoreType keyStoreType, KeyEntry[] keys, CertEntry[] trustCerts, String
            keystorePassword) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        final KeyStore keystore = KeyStore.getInstance(keyStoreType.toString());
        keystore.load(null, null);

        if (keys != null) {
            for (KeyEntry key : keys) {
                keystore.setKeyEntry(key.name, key.keyPair.getPrivate(), keystorePassword.toCharArray(), key
                        .certificates);
            }
        }

        if (trustCerts != null) {
            for (CertEntry cert : trustCerts) {
                keystore.setCertificateEntry(cert.name, cert.certificate);
            }
        }
        return keystore;
    }

    /**
     * Saves given keystore on the filesystem.
     *
     * @param keyStore         keystore to be saved
     * @param keystorePassword password for keystore
     * @param keystoreFile     destination keystore file on the filesystem
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    public static void saveKeystore(KeyStore keyStore, String keystorePassword, File keystoreFile) throws
            IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        saveKeystore(keyStore, keystorePassword, keystoreFile.getAbsolutePath());
    }

    /**
     * Saves given keystore on the filesystem.
     *
     * @param keyStore         keystore to be saved
     * @param keystorePassword password for keystore
     * @param keystoreFile     destination keystore file on the filesystem
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    public static void saveKeystore(KeyStore keyStore, String keystorePassword, String keystoreFile) throws
            IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        if (keystoreFile == null) {
            throw new RuntimeException("There has to be defined path to generated keystore file!");
        }
        final OutputStream ksOut = new FileOutputStream(keystoreFile);
        keyStore.store(ksOut, keystorePassword.toCharArray());
        ksOut.close();
    }

    public static enum KeyStoreType {
        JKS,
        PKCS12
    }

    public static class KeyEntry {
        private String name;
        private KeyPair keyPair;
        private Certificate[] certificates;

        public KeyEntry(String name, KeyPair keyPair, Certificate certificate) {
            this.name = name;
            this.keyPair = keyPair;
            this.certificates = new Certificate[]{certificate};
        }

        public KeyEntry(String name, KeyPair keyPair, Certificate[] certificates) {
            this.name = name;
            this.keyPair = keyPair;
            this.certificates = certificates;
        }
    }

    public static class CertEntry {
        private String name;
        private Certificate certificate;

        public CertEntry(String name, Certificate certificate) {
            this.name = name;
            this.certificate = certificate;
        }
    }
}
