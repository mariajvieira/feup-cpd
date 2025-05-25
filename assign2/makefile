server:
	cd src && javac ChatServer.java && java ChatServer 12345
client1:
	cd src/Client_1 && javac ChatClient.java && java -Djavax.net.ssl.trustStore=../client_truststore.jks -Djavax.net.ssl.trustStorePassword=senhatrust ChatClient 127.0.0.1 12345
client2:
	cd src/Client_2 && javac ChatClient.java && java -Djavax.net.ssl.trustStore=../client_truststore.jks -Djavax.net.ssl.trustStorePassword=senhatrust ChatClient 127.0.0.1 12345
