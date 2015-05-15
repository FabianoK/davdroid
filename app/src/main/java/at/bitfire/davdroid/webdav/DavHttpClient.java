/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import android.util.Log;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.resource.ServerInfo;


public class DavHttpClient {
	private final static String TAG = "davdroid.DavHttpClient";

	private final static RequestConfig defaultRqConfig;
	private final static Registry<ConnectionSocketFactory> socketFactoryRegistry;

	static {
		socketFactoryRegistry =	RegistryBuilder.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", TlsSniSocketFactory.getSocketFactory())
				.build();

		// use request defaults from AndroidHttpClient
		defaultRqConfig = RequestConfig.copy(RequestConfig.DEFAULT)
				.setConnectTimeout(20*1000)
				.setSocketTimeout(45*1000)
				.setStaleConnectionCheckEnabled(false)
				.build();
	}

    public static CloseableHttpClient create(boolean ignoreCertificate) {

        if(ignoreCertificate) {
            try {
                return createWithoutCertificate();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            }
        }
        //return null;
        return createWithCertificate();
    }

    public static CloseableHttpClient createWithoutCertificate() throws KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException {

        SSLContextBuilder sslBuilder = new SSLContextBuilder();
        SSLConnectionSocketFactory sslsf = null;

        sslBuilder.loadTrustMaterial(null, new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                return true;
            }
        });
        sslsf = new SSLConnectionSocketFactory(sslBuilder.build());


        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new PlainConnectionSocketFactory())
                .register("https", sslsf)
                .build();


        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
        cm.setMaxTotal(3);
        cm.setDefaultMaxPerRoute(2);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .setConnectionManager(cm)
                .build();

        return httpClient;
    }

	public static CloseableHttpClient createWithCertificate() {
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		// limits per DavHttpClient (= per DavSyncAdapter extends AbstractThreadedSyncAdapter)
		connectionManager.setMaxTotal(3);				// max.  3 connections in total
		connectionManager.setDefaultMaxPerRoute(2);		// max.  2 connections per host

		HttpClientBuilder builder = HttpClients.custom()
				.useSystemProperties()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(defaultRqConfig)
				.setRetryHandler(DavHttpRequestRetryHandler.INSTANCE)
				.setRedirectStrategy(DavRedirectStrategy.INSTANCE)
				.setUserAgent("DAVdroid/" + Constants.APP_VERSION)
				.disableCookieManagement();
		
		if (Log.isLoggable("Wire", Log.DEBUG)) {
			Log.i(TAG, "Wire logging active, disabling HTTP compression");
			builder = builder.disableContentCompression();
		}

        return builder.build();
	}

}
