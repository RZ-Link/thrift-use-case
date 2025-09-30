package org.example;

import com.tencent.kona.crypto.KonaCryptoProvider;
import com.tencent.kona.pkix.KonaPKIXProvider;
import com.tencent.kona.ssl.KonaSSLProvider;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import shared.SharedStruct;
import tutorial.Calculator;
import tutorial.InvalidOperation;
import tutorial.Operation;
import tutorial.Work;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;

public class JavaClient {
    public static void main(String[] args) {

        try {
            TTransport transport = new TSocket(getSSLSocket());

            TProtocol protocol = new TBinaryProtocol(transport);
            Calculator.Client client = new Calculator.Client(protocol);

            perform(client);

            transport.close();
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    private static void perform(Calculator.Client client) throws TException {
        client.ping();
        System.out.println("ping()");

        int sum = client.add(1, 1);
        System.out.println("1+1=" + sum);

        Work work = new Work();

        work.op = Operation.DIVIDE;
        work.num1 = 1;
        work.num2 = 0;
        try {
            int quotient = client.calculate(1, work);
            System.out.println("Whoa we can divide by 0");
        } catch (InvalidOperation io) {
            System.out.println("Invalid operation: " + io.why);
        }

        work.op = Operation.SUBTRACT;
        work.num1 = 15;
        work.num2 = 10;
        try {
            int diff = client.calculate(1, work);
            System.out.println("15-10=" + diff);
        } catch (InvalidOperation io) {
            System.out.println("Invalid operation: " + io.why);
        }

        SharedStruct log = client.getStruct(1);
        System.out.println("Check log: " + log.value);
    }

    public static SSLSocket getSSLSocket() throws Exception {

        // 加载国密Provider
        Security.addProvider(new KonaCryptoProvider()); // 实现国密密码学算法SM2，SM3和SM4
        Security.addProvider(new KonaPKIXProvider()); // 实现国密证书的解析与验证，并可加载和创建包含国密证书的密钥库
        Security.addProvider(new KonaSSLProvider()); // 实现中国的传输层密码协议（TLCP）
        // 加载可以信任的服务器证书
        KeyStore trustStore = KeyStore.getInstance("PKCS12", "KonaPKIX");
        trustStore.load(JavaClient.class.getResourceAsStream("/org/example/server.ks"), "testpasswd".toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        // 加载客户端证书，服务器通过客户端证书进行验证
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "KonaPKIX");
        keyStore.load(JavaClient.class.getResourceAsStream("/org/example/client.ks"), "testpasswd".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(keyStore, "testpasswd".toCharArray());

        // 创建SSLContext
        SSLContext context = SSLContext.getInstance("TLCPv1.1");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("127.0.0.1", 9090);

        return socket;
    }
}
