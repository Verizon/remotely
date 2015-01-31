#!/bin/bash

CA_passphrase=badpassword
server_name=Paul-test-server
client_name=Paul-test-client
ca_name=Paul-test-CA


# Generate a Certificate Authority:
openssl req -out CA.pem -new -x509 -passout pass:$CA_passphrase \
		-subj "/CN=$ca_name" -keyout CA_key.pem
# CA has required book keeping (cert.srl: incremental serial number)
echo "00" > cert.srl

## Generate a Server keypair and certificate
openssl genrsa -f4 2048 > server_key.pem
openssl rsa -in server_key.pem -pubout > server_pubkey.pem
openssl req -key server_key.pem -new -out server.req -subj "/CN=$server_name"
** convert from SSLeay to pkcs8
openssl pkcs8 -topk8 -inform pem -in server_key.pem -outform pem -nocrypt -out server_key.pk8

# Sign the server certificate with the CA:
openssl x509 -req -in server.req -CA CA.pem -passin pass:$CA_passphrase \
        -CAkey CA_key.pem -CAserial cert.srl -out server_cert.pem

## Generate a Client keypair and certificate:
openssl genrsa -out client_key.pem 2048
openssl req -key client_key.pem -new -out client.req -subj "/CN=$client_name"
** convert from SSLeay to pkcs8
openssl pkcs8 -topk8 -inform pem -in client_key.pem -outform pem -nocrypt -out client_key.pk8

# Sign the client certificate with the CA:
openssl x509 -req -in client.req -CA CA.pem -passin pass:$CA_passphrase \
        -CAkey CA_key.pem -CAserial cert.srl -out client_cert.pem


echo -e "\n\n\n"
echo "TESTING PATTERNS:"
echo ""
echo "1. Start the Server with server auth only:"
echo "openssl s_server -accept 9443 -key server_key.pem -cert server_cert.pem"
echo ""
echo "2. Start the Server with mutual auth:"
echo "openssl s_server -accept 9443 -key server_key.pem -cert server_cert.pem -CAfile CA.pem -Verify 1"
echo ""
echo "3. Start a client SSL session, single auth:"
echo "# establish an SSL connection and echo 'HELLO' to the server:"
echo "openssl s_client -connect localhost:9443 -CAfile CA.pem" 
echo ""
echo "4. Start a client SSL session, mutual auth:"
echo "openssl s_client -connect localhost:9443 -CAfile CA.pem -key client_key.pem -cert client_cert.pem"
echo ""

