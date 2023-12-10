package org.fowlart.notifier;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.azure.identity.DeviceCodeInfo;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;


import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import okhttp3.Request;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class Main {

    private static Properties _properties;
    private static DeviceCodeCredential _deviceCodeCredential;
    private static GraphServiceClient<Request> _userClient;

    public static void initializeGraphForUserAuth(Properties properties, Consumer<DeviceCodeInfo> challenge) {
        // Ensure properties isn't null

        if (properties == null) {
            throw new RuntimeException("Properties cannot be null");
        }

        _properties = properties;

        final String clientId = properties.getProperty("app.clientId");
        final String tenantId = properties.getProperty("app.tenantId");
        final List<String> graphUserScopes = Arrays.asList(properties.getProperty("app.graphUserScopes").split(","));

        _deviceCodeCredential = new DeviceCodeCredentialBuilder().clientId(clientId).tenantId(tenantId).challengeConsumer(challenge).build();

        final TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(graphUserScopes, _deviceCodeCredential);

        _userClient = GraphServiceClient.builder().authenticationProvider(authProvider).buildClient();

    }

    public static User getUser() throws Exception {
        // Ensure client isn't null
        if (_userClient == null) {
            throw new RuntimeException("Graph has not been initialized for user auth");
        }

        return _userClient.me().buildRequest().select("displayName,mail,userPrincipalName").get();
    }

    public static MessageCollectionPage getInbox() throws Exception {
        // Ensure client isn't null
        if (_userClient == null) {
            throw new Exception("Graph has not been initialized for user auth");
        }

        return _userClient.me().mailFolders("inbox").messages().buildRequest().select("from,isRead,receivedDateTime,subject").top(100).orderBy("receivedDateTime DESC").get();
    }

    public static String getUserToken() throws Exception {
        // Ensure credential isn't null
        if (_deviceCodeCredential == null) {
            throw new Exception("Graph has not been initialized for user auth");
        }

        final String[] graphUserScopes = _properties.getProperty("app.graphUserScopes").split(",");

        final TokenRequestContext context = new TokenRequestContext();
        context.addScopes(graphUserScopes);

        final AccessToken token = _deviceCodeCredential.getToken(context).block();
        return token.getToken();
    }

    public static void main(String[] args) {

        final Properties oAuthProperties = new Properties();
        try {
            oAuthProperties.load(Main.class.getResourceAsStream("/oAuth.properties"));
        } catch (IOException e) {
            System.out.println("Unable to read OAuth configuration. Make sure you have a properly formatted oAuth.properties file. See README for details.");
            return;
        }
        initializeGraphForUserAuth(oAuthProperties, deviceCodeInfo -> {
            System.out.println("Initializing Graph for user auth...");
            System.out.println("Verification url: " + deviceCodeInfo.getVerificationUrl());
            System.out.println("User code: " + deviceCodeInfo.getUserCode());
        });

        Scanner input = new Scanner(System.in);

        int choice = -1;

        while (choice != 0) {
            System.out.println("Please choose one of the following options:");
            System.out.println("0. Exit");
            System.out.println("1. Display access token");
            System.out.println("2. List my inbox");
            System.out.println("3. Send mail");
            System.out.println("4. Make a Graph call");

            try {
                choice = input.nextInt();
            } catch (InputMismatchException ex) {
                // Skip over non-integer input
            }

            input.nextLine();

            // Process user choice
            switch (choice) {
                case 0:
                    // Exit the program
                    System.out.println("Goodbye...");
                    break;
                case 1:
                    // Display access token
                    System.out.println("Not implemented");
                    break;
                case 2:
                    // List emails from user's inbox
                    listInbox();
                    break;
                case 3:
                    // Send an email message
                    System.out.println("Not implemented");
                    break;
                default:
                    System.out.println("Invalid choice");
            }
        }


    }
    private static void listInbox() {
        try {
            System.out.println("Hello, " + getUser().displayName+"!");

            getInbox().getCurrentPage()
                    .stream()
                    .filter(message -> message.isRead.equals(Boolean.FALSE))
                    .forEach(message -> {

                System.out.println("Subject: " + message.subject);
                System.out.println("From: " + message.from.emailAddress.address);
                System.out.println("Received: " + message.receivedDateTime);
                System.out.println("\n");
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}