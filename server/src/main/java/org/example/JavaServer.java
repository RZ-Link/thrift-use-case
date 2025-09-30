package org.example;

import com.tencent.kona.crypto.KonaCryptoProvider;
import com.tencent.kona.pkix.KonaPKIXProvider;
import com.tencent.kona.ssl.KonaSSLProvider;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import tutorial.Calculator;

import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;

@SpringBootApplication
public class JavaServer implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private CalculatorHandler handler;

    public static void main(String[] args) {
        SpringApplication.run(JavaServer.class, args);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            Calculator.Processor processor = new Calculator.Processor(handler);
            Runnable simple = new Runnable() {
                public void run() {
                    simple(processor);
                }
            };
            new Thread(simple).start();
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    public void simple(Calculator.Processor processor) {
        try {
            TServerTransport serverTransport = new TServerSocket(getSSLServerSocket());
            // Use this for a multithreaded server
            TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));
            System.out.println("Starting the simple server...");
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SSLServerSocket getSSLServerSocket() throws Exception {
        // 加载国密Provider
        Security.addProvider(new KonaCryptoProvider()); // 实现国密密码学算法SM2，SM3和SM4
        Security.addProvider(new KonaPKIXProvider()); // 实现国密证书的解析与验证，并可加载和创建包含国密证书的密钥库
        Security.addProvider(new KonaSSLProvider()); // 实现中国的传输层密码协议（TLCP）
        // 加载可以信任的客户端证书
        KeyStore trustStore = KeyStore.getInstance("PKCS12", "KonaPKIX");
        trustStore.load(JavaServer.class.getResourceAsStream("/org/example/client.ks"), "testpasswd".toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        // 加载服务器证书，客户端通过服务器证书进行验证
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "KonaPKIX");
        keyStore.load(JavaServer.class.getResourceAsStream("/org/example/server.ks"), "testpasswd".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(keyStore, "testpasswd".toCharArray());

        // 创建SSLContext
        SSLContext context = SSLContext.getInstance("TLCPv1.1");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        SSLServerSocketFactory sslServerSocketFactory = context.getServerSocketFactory();
        SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(9090);
        sslServerSocket.setNeedClientAuth(true); // 服务器需要验证客户端身份，通过信任的客户端证书（TrustManagerFactory）

        return sslServerSocket;
    }
}
